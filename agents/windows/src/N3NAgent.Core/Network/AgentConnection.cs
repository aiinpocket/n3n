using System.Net.WebSockets;
using System.Text;
using System.Text.Json;
using N3NAgent.Core.Crypto;
using N3NAgent.Core.Protocol;
using N3NAgent.Core.Storage;
using N3NAgent.Core.Capabilities;

namespace N3NAgent.Core.Network;

/// <summary>
/// Manages WebSocket connection to the N3N gateway.
/// </summary>
public class AgentConnection : IAsyncDisposable
{
    private readonly CredentialStorage _storage;
    private readonly CapabilityRegistry _capabilities;
    private ClientWebSocket? _webSocket;
    private CancellationTokenSource? _cts;
    private Task? _receiveTask;
    private long _sequence;
    private bool _isConnected;
    private readonly object _sendLock = new();

    public event EventHandler<ConnectionStateEventArgs>? ConnectionStateChanged;
    public event EventHandler<string>? MessageReceived;
    public event EventHandler<Exception>? ErrorOccurred;

    public bool IsConnected => _isConnected;

    public AgentConnection()
    {
        _storage = CredentialStorage.Instance;
        _capabilities = CapabilityRegistry.Instance;
    }

    /// <summary>
    /// Connect to the gateway WebSocket.
    /// </summary>
    public async Task ConnectAsync(CancellationToken cancellationToken = default)
    {
        var platformUrl = _storage.GetPlatformUrl();
        if (string.IsNullOrEmpty(platformUrl))
            throw new InvalidOperationException("Agent not paired - no platform URL");

        var deviceId = _storage.GetDeviceId();
        var deviceToken = _storage.GetDeviceToken();
        var sessionKey = _storage.GetSessionKey();

        if (string.IsNullOrEmpty(deviceId) || string.IsNullOrEmpty(deviceToken) || sessionKey == null)
            throw new InvalidOperationException("Agent not paired - missing credentials");

        // Convert HTTP URL to WebSocket URL
        var wsUrl = platformUrl
            .Replace("https://", "wss://")
            .Replace("http://", "ws://")
            + "/gateway/agent";

        _webSocket = new ClientWebSocket();
        _webSocket.Options.SetRequestHeader("X-Device-Id", deviceId);
        _webSocket.Options.SetRequestHeader("X-Device-Token", deviceToken);

        _cts = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);

        try
        {
            await _webSocket.ConnectAsync(new Uri(wsUrl), _cts.Token);
            _isConnected = true;
            ConnectionStateChanged?.Invoke(this, new ConnectionStateEventArgs(true));

            // Start authentication handshake
            await AuthenticateAsync();

            // Start receive loop
            _receiveTask = ReceiveLoopAsync(_cts.Token);
        }
        catch (Exception ex)
        {
            _isConnected = false;
            ConnectionStateChanged?.Invoke(this, new ConnectionStateEventArgs(false, ex.Message));
            throw;
        }
    }

    private async Task AuthenticateAsync()
    {
        var deviceId = _storage.GetDeviceId()!;
        var deviceToken = _storage.GetDeviceToken()!;
        var capabilities = _capabilities.GetAllIds();

        var authRequest = GatewayRequest.HandshakeAuth(deviceId, deviceToken, capabilities);
        await SendMessageAsync(authRequest);
    }

    /// <summary>
    /// Send an encrypted message to the gateway.
    /// </summary>
    public async Task SendMessageAsync(object message)
    {
        if (_webSocket?.State != WebSocketState.Open)
            throw new InvalidOperationException("WebSocket not connected");

        var deviceId = _storage.GetDeviceId()!;
        var sessionKey = _storage.GetSessionKey()!;

        var json = JsonSerializer.Serialize(message);
        var plaintext = Encoding.UTF8.GetBytes(json);

        var seq = Interlocked.Increment(ref _sequence);
        var encrypted = AgentCrypto.Encrypt(plaintext, sessionKey);

        var messageBytes = Encoding.UTF8.GetBytes(encrypted.ToCompact());

        lock (_sendLock)
        {
            _webSocket.SendAsync(
                new ArraySegment<byte>(messageBytes),
                WebSocketMessageType.Text,
                true,
                _cts?.Token ?? CancellationToken.None).Wait();
        }
    }

    private async Task ReceiveLoopAsync(CancellationToken cancellationToken)
    {
        var buffer = new byte[8192];
        var messageBuffer = new List<byte>();

        while (!cancellationToken.IsCancellationRequested && _webSocket?.State == WebSocketState.Open)
        {
            try
            {
                var result = await _webSocket.ReceiveAsync(
                    new ArraySegment<byte>(buffer), cancellationToken);

                if (result.MessageType == WebSocketMessageType.Close)
                {
                    _isConnected = false;
                    ConnectionStateChanged?.Invoke(this, new ConnectionStateEventArgs(false, "Server closed connection"));
                    break;
                }

                messageBuffer.AddRange(buffer.Take(result.Count));

                if (result.EndOfMessage)
                {
                    var messageText = Encoding.UTF8.GetString(messageBuffer.ToArray());
                    messageBuffer.Clear();

                    await HandleMessageAsync(messageText);
                }
            }
            catch (OperationCanceledException)
            {
                break;
            }
            catch (Exception ex)
            {
                ErrorOccurred?.Invoke(this, ex);
            }
        }
    }

    private async Task HandleMessageAsync(string messageText)
    {
        try
        {
            var sessionKey = _storage.GetSessionKey()!;

            // Try to parse as secure message
            SecureMessage? secureMessage;
            try
            {
                secureMessage = SecureMessage.FromCompact(messageText);
            }
            catch
            {
                // Plain message (possibly handshake response)
                MessageReceived?.Invoke(this, messageText);
                await ProcessPlainMessageAsync(messageText);
                return;
            }

            // Decrypt message
            var encryptedData = new EncryptedData(
                secureMessage.GetNonceBytes(),
                secureMessage.GetCiphertextBytes(),
                secureMessage.GetTagBytes()
            );
            var plaintext = AgentCrypto.Decrypt(encryptedData, sessionKey);
            var json = Encoding.UTF8.GetString(plaintext);

            MessageReceived?.Invoke(this, json);
            await ProcessDecryptedMessageAsync(json);
        }
        catch (Exception ex)
        {
            ErrorOccurred?.Invoke(this, ex);
        }
    }

    private async Task ProcessPlainMessageAsync(string json)
    {
        var doc = JsonDocument.Parse(json);
        var type = doc.RootElement.GetProperty("type").GetString();

        if (type == "res")
        {
            // Handle response
            var success = doc.RootElement.TryGetProperty("success", out var successProp)
                && successProp.GetBoolean();

            if (!success && doc.RootElement.TryGetProperty("error", out var errorProp))
            {
                var errorMsg = errorProp.GetProperty("message").GetString();
                ErrorOccurred?.Invoke(this, new Exception($"Server error: {errorMsg}"));
            }
        }

        await Task.CompletedTask;
    }

    private async Task ProcessDecryptedMessageAsync(string json)
    {
        var doc = JsonDocument.Parse(json);
        var type = doc.RootElement.GetProperty("type").GetString();

        switch (type)
        {
            case "event":
                await HandleEventAsync(doc);
                break;

            case "res":
                // Handle response
                break;
        }
    }

    private async Task HandleEventAsync(JsonDocument doc)
    {
        var eventName = doc.RootElement.GetProperty("event").GetString();

        if (eventName == GatewayEvent.NodeInvoke)
        {
            var payload = doc.RootElement.GetProperty("payload");
            var invokeId = payload.GetProperty("invokeId").GetString()!;
            var capabilityId = payload.GetProperty("capability").GetString()!;
            var args = payload.TryGetProperty("args", out var argsElem)
                ? JsonSerializer.Deserialize<Dictionary<string, object>>(argsElem.GetRawText())
                : new Dictionary<string, object>();

            await ExecuteCapabilityAsync(invokeId, capabilityId, args ?? new());
        }
        else if (eventName == GatewayEvent.Ping)
        {
            await SendMessageAsync(GatewayRequest.Ping());
        }
    }

    private async Task ExecuteCapabilityAsync(string invokeId, string capabilityId, Dictionary<string, object> args)
    {
        object? result = null;
        string? error = null;

        try
        {
            var capability = _capabilities.Get(capabilityId);
            if (capability == null)
            {
                error = $"Capability not found: {capabilityId}";
            }
            else
            {
                result = await capability.ExecuteAsync(args);
            }
        }
        catch (CapabilityException ex)
        {
            error = $"{ex.Code}: {ex.Message}";
        }
        catch (Exception ex)
        {
            error = $"Execution failed: {ex.Message}";
        }

        // Send result back to platform
        var response = GatewayRequest.InvokeResult(invokeId, result, error);
        await SendMessageAsync(response);
    }

    /// <summary>
    /// Disconnect from the gateway.
    /// </summary>
    public async Task DisconnectAsync()
    {
        _cts?.Cancel();

        if (_webSocket?.State == WebSocketState.Open)
        {
            try
            {
                await _webSocket.CloseAsync(
                    WebSocketCloseStatus.NormalClosure,
                    "Agent disconnecting",
                    CancellationToken.None);
            }
            catch
            {
                // Ignore close errors
            }
        }

        if (_receiveTask != null)
        {
            try
            {
                await _receiveTask;
            }
            catch (OperationCanceledException)
            {
                // Expected
            }
        }

        _isConnected = false;
        ConnectionStateChanged?.Invoke(this, new ConnectionStateEventArgs(false));
    }

    public async ValueTask DisposeAsync()
    {
        await DisconnectAsync();
        _webSocket?.Dispose();
        _cts?.Dispose();
    }
}

public class ConnectionStateEventArgs : EventArgs
{
    public bool IsConnected { get; }
    public string? Message { get; }

    public ConnectionStateEventArgs(bool isConnected, string? message = null)
    {
        IsConnected = isConnected;
        Message = message;
    }
}
