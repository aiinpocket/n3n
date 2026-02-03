import Foundation
import Logging

/// Main N3N Agent class that manages the connection and capabilities.
public final class Agent: AgentConnectionDelegate {

    public static let shared = Agent()

    private let logger = Logger(label: "n3n.agent")
    private let connection = AgentConnection()
    private let pairing = PairingService.shared
    private let storage = KeychainStorage.shared
    private let capabilities = CapabilityRegistry.shared

    public var isConnected: Bool {
        connection.state == .connected
    }

    public var isPaired: Bool {
        pairing.isPaired()
    }

    private init() {
        connection.delegate = self
    }

    // MARK: - Lifecycle

    /// Start the agent.
    public func start() async throws {
        logger.info("Starting N3N Agent")

        // Load configuration
        guard let config = try storage.loadConfig() else {
            throw AgentError.notConfigured
        }

        // Load device keys
        guard let deviceKeys = try pairing.getDeviceKeys() else {
            throw AgentError.notPaired
        }

        // Connect
        try await connection.connect(config: config, deviceKeys: deviceKeys)

        logger.info("Agent started successfully")
    }

    /// Stop the agent.
    public func stop() async {
        logger.info("Stopping N3N Agent")
        await connection.disconnect()
    }

    // MARK: - Pairing

    /// Pair with the platform using a pairing code.
    public func pair(platformUrl: String, pairingCode: String, deviceName: String) async throws {
        logger.info("Pairing with platform: \(platformUrl)")

        // Store configuration
        let config = AgentConfig(
            platformUrl: platformUrl.replacingOccurrences(of: "http", with: "ws") + "/gateway/agent/secure",
            deviceName: deviceName
        )
        try storage.storeConfig(config)

        // Complete pairing
        _ = try await pairing.pair(
            platformUrl: platformUrl,
            pairingCode: pairingCode,
            deviceName: deviceName
        )

        logger.info("Pairing successful!")
    }

    /// Unpair from the platform.
    public func unpair() async throws {
        logger.info("Unpairing from platform")
        await stop()
        try pairing.unpair()
    }

    /// Pair with the platform using a token (for one-click setup).
    public func pairWithToken(platformUrl: String, token: String, deviceName: String) async throws {
        logger.info("Pairing with platform using token: \(platformUrl)")

        // Parse URL to get domain and port
        guard let url = URL(string: platformUrl) else {
            throw AgentError.invalidUrl
        }

        let scheme = url.scheme ?? "http"
        let host = url.host ?? "localhost"
        let port = url.port ?? (scheme == "https" ? 443 : 8080)

        // Build gateway URL
        let wsScheme = scheme == "https" ? "wss" : "ws"
        let gatewayUrl = "\(wsScheme)://\(host):\(port)/gateway/agent/secure"

        let config = RegistrationConfig(
            version: 1,
            gateway: RegistrationConfig.GatewayInfo(
                url: gatewayUrl,
                domain: host,
                port: port
            ),
            registration: RegistrationConfig.RegistrationInfo(
                token: token,
                agentId: UUID().uuidString
            )
        )

        // Complete registration
        _ = try await pairing.registerWithToken(config: config, deviceName: deviceName)

        logger.info("Token pairing successful!")
    }

    // MARK: - AgentConnectionDelegate

    public func connectionStateChanged(_ state: AgentConnection.State) {
        logger.info("Connection state: \(state)")
    }

    public func getCapabilities() -> [String] {
        return capabilities.getAllIds()
    }

    public func handleInvoke(invokeId: String, capability: String, args: [String: Any]) {
        logger.info("Invoke: \(capability)")

        Task {
            do {
                guard let cap = capabilities.get(capability) else {
                    throw CapabilityError.notFound(capability)
                }

                let result = try await cap.execute(args: args)
                connection.sendInvokeResult(invokeId: invokeId, result: result, error: nil)

            } catch {
                logger.error("Invoke failed: \(error)")
                connection.sendInvokeResult(invokeId: invokeId, result: nil, error: error.localizedDescription)
            }
        }
    }

    public func handleEvent(_ event: GatewayEvent) {
        logger.debug("Event: \(event.event)")
    }

    // MARK: - Configuration

    /// Get current configuration.
    public func getConfig() throws -> AgentConfig? {
        return try storage.loadConfig()
    }

    /// Update configuration.
    public func updateConfig(_ config: AgentConfig) throws {
        try storage.storeConfig(config)
    }
}

// MARK: - Errors

public enum AgentError: Error, LocalizedError {
    case notConfigured
    case notPaired
    case alreadyRunning
    case invalidUrl

    public var errorDescription: String? {
        switch self {
        case .notConfigured:
            return "Agent is not configured. Please pair with a platform first."
        case .notPaired:
            return "Agent is not paired with any platform."
        case .alreadyRunning:
            return "Agent is already running."
        case .invalidUrl:
            return "Invalid platform URL."
        }
    }
}
