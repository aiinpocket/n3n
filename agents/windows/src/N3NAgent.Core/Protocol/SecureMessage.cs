using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace N3NAgent.Core.Protocol;

/// <summary>
/// Secure message wrapper with encryption header.
/// Format: base64url(header).base64url(ciphertext).base64url(tag)
/// </summary>
public class SecureMessage
{
    public Header MessageHeader { get; set; } = new();
    public string Ciphertext { get; set; } = "";
    public string Tag { get; set; } = "";

    public class Header
    {
        [JsonPropertyName("v")]
        public int Version { get; set; } = 1;

        [JsonPropertyName("alg")]
        public string Algorithm { get; set; } = "A256GCM";

        [JsonPropertyName("did")]
        public string DeviceId { get; set; } = "";

        [JsonPropertyName("ts")]
        public long Timestamp { get; set; }

        [JsonPropertyName("seq")]
        public long Sequence { get; set; }

        [JsonPropertyName("nonce")]
        public string Nonce { get; set; } = "";

        [JsonPropertyName("dir")]
        public string Direction { get; set; } = "c2s";
    }

    /// <summary>
    /// Serialize to compact format: header.ciphertext.tag
    /// </summary>
    public string ToCompact()
    {
        var headerJson = JsonSerializer.Serialize(MessageHeader);
        var headerB64 = Base64UrlEncode(Encoding.UTF8.GetBytes(headerJson));
        return $"{headerB64}.{Ciphertext}.{Tag}";
    }

    /// <summary>
    /// Parse from compact format
    /// </summary>
    public static SecureMessage FromCompact(string compact)
    {
        var parts = compact.Split('.');
        if (parts.Length != 3)
            throw new SecureMessageException("Invalid secure message format");

        var headerJson = Encoding.UTF8.GetString(Base64UrlDecode(parts[0]));
        var header = JsonSerializer.Deserialize<Header>(headerJson)
            ?? throw new SecureMessageException("Invalid header");

        return new SecureMessage
        {
            MessageHeader = header,
            Ciphertext = parts[1],
            Tag = parts[2]
        };
    }

    /// <summary>
    /// Get the header as bytes for AAD
    /// </summary>
    public byte[] GetHeaderBytes()
    {
        return Encoding.UTF8.GetBytes(JsonSerializer.Serialize(MessageHeader));
    }

    public byte[] GetCiphertextBytes() => Base64UrlDecode(Ciphertext);
    public byte[] GetTagBytes() => Base64UrlDecode(Tag);
    public byte[] GetNonceBytes() => Base64UrlDecode(MessageHeader.Nonce);

    // Base64URL helpers
    public static string Base64UrlEncode(byte[] data)
    {
        return Convert.ToBase64String(data)
            .Replace('+', '-')
            .Replace('/', '_')
            .TrimEnd('=');
    }

    public static byte[] Base64UrlDecode(string data)
    {
        var base64 = data
            .Replace('-', '+')
            .Replace('_', '/');

        switch (base64.Length % 4)
        {
            case 2: base64 += "=="; break;
            case 3: base64 += "="; break;
        }

        return Convert.FromBase64String(base64);
    }
}

public class SecureMessageException : Exception
{
    public SecureMessageException(string message) : base(message) { }
}
