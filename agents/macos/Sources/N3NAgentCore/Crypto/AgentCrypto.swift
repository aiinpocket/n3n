import Foundation
import CryptoKit

/// Core cryptographic operations for Agent communication.
/// Implements X25519 key exchange and AES-256-GCM encryption.
public final class AgentCrypto {

    public static let shared = AgentCrypto()

    private init() {}

    // MARK: - Key Generation

    /// Generate a new X25519 key pair for key exchange.
    public func generateKeyPair() -> KeyPair {
        let privateKey = Curve25519.KeyAgreement.PrivateKey()
        return KeyPair(
            privateKey: privateKey,
            publicKey: privateKey.publicKey
        )
    }

    /// Derive shared secret from our private key and peer's public key.
    public func deriveSharedSecret(
        privateKey: Curve25519.KeyAgreement.PrivateKey,
        peerPublicKey: Curve25519.KeyAgreement.PublicKey
    ) throws -> SharedSecret {
        return try privateKey.sharedSecretFromKeyAgreement(with: peerPublicKey)
    }

    /// Derive encryption keys from shared secret using HKDF.
    public func deriveKeys(
        sharedSecret: SharedSecret,
        salt: Data,
        info: Data
    ) -> DerivedKeys {
        // HKDF-SHA256
        let symmetricKey = sharedSecret.hkdfDerivedSymmetricKey(
            using: SHA256.self,
            salt: salt,
            sharedInfo: info,
            outputByteCount: 32
        )

        // Derive separate keys for different purposes
        let encryptKeyC2S = deriveSubKey(from: symmetricKey, info: "encrypt-c2s")
        let encryptKeyS2C = deriveSubKey(from: symmetricKey, info: "encrypt-s2c")
        let authKey = deriveSubKey(from: symmetricKey, info: "auth")

        return DerivedKeys(
            encryptKeyClientToServer: encryptKeyC2S,
            encryptKeyServerToClient: encryptKeyS2C,
            authKey: authKey
        )
    }

    private func deriveSubKey(from masterKey: SymmetricKey, info: String) -> SymmetricKey {
        let infoData = Data(info.utf8)
        return HKDF<SHA256>.deriveKey(
            inputKeyMaterial: masterKey,
            info: infoData,
            outputByteCount: 32
        )
    }

    // MARK: - Encryption

    /// Encrypt data using AES-256-GCM.
    public func encrypt(
        plaintext: Data,
        key: SymmetricKey,
        aad: Data? = nil
    ) throws -> EncryptedData {
        let nonce = AES.GCM.Nonce()

        let sealedBox: AES.GCM.SealedBox
        if let aad = aad {
            sealedBox = try AES.GCM.seal(plaintext, using: key, nonce: nonce, authenticating: aad)
        } else {
            sealedBox = try AES.GCM.seal(plaintext, using: key, nonce: nonce)
        }

        return EncryptedData(
            nonce: Data(nonce),
            ciphertext: sealedBox.ciphertext,
            tag: sealedBox.tag
        )
    }

    /// Decrypt data using AES-256-GCM.
    public func decrypt(
        ciphertext: Data,
        tag: Data,
        key: SymmetricKey,
        nonce: Data,
        aad: Data? = nil
    ) throws -> Data {
        let gcmNonce = try AES.GCM.Nonce(data: nonce)

        let sealedBox = try AES.GCM.SealedBox(
            nonce: gcmNonce,
            ciphertext: ciphertext,
            tag: tag
        )

        if let aad = aad {
            return try AES.GCM.open(sealedBox, using: key, authenticating: aad)
        } else {
            return try AES.GCM.open(sealedBox, using: key)
        }
    }

    // MARK: - Utility

    /// Parse a public key from Base64-encoded bytes.
    public func parsePublicKey(base64: String) throws -> Curve25519.KeyAgreement.PublicKey {
        guard let data = Data(base64Encoded: base64) else {
            throw CryptoError.invalidKeyFormat
        }
        return try Curve25519.KeyAgreement.PublicKey(rawRepresentation: data)
    }

    /// Encode a public key to Base64.
    public func encodePublicKey(_ publicKey: Curve25519.KeyAgreement.PublicKey) -> String {
        return publicKey.rawRepresentation.base64EncodedString()
    }

    /// Compute SHA-256 fingerprint.
    public func computeFingerprint(data: Data) -> String {
        let hash = SHA256.hash(data: data)
        return Data(hash).base64EncodedString()
    }

    /// Generate a random sequence number starting point.
    public func generateInitialSequence() -> Int64 {
        return Int64.random(in: 0..<1_000_000)
    }

    /// Generate device fingerprint based on hardware info.
    public func generateDeviceFingerprint() -> String {
        var info = ""

        // Get hardware UUID
        if let hardwareUUID = getHardwareUUID() {
            info += hardwareUUID
        }

        // Add OS version
        let osVersion = ProcessInfo.processInfo.operatingSystemVersionString
        info += osVersion

        // Add machine name
        info += Host.current().localizedName ?? "unknown"

        return computeFingerprint(data: Data(info.utf8))
    }

    private func getHardwareUUID() -> String? {
        let platformExpert = IOServiceGetMatchingService(
            kIOMainPortDefault,
            IOServiceMatching("IOPlatformExpertDevice")
        )

        defer { IOObjectRelease(platformExpert) }

        guard platformExpert != 0 else { return nil }

        guard let serialNumberAsCFString = IORegistryEntryCreateCFProperty(
            platformExpert,
            kIOPlatformUUIDKey as CFString,
            kCFAllocatorDefault,
            0
        )?.takeUnretainedValue() as? String else {
            return nil
        }

        return serialNumberAsCFString
    }
}

// MARK: - Data Types

public struct KeyPair {
    public let privateKey: Curve25519.KeyAgreement.PrivateKey
    public let publicKey: Curve25519.KeyAgreement.PublicKey

    public var publicKeyBase64: String {
        publicKey.rawRepresentation.base64EncodedString()
    }

    public var privateKeyData: Data {
        privateKey.rawRepresentation
    }
}

public struct DerivedKeys {
    public let encryptKeyClientToServer: SymmetricKey
    public let encryptKeyServerToClient: SymmetricKey
    public let authKey: SymmetricKey
}

public struct EncryptedData {
    public let nonce: Data
    public let ciphertext: Data
    public let tag: Data

    public var combined: Data {
        nonce + ciphertext + tag
    }
}

public enum CryptoError: Error, LocalizedError {
    case invalidKeyFormat
    case encryptionFailed
    case decryptionFailed
    case invalidNonce

    public var errorDescription: String? {
        switch self {
        case .invalidKeyFormat:
            return "Invalid key format"
        case .encryptionFailed:
            return "Encryption failed"
        case .decryptionFailed:
            return "Decryption failed"
        case .invalidNonce:
            return "Invalid nonce"
        }
    }
}
