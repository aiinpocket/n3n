import Foundation
import AppKit

/// Capability for clipboard operations.
public final class ClipboardCapability: Capability {

    public let id = "clipboard"
    public let name = "Clipboard"
    public let description = "Read and write to system clipboard"

    public init() {}

    public func execute(args: [String: Any]) async throws -> Any? {
        guard let operation = args["operation"] as? String else {
            throw CapabilityError.invalidArguments("Missing 'operation' argument")
        }

        switch operation {
        case "read":
            return try await readClipboard(args: args)
        case "write":
            return try await writeClipboard(args: args)
        case "clear":
            return try await clearClipboard()
        default:
            throw CapabilityError.invalidArguments("Unknown operation: \(operation)")
        }
    }

    private func readClipboard(args: [String: Any]) async throws -> [String: Any] {
        let pasteboard = NSPasteboard.general

        // Try to get text
        if let text = pasteboard.string(forType: .string) {
            return [
                "type": "text",
                "content": text
            ]
        }

        // Try to get image
        if let imageData = pasteboard.data(forType: .tiff) ?? pasteboard.data(forType: .png) {
            return [
                "type": "image",
                "content": imageData.base64EncodedString(),
                "encoding": "base64"
            ]
        }

        // Try to get file URLs
        if let urls = pasteboard.readObjects(forClasses: [NSURL.self], options: nil) as? [URL], !urls.isEmpty {
            return [
                "type": "files",
                "content": urls.map { $0.path }
            ]
        }

        return [
            "type": "empty"
        ]
    }

    private func writeClipboard(args: [String: Any]) async throws -> [String: Any] {
        guard let content = args["content"] else {
            throw CapabilityError.invalidArguments("Missing 'content' argument")
        }

        let type = args["type"] as? String ?? "text"
        let pasteboard = NSPasteboard.general

        pasteboard.clearContents()

        switch type {
        case "text":
            guard let text = content as? String else {
                throw CapabilityError.invalidArguments("Content must be a string for text type")
            }
            pasteboard.setString(text, forType: .string)

        case "image":
            guard let base64 = content as? String,
                  let data = Data(base64Encoded: base64) else {
                throw CapabilityError.invalidArguments("Content must be base64 encoded for image type")
            }
            pasteboard.setData(data, forType: .png)

        default:
            throw CapabilityError.invalidArguments("Unknown content type: \(type)")
        }

        return [
            "success": true,
            "type": type
        ]
    }

    private func clearClipboard() async throws -> [String: Any] {
        NSPasteboard.general.clearContents()
        return ["success": true]
    }
}
