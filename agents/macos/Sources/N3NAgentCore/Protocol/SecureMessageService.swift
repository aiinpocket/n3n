import Foundation
import CryptoKit
import Logging

/// Service for encrypting and decrypting agent messages.
public final class SecureMessageService {

    private let crypto = AgentCrypto.shared
    private let storage = KeychainStorage.shared
    private let logger = Logger(label: "n3n.agent.secure-message")

    /// Maximum time drift allowed (5 minutes in milliseconds)
    private let maxTimeDriftMs: Int64 = 5 * 60 * 1000

    /// Current sequence counter for outgoing messages
    private var outgoingSequence: Int64

    public init() {
        self.outgoingSequence = Int64(Date().timeIntervalSince1970 * 1000)
    }

    // MARK: - Encryption

    /// Encrypt a message to send to the server.
    public func encrypt<T: Encodable>(_ payload: T, deviceKeys: DeviceKeys) throws -> String {
        // Serialize payload
        let encoder = JSONEncoder()
        let plaintext = try encoder.encode(payload)

        // Get encryption key (client to server)
        let encryptKey = SymmetricKey(data: deviceKeys.encryptKeyC2S)

        // Build initial header (nonce will be set after encryption)
        let nonce = AES.GCM.Nonce()
        let header = SecureMessage.Header(
            did: deviceKeys.deviceId,
            seq: nextSequence(),
            nonce: Data(nonce).base64URLEncodedString(),
            dir: "c2s"
        )

        // Get header bytes for AAD
        let headerBytes = try encoder.encode(header)

        // Encrypt with AAD
        let sealedBox = try AES.GCM.seal(plaintext, using: encryptKey, nonce: nonce, authenticating: headerBytes)

        // Build secure message
        let message = SecureMessage(
            header: header,
            ciphertext: sealedBox.ciphertext.base64URLEncodedString(),
            tag: sealedBox.tag.base64URLEncodedString()
        )

        return try message.toCompact()
    }

    // MARK: - Decryption

    /// Decrypt and verify a message from the server.
    public func decrypt<T: Decodable>(
        _ encryptedMessage: String,
        as type: T.Type,
        deviceKeys: DeviceKeys
    ) throws -> DecryptedMessage<T> {
        // Parse message
        let message = try SecureMessage.fromCompact(encryptedMessage)
        let header = message.header

        // 1. Validate protocol version
        guard header.v == 1 else {
            throw SecureMessageError.invalidFormat
        }

        // 2. Validate device ID
        guard header.did == deviceKeys.deviceId else {
            throw SecureMessageError.unknownDevice
        }

        // 3. Validate timestamp
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let timeDrift = abs(now - header.ts)
        guard timeDrift <= maxTimeDriftMs else {
            logger.warning("Message expired: drift=\(timeDrift)ms")
            throw SecureMessageError.messageExpired
        }

        // 4. Validate sequence (for replay protection)
        guard header.seq > deviceKeys.lastSequence else {
            logger.warning("Replay detected: seq=\(header.seq), last=\(deviceKeys.lastSequence)")
            throw SecureMessageError.replayDetected
        }

        // 5. Get decryption key (server to client)
        let decryptKey = SymmetricKey(data: deviceKeys.encryptKeyS2C)

        // 6. Get AAD (header bytes)
        let encoder = JSONEncoder()
        let headerBytes = try encoder.encode(header)

        // 7. Decrypt
        let nonce = try AES.GCM.Nonce(data: message.getNonceBytes())
        let sealedBox = try AES.GCM.SealedBox(
            nonce: nonce,
            ciphertext: message.getCiphertextBytes(),
            tag: message.getTagBytes()
        )

        let plaintext = try AES.GCM.open(sealedBox, using: decryptKey, authenticating: headerBytes)

        // 8. Parse payload
        let decoder = JSONDecoder()
        let payload = try decoder.decode(T.self, from: plaintext)

        logger.debug("Successfully decrypted message from server")

        return DecryptedMessage(
            deviceId: header.did,
            timestamp: header.ts,
            sequence: header.seq,
            payload: payload
        )
    }

    /// Verify message without decrypting (for initial validation)
    public func verify(_ encryptedMessage: String, deviceKeys: DeviceKeys) -> VerificationResult {
        do {
            let message = try SecureMessage.fromCompact(encryptedMessage)
            let header = message.header

            // Basic validation
            guard header.v == 1 else {
                return .invalid("Unsupported protocol version")
            }

            // Check device ID
            guard header.did == deviceKeys.deviceId else {
                return .invalid("Device ID mismatch")
            }

            // Check timestamp
            let now = Int64(Date().timeIntervalSince1970 * 1000)
            let timeDrift = abs(now - header.ts)
            guard timeDrift <= maxTimeDriftMs else {
                return .invalid("Message expired")
            }

            // Check sequence
            guard header.seq > deviceKeys.lastSequence else {
                return .invalid("Replay detected")
            }

            return .valid(deviceId: header.did, sequence: header.seq)

        } catch {
            return .invalid("Parse error: \(error.localizedDescription)")
        }
    }

    // MARK: - Helpers

    private func nextSequence() -> Int64 {
        outgoingSequence += 1
        return outgoingSequence
    }
}

// MARK: - Result Types

public struct DecryptedMessage<T> {
    public let deviceId: String
    public let timestamp: Int64
    public let sequence: Int64
    public let payload: T
}

public enum VerificationResult {
    case valid(deviceId: String, sequence: Int64)
    case invalid(String)

    public var isValid: Bool {
        if case .valid = self { return true }
        return false
    }
}
