using System.Runtime.InteropServices;
using System.Text;
using System.Text.Json;

namespace N3NAgent.Core.Storage;

/// <summary>
/// Secure storage using Windows Credential Manager.
/// </summary>
public class CredentialStorage
{
    private static readonly Lazy<CredentialStorage> _instance = new(() => new CredentialStorage());
    public static CredentialStorage Instance => _instance.Value;

    private const string CredentialPrefix = "N3NAgent_";

    private CredentialStorage() { }

    // MARK: - Device Keys

    /// <summary>
    /// Store device keys after successful pairing.
    /// </summary>
    public void StoreDeviceKeys(DeviceKeys keys)
    {
        var json = JsonSerializer.Serialize(keys);
        WriteCredential($"{CredentialPrefix}DeviceKeys", json);
    }

    /// <summary>
    /// Load device keys.
    /// </summary>
    public DeviceKeys? LoadDeviceKeys()
    {
        var json = ReadCredential($"{CredentialPrefix}DeviceKeys");
        if (string.IsNullOrEmpty(json))
            return null;

        return JsonSerializer.Deserialize<DeviceKeys>(json);
    }

    /// <summary>
    /// Delete device keys (unpair).
    /// </summary>
    public void DeleteDeviceKeys()
    {
        DeleteCredential($"{CredentialPrefix}DeviceKeys");
    }

    // MARK: - Configuration

    /// <summary>
    /// Store agent configuration.
    /// </summary>
    public void StoreConfig(AgentConfig config)
    {
        var json = JsonSerializer.Serialize(config);
        WriteCredential($"{CredentialPrefix}Config", json);
    }

    /// <summary>
    /// Load agent configuration.
    /// </summary>
    public AgentConfig? LoadConfig()
    {
        var json = ReadCredential($"{CredentialPrefix}Config");
        if (string.IsNullOrEmpty(json))
            return null;

        return JsonSerializer.Deserialize<AgentConfig>(json);
    }

    /// <summary>
    /// Clear all stored data (full reset).
    /// </summary>
    public void ClearAll()
    {
        DeleteCredential($"{CredentialPrefix}DeviceKeys");
        DeleteCredential($"{CredentialPrefix}Config");
    }

    // MARK: - Helper Methods

    /// <summary>
    /// Get the device ID if paired.
    /// </summary>
    public string? GetDeviceId()
    {
        var keys = LoadDeviceKeys();
        return keys?.DeviceId;
    }

    /// <summary>
    /// Get the platform URL from config.
    /// </summary>
    public string? GetPlatformUrl()
    {
        var config = LoadConfig();
        return config?.PlatformUrl;
    }

    /// <summary>
    /// Get the device token if paired.
    /// </summary>
    public string? GetDeviceToken()
    {
        var keys = LoadDeviceKeys();
        return keys?.DeviceToken;
    }

    /// <summary>
    /// Get the session key (C2S encryption key) if paired.
    /// </summary>
    public byte[]? GetSessionKey()
    {
        var keys = LoadDeviceKeys();
        return keys?.EncryptKeyC2S;
    }

    // MARK: - Windows Credential Manager P/Invoke

    [DllImport("advapi32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    private static extern bool CredWriteW(ref CREDENTIAL credential, uint flags);

    [DllImport("advapi32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    private static extern bool CredReadW(string targetName, uint type, uint flags, out IntPtr credential);

    [DllImport("advapi32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    private static extern bool CredDeleteW(string targetName, uint type, uint flags);

    [DllImport("advapi32.dll")]
    private static extern void CredFree(IntPtr credential);

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct CREDENTIAL
    {
        public uint Flags;
        public uint Type;
        public string TargetName;
        public string Comment;
        public long LastWritten;
        public uint CredentialBlobSize;
        public IntPtr CredentialBlob;
        public uint Persist;
        public uint AttributeCount;
        public IntPtr Attributes;
        public string TargetAlias;
        public string UserName;
    }

    private const uint CRED_TYPE_GENERIC = 1;
    private const uint CRED_PERSIST_LOCAL_MACHINE = 2;

    private void WriteCredential(string name, string value)
    {
        var bytes = Encoding.UTF8.GetBytes(value);
        var blob = Marshal.AllocHGlobal(bytes.Length);

        try
        {
            Marshal.Copy(bytes, 0, blob, bytes.Length);

            var credential = new CREDENTIAL
            {
                Flags = 0,
                Type = CRED_TYPE_GENERIC,
                TargetName = name,
                Comment = "N3N Agent Credential",
                CredentialBlobSize = (uint)bytes.Length,
                CredentialBlob = blob,
                Persist = CRED_PERSIST_LOCAL_MACHINE,
                UserName = Environment.UserName
            };

            if (!CredWriteW(ref credential, 0))
            {
                throw new StorageException($"Failed to write credential: {Marshal.GetLastWin32Error()}");
            }
        }
        finally
        {
            Marshal.FreeHGlobal(blob);
        }
    }

    private string? ReadCredential(string name)
    {
        if (!CredReadW(name, CRED_TYPE_GENERIC, 0, out var credentialPtr))
        {
            var error = Marshal.GetLastWin32Error();
            if (error == 1168) // ERROR_NOT_FOUND
                return null;
            throw new StorageException($"Failed to read credential: {error}");
        }

        try
        {
            var credential = Marshal.PtrToStructure<CREDENTIAL>(credentialPtr);
            if (credential.CredentialBlobSize == 0)
                return null;

            var bytes = new byte[credential.CredentialBlobSize];
            Marshal.Copy(credential.CredentialBlob, bytes, 0, bytes.Length);
            return Encoding.UTF8.GetString(bytes);
        }
        finally
        {
            CredFree(credentialPtr);
        }
    }

    private void DeleteCredential(string name)
    {
        CredDeleteW(name, CRED_TYPE_GENERIC, 0);
    }
}

// MARK: - Data Types

public class DeviceKeys
{
    public string DeviceId { get; set; } = "";
    public string DeviceToken { get; set; } = "";
    public string PlatformPublicKey { get; set; } = "";
    public string PlatformFingerprint { get; set; } = "";
    public byte[] EncryptKeyC2S { get; set; } = Array.Empty<byte>();
    public byte[] EncryptKeyS2C { get; set; } = Array.Empty<byte>();
    public byte[] AuthKey { get; set; } = Array.Empty<byte>();
    public DateTime PairedAt { get; set; }
    public long LastSequence { get; set; }
}

public class AgentConfig
{
    public string PlatformUrl { get; set; } = "wss://localhost:8080/gateway/agent/secure";
    public string DeviceName { get; set; } = Environment.MachineName;
    public bool EnableDirectConnection { get; set; }
    public int ListenPort { get; set; } = 9999;
    public string? ExternalAddress { get; set; }
    public bool AutoStart { get; set; } = true;
    public string LogLevel { get; set; } = "info";
}

public class StorageException : Exception
{
    public StorageException(string message) : base(message) { }
}

/// <summary>
/// Registration config file structure for token-based registration.
/// </summary>
public class RegistrationConfig
{
    public int Version { get; set; }
    public GatewayInfo Gateway { get; set; } = new();
    public RegistrationInfo Registration { get; set; } = new();

    public class GatewayInfo
    {
        public string Url { get; set; } = "";
        public string Domain { get; set; } = "";
        public int Port { get; set; }
    }

    public class RegistrationInfo
    {
        public string Token { get; set; } = "";
        public string AgentId { get; set; } = "";
    }

    /// <summary>
    /// Load config from a JSON file.
    /// </summary>
    public static RegistrationConfig? LoadFromFile(string path)
    {
        if (!File.Exists(path))
            return null;

        var json = File.ReadAllText(path);
        return JsonSerializer.Deserialize<RegistrationConfig>(json, new JsonSerializerOptions
        {
            PropertyNameCaseInsensitive = true
        });
    }

    /// <summary>
    /// Find config file in common locations.
    /// </summary>
    public static string? FindConfigFile()
    {
        var locations = new[]
        {
            // Same directory as executable
            Path.Combine(AppContext.BaseDirectory, "n3n-agent-config.json"),
            // Current working directory
            Path.Combine(Directory.GetCurrentDirectory(), "n3n-agent-config.json"),
            // User's home directory
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "n3n-agent-config.json"),
            // AppData local
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "N3N Agent", "config.json"),
        };

        foreach (var location in locations)
        {
            if (File.Exists(location))
                return location;
        }

        return null;
    }
}
