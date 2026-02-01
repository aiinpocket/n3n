using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Serialization;
using N3NAgent.Core.Crypto;
using N3NAgent.Core.Storage;
using N3NAgent.Core.Capabilities;

namespace N3NAgent.Core.Network;

/// <summary>
/// Service for pairing the agent with the N3N platform.
/// </summary>
public class PairingService
{
    private readonly HttpClient _httpClient;
    private readonly CredentialStorage _storage;
    private readonly string _platformUrl;

    public PairingService(string platformUrl)
    {
        _platformUrl = platformUrl.TrimEnd('/');
        _httpClient = new HttpClient
        {
            Timeout = TimeSpan.FromSeconds(30)
        };
        _storage = CredentialStorage.Instance;
    }

    /// <summary>
    /// Initiate pairing with the platform using a 6-digit code.
    /// </summary>
    public async Task<PairingResult> PairAsync(string pairingCode)
    {
        // Generate new key pair
        var (privateKey, publicKey) = AgentCrypto.GenerateKeyPair();

        // Send public key to platform
        var initRequest = new PairingInitRequest
        {
            PairingCode = pairingCode,
            AgentPublicKey = Convert.ToBase64String(publicKey),
            Capabilities = CapabilityRegistry.Instance.GetAllIds(),
            ClientInfo = new ClientInfo
            {
                Version = "1.0.0",
                Platform = "windows",
                Architecture = Environment.Is64BitOperatingSystem ? "x64" : "x86",
                Hostname = Environment.MachineName
            }
        };

        var response = await _httpClient.PostAsJsonAsync(
            $"{_platformUrl}/api/agents/pairing/init",
            initRequest);

        if (!response.IsSuccessStatusCode)
        {
            var error = await response.Content.ReadAsStringAsync();
            throw new PairingException($"Pairing failed: {response.StatusCode} - {error}");
        }

        var result = await response.Content.ReadFromJsonAsync<PairingInitResponse>();
        if (result == null)
            throw new PairingException("Invalid response from platform");

        // Derive shared secret using ECDH
        var platformPublicKey = Convert.FromBase64String(result.PlatformPublicKey);
        var sharedSecret = AgentCrypto.DeriveSharedSecret(privateKey, platformPublicKey);

        // Derive session key
        var sessionKey = AgentCrypto.DeriveSessionKey(sharedSecret, result.DeviceId);

        // Store credentials securely
        _storage.StoreDeviceKeys(new DeviceKeys
        {
            DeviceId = result.DeviceId,
            DeviceToken = result.DeviceToken,
            PlatformPublicKey = result.PlatformPublicKey,
            EncryptKeyC2S = sessionKey,
            EncryptKeyS2C = sessionKey, // Same key for now
            PairedAt = DateTime.UtcNow
        });
        _storage.StoreConfig(new AgentConfig
        {
            PlatformUrl = _platformUrl
        });

        return new PairingResult
        {
            DeviceId = result.DeviceId,
            DeviceName = result.DeviceName,
            Success = true
        };
    }

    /// <summary>
    /// Check if the agent is already paired.
    /// </summary>
    public bool IsPaired()
    {
        var deviceId = _storage.GetDeviceId();
        var sessionKey = _storage.GetSessionKey();
        return !string.IsNullOrEmpty(deviceId) && sessionKey != null;
    }

    /// <summary>
    /// Get current pairing status.
    /// </summary>
    public PairingStatus GetStatus()
    {
        return new PairingStatus
        {
            IsPaired = IsPaired(),
            DeviceId = _storage.GetDeviceId(),
            PlatformUrl = _storage.GetPlatformUrl()
        };
    }

    /// <summary>
    /// Unpair the agent from the platform.
    /// </summary>
    public async Task UnpairAsync()
    {
        var deviceId = _storage.GetDeviceId();
        var deviceToken = _storage.GetDeviceToken();

        if (!string.IsNullOrEmpty(deviceId) && !string.IsNullOrEmpty(deviceToken))
        {
            try
            {
                _httpClient.DefaultRequestHeaders.Add("X-Device-Id", deviceId);
                _httpClient.DefaultRequestHeaders.Add("X-Device-Token", deviceToken);

                var platformUrl = _storage.GetPlatformUrl() ?? _platformUrl;
                await _httpClient.DeleteAsync($"{platformUrl}/api/agents/devices/{deviceId}");
            }
            catch
            {
                // Ignore errors - we're unpairing anyway
            }
        }

        // Clear stored credentials
        _storage.ClearAll();
    }
}

public class PairingInitRequest
{
    [JsonPropertyName("pairingCode")]
    public string PairingCode { get; set; } = "";

    [JsonPropertyName("agentPublicKey")]
    public string AgentPublicKey { get; set; } = "";

    [JsonPropertyName("capabilities")]
    public List<string> Capabilities { get; set; } = new();

    [JsonPropertyName("clientInfo")]
    public ClientInfo ClientInfo { get; set; } = new();
}

public class ClientInfo
{
    [JsonPropertyName("version")]
    public string Version { get; set; } = "";

    [JsonPropertyName("platform")]
    public string Platform { get; set; } = "";

    [JsonPropertyName("architecture")]
    public string Architecture { get; set; } = "";

    [JsonPropertyName("hostname")]
    public string Hostname { get; set; } = "";
}

public class PairingInitResponse
{
    [JsonPropertyName("deviceId")]
    public string DeviceId { get; set; } = "";

    [JsonPropertyName("deviceName")]
    public string DeviceName { get; set; } = "";

    [JsonPropertyName("deviceToken")]
    public string DeviceToken { get; set; } = "";

    [JsonPropertyName("platformPublicKey")]
    public string PlatformPublicKey { get; set; } = "";
}

public class PairingResult
{
    public bool Success { get; set; }
    public string DeviceId { get; set; } = "";
    public string DeviceName { get; set; } = "";
    public string? Error { get; set; }
}

public class PairingStatus
{
    public bool IsPaired { get; set; }
    public string? DeviceId { get; set; }
    public string? PlatformUrl { get; set; }
}

public class PairingException : Exception
{
    public PairingException(string message) : base(message) { }
}
