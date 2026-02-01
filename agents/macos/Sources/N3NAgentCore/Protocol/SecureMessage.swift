import Foundation
import CryptoKit

/// Secure message wrapper with encryption header.
/// Format: base64url(header).base64url(ciphertext).base64url(tag)
public struct SecureMessage: Codable {

    public let header: Header
    public let ciphertext: String  // Base64URL
    public let tag: String         // Base64URL

    public struct Header: Codable {
        /// Protocol version
        public let v: Int
        /// Encryption algorithm (always A256GCM)
        public let alg: String
        /// Device ID
        public let did: String
        /// Timestamp in milliseconds
        public let ts: Int64
        /// Sequence number
        public let seq: Int64
        /// Nonce (Base64URL encoded)
        public let nonce: String
        /// Direction: c2s (client to server) or s2c (server to client)
        public let dir: String

        public init(
            v: Int = 1,
            alg: String = "A256GCM",
            did: String,
            ts: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
            seq: Int64,
            nonce: String,
            dir: String
        ) {
            self.v = v
            self.alg = alg
            self.did = did
            self.ts = ts
            self.seq = seq
            self.nonce = nonce
            self.dir = dir
        }
    }

    public init(header: Header, ciphertext: String, tag: String) {
        self.header = header
        self.ciphertext = ciphertext
        self.tag = tag
    }

    /// Serialize to compact format: header.ciphertext.tag
    public func toCompact() throws -> String {
        let encoder = JSONEncoder()
        let headerData = try encoder.encode(header)
        let headerB64 = headerData.base64URLEncodedString()
        return "\(headerB64).\(ciphertext).\(tag)"
    }

    /// Parse from compact format
    public static func fromCompact(_ compact: String) throws -> SecureMessage {
        let parts = compact.split(separator: ".")
        guard parts.count == 3 else {
            throw SecureMessageError.invalidFormat
        }

        guard let headerData = Data(base64URLEncoded: String(parts[0])) else {
            throw SecureMessageError.invalidHeader
        }

        let decoder = JSONDecoder()
        let header = try decoder.decode(Header.self, from: headerData)

        return SecureMessage(
            header: header,
            ciphertext: String(parts[1]),
            tag: String(parts[2])
        )
    }

    /// Get the header as bytes for AAD
    public func getHeaderBytes() throws -> Data {
        let encoder = JSONEncoder()
        return try encoder.encode(header)
    }

    /// Get ciphertext as bytes
    public func getCiphertextBytes() throws -> Data {
        guard let data = Data(base64URLEncoded: ciphertext) else {
            throw SecureMessageError.invalidCiphertext
        }
        return data
    }

    /// Get tag as bytes
    public func getTagBytes() throws -> Data {
        guard let data = Data(base64URLEncoded: tag) else {
            throw SecureMessageError.invalidTag
        }
        return data
    }

    /// Get nonce as bytes
    public func getNonceBytes() throws -> Data {
        guard let data = Data(base64URLEncoded: header.nonce) else {
            throw SecureMessageError.invalidNonce
        }
        return data
    }
}

public enum SecureMessageError: Error, LocalizedError {
    case invalidFormat
    case invalidHeader
    case invalidCiphertext
    case invalidTag
    case invalidNonce
    case encryptionFailed
    case decryptionFailed
    case replayDetected
    case messageExpired
    case unknownDevice

    public var errorDescription: String? {
        switch self {
        case .invalidFormat: return "Invalid secure message format"
        case .invalidHeader: return "Invalid message header"
        case .invalidCiphertext: return "Invalid ciphertext"
        case .invalidTag: return "Invalid authentication tag"
        case .invalidNonce: return "Invalid nonce"
        case .encryptionFailed: return "Encryption failed"
        case .decryptionFailed: return "Decryption failed"
        case .replayDetected: return "Replay attack detected"
        case .messageExpired: return "Message expired"
        case .unknownDevice: return "Unknown device"
        }
    }
}

// MARK: - Base64URL Extensions

extension Data {
    /// Encode to Base64URL (no padding)
    public func base64URLEncodedString() -> String {
        base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    /// Decode from Base64URL
    public init?(base64URLEncoded string: String) {
        var base64 = string
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")

        // Add padding if needed
        let remainder = base64.count % 4
        if remainder > 0 {
            base64 += String(repeating: "=", count: 4 - remainder)
        }

        self.init(base64Encoded: base64)
    }
}
