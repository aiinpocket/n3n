import Foundation
import CryptoKit
import Logging

/// Service for handling device pairing with the platform.
public final class PairingService {

    public static let shared = PairingService()

    private let crypto = AgentCrypto.shared
    private let storage = KeychainStorage.shared
    private let logger = Logger(label: "n3n.agent.pairing")

    private init() {}

    // MARK: - Pairing

    /// Complete pairing with the platform using a pairing code.
    public func pair(
        platformUrl: String,
        pairingCode: String,
        deviceName: String
    ) async throws -> DeviceKeys {
        logger.info("Starting pairing process with code: \(pairingCode)")

        // 1. Generate device key pair
        let keyPair = crypto.generateKeyPair()
        let deviceId = UUID().uuidString

        // 2. Generate device fingerprint
        let fingerprint = crypto.generateDeviceFingerprint()

        // 3. Build pairing request
        let request = PairingRequest(
            pairingCode: pairingCode,
            deviceId: deviceId,
            deviceName: deviceName,
            platform: "macos",
            devicePublicKey: keyPair.publicKeyBase64,
            deviceFingerprint: fingerprint,
            externalAddress: nil,
            directConnectionEnabled: false,
            allowedIps: nil
        )

        // 4. Send pairing request to platform
        let pairUrl = platformUrl
            .replacingOccurrences(of: "wss://", with: "https://")
            .replacingOccurrences(of: "ws://", with: "http://")
            .replacingOccurrences(of: "/gateway/agent/secure", with: "")
            .replacingOccurrences(of: "/gateway/agent", with: "")
            + "/api/agent/pair/complete"

        logger.debug("Sending pairing request to: \(pairUrl)")

        var urlRequest = URLRequest(url: URL(string: pairUrl)!)
        urlRequest.httpMethod = "POST"
        urlRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let encoder = JSONEncoder()
        urlRequest.httpBody = try encoder.encode(request)

        let (data, response) = try await URLSession.shared.data(for: urlRequest)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw PairingError.networkError("Invalid response")
        }

        guard httpResponse.statusCode == 200 else {
            let errorBody = String(data: data, encoding: .utf8) ?? "Unknown error"
            logger.error("Pairing failed: \(errorBody)")
            throw PairingError.pairingFailed(errorBody)
        }

        // 5. Parse response
        let decoder = JSONDecoder()
        let pairingResponse = try decoder.decode(PairingResponse.self, from: data)

        guard pairingResponse.success else {
            throw PairingError.pairingFailed(pairingResponse.error ?? "Unknown error")
        }

        guard let platformPublicKeyBase64 = pairingResponse.platformPublicKey,
              let platformFingerprint = pairingResponse.platformFingerprint,
              let deviceToken = pairingResponse.deviceToken else {
            throw PairingError.invalidResponse
        }

        // 6. Parse platform public key
        let platformPublicKey = try crypto.parsePublicKey(base64: platformPublicKeyBase64)

        // 7. Derive shared secret
        let sharedSecret = try crypto.deriveSharedSecret(
            privateKey: keyPair.privateKey,
            peerPublicKey: platformPublicKey
        )

        // 8. Derive encryption keys
        let salt = (deviceId + "platform").data(using: .utf8)!
        let info = "n3n-agent-v1".data(using: .utf8)!
        let derivedKeys = crypto.deriveKeys(
            sharedSecret: sharedSecret,
            salt: salt,
            info: info
        )

        // 9. Store device keys
        let deviceKeys = DeviceKeys(
            deviceId: deviceId,
            deviceToken: deviceToken,
            platformPublicKey: platformPublicKeyBase64,
            platformFingerprint: platformFingerprint,
            encryptKeyC2S: derivedKeys.encryptKeyClientToServer.withUnsafeBytes { Data($0) },
            encryptKeyS2C: derivedKeys.encryptKeyServerToClient.withUnsafeBytes { Data($0) },
            authKey: derivedKeys.authKey.withUnsafeBytes { Data($0) },
            lastSequence: crypto.generateInitialSequence()
        )

        try storage.storeDeviceKeys(deviceKeys)

        // Store private key separately (more secure)
        try storage.storePrivateKey(keyPair.privateKeyData, deviceId: deviceId)

        logger.info("Pairing successful! Device ID: \(deviceId)")

        return deviceKeys
    }

    // MARK: - Token-based Registration

    /// Register with the platform using a one-time token from config file.
    public func registerWithToken(
        config: RegistrationConfig,
        deviceName: String
    ) async throws -> DeviceKeys {
        logger.info("Starting token-based registration")

        // 1. Generate device key pair
        let keyPair = crypto.generateKeyPair()
        let deviceId = UUID().uuidString

        // 2. Generate device fingerprint
        let fingerprint = crypto.generateDeviceFingerprint()

        // 3. Build registration request
        let request = TokenRegistrationRequest(
            token: config.registration.token,
            deviceId: deviceId,
            deviceName: deviceName,
            platform: "macos",
            devicePublicKey: keyPair.publicKeyBase64,
            deviceFingerprint: fingerprint
        )

        // 4. Build registration URL
        let registerUrl = config.gateway.url
            .replacingOccurrences(of: "wss://", with: "https://")
            .replacingOccurrences(of: "ws://", with: "http://")
            .replacingOccurrences(of: "/gateway/agent/secure", with: "")
            .replacingOccurrences(of: "/gateway/agent", with: "")
            + "/api/agents/register"

        logger.debug("Sending registration request to: \(registerUrl)")

        var urlRequest = URLRequest(url: URL(string: registerUrl)!)
        urlRequest.httpMethod = "POST"
        urlRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let encoder = JSONEncoder()
        urlRequest.httpBody = try encoder.encode(request)

        let (data, response) = try await URLSession.shared.data(for: urlRequest)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw PairingError.networkError("Invalid response")
        }

        guard httpResponse.statusCode == 200 else {
            let errorBody = String(data: data, encoding: .utf8) ?? "Unknown error"
            logger.error("Registration failed: \(errorBody)")
            throw PairingError.pairingFailed(errorBody)
        }

        // 5. Parse response
        let decoder = JSONDecoder()
        let registrationResponse = try decoder.decode(TokenRegistrationResponse.self, from: data)

        guard registrationResponse.success else {
            throw PairingError.pairingFailed(registrationResponse.error ?? "Unknown error")
        }

        guard let platformPublicKeyBase64 = registrationResponse.platformPublicKey,
              let platformFingerprint = registrationResponse.platformFingerprint,
              let deviceToken = registrationResponse.deviceToken else {
            throw PairingError.invalidResponse
        }

        // 6. Parse platform public key
        let platformPublicKey = try crypto.parsePublicKey(base64: platformPublicKeyBase64)

        // 7. Derive shared secret
        let sharedSecret = try crypto.deriveSharedSecret(
            privateKey: keyPair.privateKey,
            peerPublicKey: platformPublicKey
        )

        // 8. Derive encryption keys
        let salt = (deviceId + "platform").data(using: .utf8)!
        let info = "n3n-agent-v1".data(using: .utf8)!
        let derivedKeys = crypto.deriveKeys(
            sharedSecret: sharedSecret,
            salt: salt,
            info: info
        )

        // 9. Store device keys
        let deviceKeys = DeviceKeys(
            deviceId: deviceId,
            deviceToken: deviceToken,
            platformPublicKey: platformPublicKeyBase64,
            platformFingerprint: platformFingerprint,
            encryptKeyC2S: derivedKeys.encryptKeyClientToServer.withUnsafeBytes { Data($0) },
            encryptKeyS2C: derivedKeys.encryptKeyServerToClient.withUnsafeBytes { Data($0) },
            authKey: derivedKeys.authKey.withUnsafeBytes { Data($0) },
            lastSequence: crypto.generateInitialSequence()
        )

        try storage.storeDeviceKeys(deviceKeys)

        // Store private key separately (more secure)
        try storage.storePrivateKey(keyPair.privateKeyData, deviceId: deviceId)

        // Store the gateway URL for future connections
        var agentConfig = AgentConfig()
        agentConfig.platformUrl = config.gateway.url
        agentConfig.deviceName = deviceName
        try storage.storeConfig(agentConfig)

        logger.info("Registration successful! Device ID: \(deviceId)")

        return deviceKeys
    }

    /// Check if device is already paired.
    public func isPaired() -> Bool {
        do {
            return try storage.loadDeviceKeys() != nil
        } catch {
            return false
        }
    }

    /// Get stored device keys.
    public func getDeviceKeys() throws -> DeviceKeys? {
        return try storage.loadDeviceKeys()
    }

    /// Unpair the device.
    public func unpair() throws {
        logger.info("Unpairing device")
        try storage.deleteDeviceKeys()
        try storage.clearAll()
    }
}

// MARK: - Request/Response Types

struct PairingRequest: Codable {
    let pairingCode: String
    let deviceId: String
    let deviceName: String
    let platform: String
    let devicePublicKey: String
    let deviceFingerprint: String
    let externalAddress: String?
    let directConnectionEnabled: Bool
    let allowedIps: [String]?
}

struct PairingResponse: Codable {
    let success: Bool
    let platformPublicKey: String?
    let platformFingerprint: String?
    let deviceToken: String?
    let error: String?
}

struct TokenRegistrationRequest: Codable {
    let token: String
    let deviceId: String
    let deviceName: String
    let platform: String
    let devicePublicKey: String
    let deviceFingerprint: String
}

struct TokenRegistrationResponse: Codable {
    let success: Bool
    let platformPublicKey: String?
    let platformFingerprint: String?
    let deviceToken: String?
    let error: String?
}

// MARK: - Errors

public enum PairingError: Error, LocalizedError {
    case networkError(String)
    case pairingFailed(String)
    case invalidResponse
    case alreadyPaired

    public var errorDescription: String? {
        switch self {
        case .networkError(let message):
            return "Network error: \(message)"
        case .pairingFailed(let message):
            return "Pairing failed: \(message)"
        case .invalidResponse:
            return "Invalid response from platform"
        case .alreadyPaired:
            return "Device is already paired"
        }
    }
}
