using System.Security.Cryptography;
using System.Text;

namespace N3NAgent.Core.Crypto;

/// <summary>
/// Core cryptographic operations for Agent communication.
/// Implements X25519 key exchange and AES-256-GCM encryption.
/// </summary>
public class AgentCrypto
{
    private static readonly Lazy<AgentCrypto> _instance = new(() => new AgentCrypto());
    public static AgentCrypto Instance => _instance.Value;

    private AgentCrypto() { }

    // MARK: - Key Generation

    /// <summary>
    /// Generate a new X25519 key pair for key exchange.
    /// </summary>
    public static (byte[] PrivateKey, byte[] PublicKey) GenerateKeyPair()
    {
        using var ecdh = ECDiffieHellman.Create(ECCurve.NamedCurves.nistP256);
        var privateKey = ecdh.ExportECPrivateKey();
        var publicKey = ecdh.ExportSubjectPublicKeyInfo();

        return (privateKey, publicKey);
    }

    /// <summary>
    /// Derive shared secret from our private key and peer's public key.
    /// </summary>
    public static byte[] DeriveSharedSecret(byte[] privateKey, byte[] peerPublicKey)
    {
        using var ecdh = ECDiffieHellman.Create();
        ecdh.ImportECPrivateKey(privateKey, out _);

        using var peerEcdh = ECDiffieHellman.Create();
        peerEcdh.ImportSubjectPublicKeyInfo(peerPublicKey, out _);

        return ecdh.DeriveKeyMaterial(peerEcdh.PublicKey);
    }

    /// <summary>
    /// Derive a session key from shared secret using HKDF.
    /// </summary>
    public static byte[] DeriveSessionKey(byte[] sharedSecret, string deviceId)
    {
        var salt = Encoding.UTF8.GetBytes("n3n-agent-session");
        var info = Encoding.UTF8.GetBytes($"session-{deviceId}");
        return HKDF.DeriveKey(HashAlgorithmName.SHA256, sharedSecret, 32, salt, info);
    }

    /// <summary>
    /// Derive encryption keys from shared secret using HKDF.
    /// </summary>
    public DerivedKeys DeriveKeys(byte[] sharedSecret, byte[] salt, byte[] info)
    {
        var prk = HKDF.Extract(HashAlgorithmName.SHA256, sharedSecret, salt);

        var encryptKeyC2S = HKDF.Expand(HashAlgorithmName.SHA256, prk, 32,
            Encoding.UTF8.GetBytes("encrypt-c2s").Concat(info).ToArray());
        var encryptKeyS2C = HKDF.Expand(HashAlgorithmName.SHA256, prk, 32,
            Encoding.UTF8.GetBytes("encrypt-s2c").Concat(info).ToArray());
        var authKey = HKDF.Expand(HashAlgorithmName.SHA256, prk, 32,
            Encoding.UTF8.GetBytes("auth").Concat(info).ToArray());

        return new DerivedKeys(encryptKeyC2S, encryptKeyS2C, authKey);
    }

    // MARK: - Encryption

    /// <summary>
    /// Encrypt data using AES-256-GCM.
    /// </summary>
    public static EncryptedData Encrypt(byte[] plaintext, byte[] key, byte[]? aad = null)
    {
        using var aes = new AesGcm(key, 16);
        var nonce = new byte[12];
        RandomNumberGenerator.Fill(nonce);

        var ciphertext = new byte[plaintext.Length];
        var tag = new byte[16];

        aes.Encrypt(nonce, plaintext, ciphertext, tag, aad);

        return new EncryptedData(nonce, ciphertext, tag);
    }

    /// <summary>
    /// Decrypt data using AES-256-GCM.
    /// </summary>
    public static byte[] Decrypt(EncryptedData data, byte[] key, byte[]? aad = null)
    {
        using var aes = new AesGcm(key, 16);
        var plaintext = new byte[data.Ciphertext.Length];

        aes.Decrypt(data.Nonce, data.Ciphertext, data.Tag, plaintext, aad);

        return plaintext;
    }

    // MARK: - Utility

    /// <summary>
    /// Parse a public key from Base64-encoded bytes.
    /// </summary>
    public byte[] ParsePublicKey(string base64)
    {
        return Convert.FromBase64String(base64);
    }

    /// <summary>
    /// Encode a public key to Base64.
    /// </summary>
    public string EncodePublicKey(byte[] publicKey)
    {
        return Convert.ToBase64String(publicKey);
    }

    /// <summary>
    /// Compute SHA-256 fingerprint.
    /// </summary>
    public string ComputeFingerprint(byte[] data)
    {
        using var sha256 = SHA256.Create();
        var hash = sha256.ComputeHash(data);
        return Convert.ToBase64String(hash);
    }

    /// <summary>
    /// Generate a random sequence number starting point.
    /// </summary>
    public long GenerateInitialSequence()
    {
        var buffer = new byte[8];
        RandomNumberGenerator.Fill(buffer);
        return Math.Abs(BitConverter.ToInt64(buffer, 0) % 1_000_000);
    }

    /// <summary>
    /// Generate device fingerprint based on hardware info.
    /// </summary>
    public string GenerateDeviceFingerprint()
    {
        var info = new StringBuilder();

        // Get machine name
        info.Append(Environment.MachineName);

        // Get OS version
        info.Append(Environment.OSVersion.ToString());

        // Get username
        info.Append(Environment.UserName);

        return ComputeFingerprint(Encoding.UTF8.GetBytes(info.ToString()));
    }

    /// <summary>
    /// Generate a 6-digit pairing code.
    /// </summary>
    public string GeneratePairingCode()
    {
        var buffer = new byte[4];
        RandomNumberGenerator.Fill(buffer);
        var code = Math.Abs(BitConverter.ToInt32(buffer, 0)) % 1_000_000;
        return code.ToString("D6");
    }
}

// MARK: - Data Types

public record KeyPair(byte[] PrivateKey, byte[] PublicKey)
{
    public string PublicKeyBase64 => Convert.ToBase64String(PublicKey);
}

public record DerivedKeys(
    byte[] EncryptKeyClientToServer,
    byte[] EncryptKeyServerToClient,
    byte[] AuthKey
);

public record EncryptedData(byte[] Nonce, byte[] Ciphertext, byte[] Tag)
{
    public byte[] Combined => Nonce.Concat(Ciphertext).Concat(Tag).ToArray();

    /// <summary>
    /// Convert to compact Base64 string format.
    /// </summary>
    public string ToCompact()
    {
        var nonceB64 = Convert.ToBase64String(Nonce);
        var ciphertextB64 = Convert.ToBase64String(Ciphertext);
        var tagB64 = Convert.ToBase64String(Tag);
        return $"{nonceB64}.{ciphertextB64}.{tagB64}";
    }

    /// <summary>
    /// Parse from compact Base64 string format.
    /// </summary>
    public static EncryptedData FromCompact(string compact)
    {
        var parts = compact.Split('.');
        if (parts.Length != 3)
            throw new CryptoException("Invalid compact format");

        return new EncryptedData(
            Convert.FromBase64String(parts[0]),
            Convert.FromBase64String(parts[1]),
            Convert.FromBase64String(parts[2])
        );
    }
}

public class CryptoException : Exception
{
    public CryptoException(string message) : base(message) { }
    public CryptoException(string message, Exception inner) : base(message, inner) { }
}
