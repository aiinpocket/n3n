import Foundation
import WebSocketKit
import NIOCore
import NIOPosix
import Logging

/// Manages the WebSocket connection to the N3N platform.
public final class AgentConnection {

    public enum State {
        case disconnected
        case connecting
        case handshaking
        case connected
        case reconnecting
    }

    public weak var delegate: AgentConnectionDelegate?

    private let logger = Logger(label: "n3n.agent.connection")
    private let secureMessage = SecureMessageService()
    private let storage = KeychainStorage.shared

    private var webSocket: WebSocket?
    private var eventLoopGroup: EventLoopGroup?
    private var deviceKeys: DeviceKeys?
    private var config: AgentConfig?

    private(set) public var state: State = .disconnected
    private var reconnectAttempts = 0
    private let maxReconnectAttempts = 10
    private let baseReconnectDelay: TimeInterval = 1.0

    /// Pending requests waiting for responses
    private var pendingRequests: [String: (Result<GatewayResponse, Error>) -> Void] = [:]

    public init() {}

    // MARK: - Connection

    /// Connect to the platform.
    public func connect(config: AgentConfig, deviceKeys: DeviceKeys) async throws {
        guard state == .disconnected || state == .reconnecting else {
            logger.warning("Already connected or connecting")
            return
        }

        self.config = config
        self.deviceKeys = deviceKeys

        state = .connecting
        delegate?.connectionStateChanged(state)

        logger.info("Connecting to: \(config.platformUrl)")

        // Create event loop group
        eventLoopGroup = MultiThreadedEventLoopGroup(numberOfThreads: 1)

        do {
            try await connectWebSocket()
        } catch {
            state = .disconnected
            delegate?.connectionStateChanged(state)
            throw error
        }
    }

    private func connectWebSocket() async throws {
        guard let eventLoopGroup = eventLoopGroup,
              let config = config else {
            throw ConnectionError.notConfigured
        }

        let promise = eventLoopGroup.next().makePromise(of: Void.self)

        WebSocket.connect(
            to: config.platformUrl,
            on: eventLoopGroup
        ) { [weak self] ws in
            self?.handleWebSocketConnected(ws)
        }.whenComplete { [weak self] result in
            switch result {
            case .success:
                promise.succeed(())
            case .failure(let error):
                self?.logger.error("WebSocket connection failed: \(error)")
                promise.fail(error)
            }
        }

        try await promise.futureResult.get()
    }

    private func handleWebSocketConnected(_ ws: WebSocket) {
        self.webSocket = ws
        self.reconnectAttempts = 0

        logger.info("WebSocket connected, waiting for handshake challenge")

        // Set up message handler
        ws.onText { [weak self] ws, text in
            self?.handleMessage(text)
        }

        // Set up close handler
        ws.onClose.whenComplete { [weak self] _ in
            self?.handleDisconnected()
        }
    }

    /// Disconnect from the platform.
    public func disconnect() async {
        logger.info("Disconnecting")

        state = .disconnected
        delegate?.connectionStateChanged(state)

        try? await webSocket?.close()
        webSocket = nil

        try? await eventLoopGroup?.shutdownGracefully()
        eventLoopGroup = nil
    }

    // MARK: - Message Handling

    private func handleMessage(_ text: String) {
        // Check if we're in handshake phase
        if state == .connecting || state == .handshaking {
            handleHandshakeMessage(text)
            return
        }

        // Encrypted message
        guard let deviceKeys = deviceKeys else {
            logger.error("No device keys available")
            return
        }

        do {
            // Verify message
            let verification = secureMessage.verify(text, deviceKeys: deviceKeys)
            guard verification.isValid else {
                if case .invalid(let error) = verification {
                    logger.error("Message verification failed: \(error)")
                }
                return
            }

            // Decrypt
            let decrypted = try secureMessage.decrypt(
                text,
                as: GatewayMessage.self,
                deviceKeys: deviceKeys
            )

            // Update sequence
            var updatedKeys = deviceKeys
            updatedKeys.lastSequence = decrypted.sequence
            try? storage.storeDeviceKeys(updatedKeys)
            self.deviceKeys = updatedKeys

            // Process based on type
            processMessage(decrypted.payload, rawText: text)

        } catch {
            logger.error("Failed to decrypt message: \(error)")
        }
    }

    private func handleHandshakeMessage(_ text: String) {
        do {
            let decoder = JSONDecoder()

            // Try to parse as event (challenge)
            if let event = try? decoder.decode(GatewayEvent.self, from: Data(text.utf8)),
               event.event == GatewayEvent.handshakeChallenge {
                state = .handshaking
                delegate?.connectionStateChanged(state)
                sendHandshakeAuth()
                return
            }

            // Try to parse as response
            if let response = try? decoder.decode(GatewayResponse.self, from: Data(text.utf8)) {
                if response.success {
                    state = .connected
                    delegate?.connectionStateChanged(state)
                    logger.info("Handshake complete, connection established")

                    // Register capabilities
                    registerCapabilities()
                } else {
                    logger.error("Handshake failed: \(response.error?.message ?? "Unknown error")")
                    Task {
                        await disconnect()
                    }
                }
            }

        } catch {
            logger.error("Failed to parse handshake message: \(error)")
        }
    }

    private func sendHandshakeAuth() {
        guard let deviceKeys = deviceKeys else {
            logger.error("No device keys for handshake")
            return
        }

        let capabilities = delegate?.getCapabilities() ?? []

        let request = GatewayRequest.handshakeAuth(
            deviceId: deviceKeys.deviceId,
            deviceToken: deviceKeys.deviceToken,
            capabilities: capabilities
        )

        sendPlainMessage(request)
    }

    private func registerCapabilities() {
        guard let deviceKeys = deviceKeys else { return }

        let capabilities = delegate?.getCapabilities() ?? []
        let request = GatewayRequest.nodeRegister(capabilities: capabilities)

        do {
            let encrypted = try secureMessage.encrypt(request, deviceKeys: deviceKeys)
            sendRaw(encrypted)
        } catch {
            logger.error("Failed to encrypt register request: \(error)")
        }
    }

    private func processMessage(_ message: GatewayMessage, rawText: String) {
        switch message.type {
        case GatewayMessageType.request.rawValue:
            if let request = try? JSONDecoder().decode(GatewayRequest.self, from: Data(rawText.utf8)) {
                handleRequest(request)
            }

        case GatewayMessageType.response.rawValue:
            if let response = try? JSONDecoder().decode(GatewayResponse.self, from: Data(rawText.utf8)),
               let id = response.id as String?,
               let handler = pendingRequests.removeValue(forKey: id) {
                handler(.success(response))
            }

        case GatewayMessageType.event.rawValue:
            if let event = try? JSONDecoder().decode(GatewayEvent.self, from: Data(rawText.utf8)) {
                handleEvent(event)
            }

        default:
            logger.warning("Unknown message type: \(message.type)")
        }
    }

    private func handleRequest(_ request: GatewayRequest) {
        logger.debug("Received request: \(request.method)")

        switch request.method {
        case "node.invoke":
            if let params = request.params,
               let capability = params["capability"]?.asString(),
               let invokeId = params["invokeId"]?.asString() {
                let args = params["args"]?.asDictionary() ?? [:]
                delegate?.handleInvoke(invokeId: invokeId, capability: capability, args: args)
            }

        case "ping":
            // Respond to ping
            sendEncrypted(GatewayResponse.success(id: request.id, result: [
                "pong": AnyCodable(Int64(Date().timeIntervalSince1970 * 1000))
            ]))

        default:
            logger.warning("Unknown request method: \(request.method)")
        }
    }

    private func handleEvent(_ event: GatewayEvent) {
        logger.debug("Received event: \(event.event)")
        delegate?.handleEvent(event)
    }

    // MARK: - Sending

    /// Send an invoke result back to the server.
    public func sendInvokeResult(invokeId: String, result: Any?, error: String?) {
        let request = GatewayRequest.invokeResult(invokeId: invokeId, result: result, error: error)
        sendEncrypted(request)
    }

    private func sendEncrypted<T: Encodable>(_ message: T) {
        guard let deviceKeys = deviceKeys else {
            logger.error("No device keys for encryption")
            return
        }

        do {
            let encrypted = try secureMessage.encrypt(message, deviceKeys: deviceKeys)
            sendRaw(encrypted)
        } catch {
            logger.error("Failed to encrypt message: \(error)")
        }
    }

    private func sendPlainMessage<T: Encodable>(_ message: T) {
        do {
            let encoder = JSONEncoder()
            let data = try encoder.encode(message)
            if let text = String(data: data, encoding: .utf8) {
                sendRaw(text)
            }
        } catch {
            logger.error("Failed to encode message: \(error)")
        }
    }

    private func sendRaw(_ text: String) {
        webSocket?.send(text)
    }

    // MARK: - Reconnection

    private func handleDisconnected() {
        guard state != .disconnected else { return }

        logger.warning("Connection lost")

        webSocket = nil
        state = .reconnecting
        delegate?.connectionStateChanged(state)

        // Attempt reconnection
        scheduleReconnect()
    }

    private func scheduleReconnect() {
        guard reconnectAttempts < maxReconnectAttempts else {
            logger.error("Max reconnection attempts reached")
            state = .disconnected
            delegate?.connectionStateChanged(state)
            return
        }

        reconnectAttempts += 1
        let delay = baseReconnectDelay * pow(2.0, Double(reconnectAttempts - 1))

        logger.info("Reconnecting in \(delay) seconds (attempt \(reconnectAttempts)/\(maxReconnectAttempts))")

        DispatchQueue.main.asyncAfter(deadline: .now() + delay) { [weak self] in
            guard let self = self, self.state == .reconnecting else { return }

            Task {
                do {
                    try await self.connectWebSocket()
                } catch {
                    self.logger.error("Reconnection failed: \(error)")
                    self.scheduleReconnect()
                }
            }
        }
    }
}

// MARK: - Delegate Protocol

public protocol AgentConnectionDelegate: AnyObject {
    func connectionStateChanged(_ state: AgentConnection.State)
    func getCapabilities() -> [String]
    func handleInvoke(invokeId: String, capability: String, args: [String: Any])
    func handleEvent(_ event: GatewayEvent)
}

// MARK: - Errors

public enum ConnectionError: Error, LocalizedError {
    case notConfigured
    case notConnected
    case handshakeFailed

    public var errorDescription: String? {
        switch self {
        case .notConfigured: return "Connection not configured"
        case .notConnected: return "Not connected"
        case .handshakeFailed: return "Handshake failed"
        }
    }
}
