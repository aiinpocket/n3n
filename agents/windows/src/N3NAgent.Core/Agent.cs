using N3NAgent.Core.Network;
using N3NAgent.Core.Storage;
using N3NAgent.Core.Capabilities;

namespace N3NAgent.Core;

/// <summary>
/// Main N3N Agent orchestrator.
/// </summary>
public class Agent : IAsyncDisposable
{
    private readonly CredentialStorage _storage;
    private readonly CapabilityRegistry _capabilities;
    private AgentConnection? _connection;
    private CancellationTokenSource? _cts;
    private bool _isRunning;
    private readonly int _reconnectDelayMs;
    private readonly int _maxReconnectDelayMs;

    public event EventHandler<AgentStatusEventArgs>? StatusChanged;
    public event EventHandler<string>? LogMessage;

    public bool IsRunning => _isRunning;
    public bool IsConnected => _connection?.IsConnected ?? false;

    public Agent(int reconnectDelayMs = 5000, int maxReconnectDelayMs = 60000)
    {
        _storage = CredentialStorage.Instance;
        _capabilities = CapabilityRegistry.Instance;
        _reconnectDelayMs = reconnectDelayMs;
        _maxReconnectDelayMs = maxReconnectDelayMs;
    }

    /// <summary>
    /// Start the agent and connect to the platform.
    /// </summary>
    public async Task StartAsync(CancellationToken cancellationToken = default)
    {
        if (_isRunning)
            throw new InvalidOperationException("Agent is already running");

        var deviceId = _storage.GetDeviceId();
        if (string.IsNullOrEmpty(deviceId))
            throw new InvalidOperationException("Agent not paired. Run 'pair' command first.");

        _cts = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        _isRunning = true;
        StatusChanged?.Invoke(this, new AgentStatusEventArgs(AgentStatus.Starting));

        Log($"Starting N3N Agent...");
        Log($"Device ID: {deviceId}");
        Log($"Capabilities: {string.Join(", ", _capabilities.GetAllIds())}");

        await RunConnectionLoopAsync(_cts.Token);
    }

    private async Task RunConnectionLoopAsync(CancellationToken cancellationToken)
    {
        var reconnectDelay = _reconnectDelayMs;

        while (!cancellationToken.IsCancellationRequested)
        {
            try
            {
                _connection = new AgentConnection();

                _connection.ConnectionStateChanged += (s, e) =>
                {
                    if (e.IsConnected)
                    {
                        StatusChanged?.Invoke(this, new AgentStatusEventArgs(AgentStatus.Connected));
                        Log("Connected to platform");
                        reconnectDelay = _reconnectDelayMs; // Reset delay on successful connection
                    }
                    else
                    {
                        StatusChanged?.Invoke(this, new AgentStatusEventArgs(AgentStatus.Disconnected, e.Message));
                        Log($"Disconnected: {e.Message}");
                    }
                };

                _connection.ErrorOccurred += (s, e) =>
                {
                    Log($"Error: {e.Message}");
                };

                _connection.MessageReceived += (s, msg) =>
                {
                    // Log($"Message: {msg}");
                };

                StatusChanged?.Invoke(this, new AgentStatusEventArgs(AgentStatus.Connecting));
                Log("Connecting to platform...");

                await _connection.ConnectAsync(cancellationToken);

                // Wait until disconnected
                while (_connection.IsConnected && !cancellationToken.IsCancellationRequested)
                {
                    await Task.Delay(1000, cancellationToken);
                }
            }
            catch (OperationCanceledException)
            {
                break;
            }
            catch (Exception ex)
            {
                Log($"Connection error: {ex.Message}");
                StatusChanged?.Invoke(this, new AgentStatusEventArgs(AgentStatus.Error, ex.Message));
            }

            if (cancellationToken.IsCancellationRequested)
                break;

            // Clean up old connection
            if (_connection != null)
            {
                await _connection.DisposeAsync();
                _connection = null;
            }

            // Reconnect with exponential backoff
            Log($"Reconnecting in {reconnectDelay / 1000} seconds...");
            await Task.Delay(reconnectDelay, cancellationToken);
            reconnectDelay = Math.Min(reconnectDelay * 2, _maxReconnectDelayMs);
        }

        _isRunning = false;
        StatusChanged?.Invoke(this, new AgentStatusEventArgs(AgentStatus.Stopped));
        Log("Agent stopped");
    }

    /// <summary>
    /// Stop the agent.
    /// </summary>
    public async Task StopAsync()
    {
        _cts?.Cancel();

        if (_connection != null)
        {
            await _connection.DisconnectAsync();
        }
    }

    /// <summary>
    /// Get agent status information.
    /// </summary>
    public AgentInfo GetInfo()
    {
        return new AgentInfo
        {
            IsRunning = _isRunning,
            IsConnected = IsConnected,
            DeviceId = _storage.GetDeviceId(),
            PlatformUrl = _storage.GetPlatformUrl(),
            Capabilities = _capabilities.GetAllIds(),
            Platform = "windows",
            Version = "1.0.0"
        };
    }

    private void Log(string message)
    {
        LogMessage?.Invoke(this, message);
    }

    public async ValueTask DisposeAsync()
    {
        await StopAsync();
        if (_connection != null)
        {
            await _connection.DisposeAsync();
        }
        _cts?.Dispose();
    }
}

public enum AgentStatus
{
    Starting,
    Connecting,
    Connected,
    Disconnected,
    Error,
    Stopped
}

public class AgentStatusEventArgs : EventArgs
{
    public AgentStatus Status { get; }
    public string? Message { get; }

    public AgentStatusEventArgs(AgentStatus status, string? message = null)
    {
        Status = status;
        Message = message;
    }
}

public class AgentInfo
{
    public bool IsRunning { get; set; }
    public bool IsConnected { get; set; }
    public string? DeviceId { get; set; }
    public string? PlatformUrl { get; set; }
    public List<string> Capabilities { get; set; } = new();
    public string Platform { get; set; } = "";
    public string Version { get; set; } = "";
}
