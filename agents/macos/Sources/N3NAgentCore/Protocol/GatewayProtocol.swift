import Foundation

/// Base gateway message type
public enum GatewayMessageType: String, Codable {
    case request = "req"
    case response = "res"
    case event = "event"
}

/// Gateway message base
public struct GatewayMessage: Codable {
    public let type: String
    public let id: String?

    public init(type: GatewayMessageType, id: String? = nil) {
        self.type = type.rawValue
        self.id = id
    }
}

/// Gateway request message
public struct GatewayRequest: Codable {
    public let type: String
    public let id: String
    public let method: String
    public let params: [String: AnyCodable]?

    public init(method: String, params: [String: AnyCodable]? = nil) {
        self.type = GatewayMessageType.request.rawValue
        self.id = UUID().uuidString
        self.method = method
        self.params = params
    }

    public static func handshakeAuth(
        deviceId: String,
        deviceToken: String,
        capabilities: [String]
    ) -> GatewayRequest {
        return GatewayRequest(
            method: "handshake.auth",
            params: [
                "deviceId": AnyCodable(deviceId),
                "deviceToken": AnyCodable(deviceToken),
                "capabilities": AnyCodable(capabilities),
                "client": AnyCodable([
                    "version": "1.0.0",
                    "platform": "macos",
                    "arch": getArchitecture()
                ])
            ]
        )
    }

    public static func nodeRegister(capabilities: [String]) -> GatewayRequest {
        return GatewayRequest(
            method: "node.register",
            params: [
                "capabilities": AnyCodable(capabilities)
            ]
        )
    }

    public static func ping() -> GatewayRequest {
        return GatewayRequest(method: "ping")
    }

    public static func invokeResult(invokeId: String, result: Any?, error: String?) -> GatewayRequest {
        var params: [String: AnyCodable] = [
            "invokeId": AnyCodable(invokeId)
        ]

        if let result = result {
            params["result"] = AnyCodable(result)
        }

        if let error = error {
            params["error"] = AnyCodable(error)
        }

        return GatewayRequest(
            method: "node.invoke.result",
            params: params
        )
    }

    private static func getArchitecture() -> String {
        #if arch(arm64)
        return "arm64"
        #elseif arch(x86_64)
        return "x86_64"
        #else
        return "unknown"
        #endif
    }
}

/// Gateway response message
public struct GatewayResponse: Codable {
    public let type: String
    public let id: String
    public let success: Bool
    public let result: [String: AnyCodable]?
    public let error: ResponseError?

    public struct ResponseError: Codable {
        public let code: String
        public let message: String

        public init(code: String, message: String) {
            self.code = code
            self.message = message
        }
    }

    public init(id: String, success: Bool, result: [String: AnyCodable]? = nil, error: ResponseError? = nil) {
        self.type = GatewayMessageType.response.rawValue
        self.id = id
        self.success = success
        self.result = result
        self.error = error
    }

    public static func success(id: String, result: [String: AnyCodable]) -> GatewayResponse {
        return GatewayResponse(id: id, success: true, result: result)
    }

    public static func error(id: String, code: String, message: String) -> GatewayResponse {
        return GatewayResponse(id: id, success: false, error: ResponseError(code: code, message: message))
    }
}

/// Gateway event message
public struct GatewayEvent: Codable {
    public let type: String
    public let event: String
    public let payload: [String: AnyCodable]?

    public init(event: String, payload: [String: AnyCodable]? = nil) {
        self.type = GatewayMessageType.event.rawValue
        self.event = event
        self.payload = payload
    }

    // Common events
    public static let handshakeChallenge = "handshake.challenge"
    public static let connected = "connected"
    public static let disconnected = "disconnected"
    public static let nodeInvoke = "node.invoke"
    public static let ping = "ping"
    public static let pong = "pong"
}

/// Node invoke request (from server)
public struct NodeInvokeRequest: Codable {
    public let invokeId: String
    public let capability: String
    public let args: [String: AnyCodable]?

    public init(invokeId: String, capability: String, args: [String: AnyCodable]? = nil) {
        self.invokeId = invokeId
        self.capability = capability
        self.args = args
    }
}

// MARK: - AnyCodable Helper

/// A type-erased Codable value.
public struct AnyCodable: Codable {
    public let value: Any

    public init(_ value: Any) {
        self.value = value
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()

        if container.decodeNil() {
            self.value = NSNull()
        } else if let bool = try? container.decode(Bool.self) {
            self.value = bool
        } else if let int = try? container.decode(Int.self) {
            self.value = int
        } else if let double = try? container.decode(Double.self) {
            self.value = double
        } else if let string = try? container.decode(String.self) {
            self.value = string
        } else if let array = try? container.decode([AnyCodable].self) {
            self.value = array.map { $0.value }
        } else if let dictionary = try? container.decode([String: AnyCodable].self) {
            self.value = dictionary.mapValues { $0.value }
        } else {
            throw DecodingError.dataCorruptedError(in: container, debugDescription: "Cannot decode value")
        }
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()

        switch value {
        case is NSNull:
            try container.encodeNil()
        case let bool as Bool:
            try container.encode(bool)
        case let int as Int:
            try container.encode(int)
        case let double as Double:
            try container.encode(double)
        case let string as String:
            try container.encode(string)
        case let array as [Any]:
            try container.encode(array.map { AnyCodable($0) })
        case let dictionary as [String: Any]:
            try container.encode(dictionary.mapValues { AnyCodable($0) })
        default:
            throw EncodingError.invalidValue(value, EncodingError.Context(
                codingPath: container.codingPath,
                debugDescription: "Cannot encode value"
            ))
        }
    }

    public func asString() -> String? {
        return value as? String
    }

    public func asInt() -> Int? {
        return value as? Int
    }

    public func asBool() -> Bool? {
        return value as? Bool
    }

    public func asDouble() -> Double? {
        return value as? Double
    }

    public func asArray() -> [Any]? {
        return value as? [Any]
    }

    public func asDictionary() -> [String: Any]? {
        return value as? [String: Any]
    }
}
