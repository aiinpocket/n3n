import Foundation

/// Protocol for agent capabilities.
public protocol Capability {
    /// Unique capability identifier.
    var id: String { get }

    /// Human-readable name.
    var name: String { get }

    /// Description of what this capability does.
    var description: String { get }

    /// Execute the capability with given arguments.
    func execute(args: [String: Any]) async throws -> Any?
}

/// Registry of available capabilities.
public final class CapabilityRegistry {

    public static let shared = CapabilityRegistry()

    private var capabilities: [String: Capability] = [:]

    private init() {
        // Register default capabilities
        register(ScreenCaptureCapability())
        register(ShellCapability())
        register(FileSystemCapability())
        register(ClipboardCapability())
        register(NotificationCapability())
    }

    public func register(_ capability: Capability) {
        capabilities[capability.id] = capability
    }

    public func get(_ id: String) -> Capability? {
        return capabilities[id]
    }

    public func getAll() -> [Capability] {
        return Array(capabilities.values)
    }

    public func getAllIds() -> [String] {
        return Array(capabilities.keys)
    }
}

// MARK: - Capability Errors

public enum CapabilityError: Error, LocalizedError {
    case notFound(String)
    case invalidArguments(String)
    case executionFailed(String)
    case permissionDenied(String)

    public var errorDescription: String? {
        switch self {
        case .notFound(let id):
            return "Capability not found: \(id)"
        case .invalidArguments(let message):
            return "Invalid arguments: \(message)"
        case .executionFailed(let message):
            return "Execution failed: \(message)"
        case .permissionDenied(let message):
            return "Permission denied: \(message)"
        }
    }
}
