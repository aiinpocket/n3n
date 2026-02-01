import Foundation
import Security
import CryptoKit

/// Secure storage using macOS Keychain.
public final class KeychainStorage {

    public static let shared = KeychainStorage()

    private let service = "com.aiinpocket.n3n.agent"

    private init() {}

    // MARK: - Device Keys

    /// Store device keys after successful pairing.
    public func storeDeviceKeys(_ keys: DeviceKeys) throws {
        let encoder = JSONEncoder()
        let data = try encoder.encode(keys)

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: "device-keys",
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock
        ]

        // Delete existing if any
        SecItemDelete(query as CFDictionary)

        let status = SecItemAdd(query as CFDictionary, nil)
        guard status == errSecSuccess else {
            throw KeychainError.saveFailed(status)
        }
    }

    /// Load device keys.
    public func loadDeviceKeys() throws -> DeviceKeys? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: "device-keys",
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)

        guard status == errSecSuccess, let data = result as? Data else {
            if status == errSecItemNotFound {
                return nil
            }
            throw KeychainError.loadFailed(status)
        }

        let decoder = JSONDecoder()
        return try decoder.decode(DeviceKeys.self, from: data)
    }

    /// Delete device keys (unpair).
    public func deleteDeviceKeys() throws {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: "device-keys"
        ]

        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw KeychainError.deleteFailed(status)
        }
    }

    // MARK: - Private Key

    /// Store the device's private key securely.
    public func storePrivateKey(_ privateKey: Data, deviceId: String) throws {
        let query: [String: Any] = [
            kSecClass as String: kSecClassKey,
            kSecAttrKeyType as String: kSecAttrKeyTypeECSECPrimeRandom,
            kSecAttrApplicationTag as String: "com.aiinpocket.n3n.agent.key.\(deviceId)".data(using: .utf8)!,
            kSecValueData as String: privateKey,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock
        ]

        // Delete existing if any
        SecItemDelete(query as CFDictionary)

        let status = SecItemAdd(query as CFDictionary, nil)
        guard status == errSecSuccess else {
            throw KeychainError.saveFailed(status)
        }
    }

    /// Load the device's private key.
    public func loadPrivateKey(deviceId: String) throws -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassKey,
            kSecAttrKeyType as String: kSecAttrKeyTypeECSECPrimeRandom,
            kSecAttrApplicationTag as String: "com.aiinpocket.n3n.agent.key.\(deviceId)".data(using: .utf8)!,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)

        guard status == errSecSuccess, let data = result as? Data else {
            if status == errSecItemNotFound {
                return nil
            }
            throw KeychainError.loadFailed(status)
        }

        return data
    }

    // MARK: - Configuration

    /// Store agent configuration.
    public func storeConfig(_ config: AgentConfig) throws {
        let encoder = JSONEncoder()
        let data = try encoder.encode(config)

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: "agent-config",
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock
        ]

        // Delete existing if any
        SecItemDelete(query as CFDictionary)

        let status = SecItemAdd(query as CFDictionary, nil)
        guard status == errSecSuccess else {
            throw KeychainError.saveFailed(status)
        }
    }

    /// Load agent configuration.
    public func loadConfig() throws -> AgentConfig? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: "agent-config",
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)

        guard status == errSecSuccess, let data = result as? Data else {
            if status == errSecItemNotFound {
                return nil
            }
            throw KeychainError.loadFailed(status)
        }

        let decoder = JSONDecoder()
        return try decoder.decode(AgentConfig.self, from: data)
    }

    /// Clear all stored data (full reset).
    public func clearAll() throws {
        let queries: [[String: Any]] = [
            [
                kSecClass as String: kSecClassGenericPassword,
                kSecAttrService as String: service
            ],
            [
                kSecClass as String: kSecClassKey,
                kSecAttrApplicationTag as String: "com.aiinpocket.n3n.agent".data(using: .utf8)!
            ]
        ]

        for query in queries {
            SecItemDelete(query as CFDictionary)
        }
    }
}

// MARK: - Data Types

public struct DeviceKeys: Codable {
    public let deviceId: String
    public let deviceToken: String
    public let platformPublicKey: String
    public let platformFingerprint: String
    public let encryptKeyC2S: Data  // Client to Server
    public let encryptKeyS2C: Data  // Server to Client
    public let authKey: Data
    public let pairedAt: Date
    public var lastSequence: Int64

    public init(
        deviceId: String,
        deviceToken: String,
        platformPublicKey: String,
        platformFingerprint: String,
        encryptKeyC2S: Data,
        encryptKeyS2C: Data,
        authKey: Data,
        pairedAt: Date = Date(),
        lastSequence: Int64 = 0
    ) {
        self.deviceId = deviceId
        self.deviceToken = deviceToken
        self.platformPublicKey = platformPublicKey
        self.platformFingerprint = platformFingerprint
        self.encryptKeyC2S = encryptKeyC2S
        self.encryptKeyS2C = encryptKeyS2C
        self.authKey = authKey
        self.pairedAt = pairedAt
        self.lastSequence = lastSequence
    }
}

public struct AgentConfig: Codable {
    public var platformUrl: String
    public var deviceName: String
    public var enableDirectConnection: Bool
    public var listenPort: Int
    public var externalAddress: String?
    public var autoStart: Bool
    public var logLevel: String

    public init(
        platformUrl: String = "wss://localhost:8080/ws/agent/secure",
        deviceName: String = Host.current().localizedName ?? "My Mac",
        enableDirectConnection: Bool = false,
        listenPort: Int = 9999,
        externalAddress: String? = nil,
        autoStart: Bool = true,
        logLevel: String = "info"
    ) {
        self.platformUrl = platformUrl
        self.deviceName = deviceName
        self.enableDirectConnection = enableDirectConnection
        self.listenPort = listenPort
        self.externalAddress = externalAddress
        self.autoStart = autoStart
        self.logLevel = logLevel
    }
}

public enum KeychainError: Error, LocalizedError {
    case saveFailed(OSStatus)
    case loadFailed(OSStatus)
    case deleteFailed(OSStatus)

    public var errorDescription: String? {
        switch self {
        case .saveFailed(let status):
            return "Failed to save to Keychain: \(status)"
        case .loadFailed(let status):
            return "Failed to load from Keychain: \(status)"
        case .deleteFailed(let status):
            return "Failed to delete from Keychain: \(status)"
        }
    }
}

// MARK: - Registration Config File

/// Config file structure for token-based registration
public struct RegistrationConfig: Codable {
    public let version: Int
    public let gateway: GatewayInfo
    public let registration: RegistrationInfo

    public struct GatewayInfo: Codable {
        public let url: String
        public let domain: String
        public let port: Int
    }

    public struct RegistrationInfo: Codable {
        public let token: String
        public let agentId: String
    }

    /// Load config from a JSON file
    public static func load(from url: URL) throws -> RegistrationConfig {
        let data = try Data(contentsOf: url)
        let decoder = JSONDecoder()
        return try decoder.decode(RegistrationConfig.self, from: data)
    }

    /// Find config file in common locations
    public static func findConfigFile() -> URL? {
        let fileManager = FileManager.default

        // Check locations in order:
        // 1. Same directory as executable
        // 2. Current working directory
        // 3. Home directory
        // 4. Application Support directory

        let locations: [URL] = [
            Bundle.main.bundleURL.appendingPathComponent("n3n-agent-config.json"),
            URL(fileURLWithPath: fileManager.currentDirectoryPath)
                .appendingPathComponent("n3n-agent-config.json"),
            fileManager.homeDirectoryForCurrentUser
                .appendingPathComponent("n3n-agent-config.json"),
            fileManager.homeDirectoryForCurrentUser
                .appendingPathComponent(".n3n/config.json"),
            fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask).first?
                .appendingPathComponent("N3N Agent/config.json")
        ].compactMap { $0 }

        for location in locations {
            if fileManager.fileExists(atPath: location.path) {
                return location
            }
        }

        return nil
    }
}
