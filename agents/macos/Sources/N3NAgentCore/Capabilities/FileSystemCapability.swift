import Foundation

/// Capability for file system operations.
public final class FileSystemCapability: Capability {

    public let id = "filesystem"
    public let name = "File System"
    public let description = "Read, write, and manage files"

    /// Maximum file size for read/write operations (10MB)
    private let maxFileSize = 10 * 1024 * 1024

    public init() {}

    public func execute(args: [String: Any]) async throws -> Any? {
        guard let operation = args["operation"] as? String else {
            throw CapabilityError.invalidArguments("Missing 'operation' argument")
        }

        switch operation {
        case "read":
            return try await readFile(args: args)
        case "write":
            return try await writeFile(args: args)
        case "delete":
            return try await deleteFile(args: args)
        case "list":
            return try await listDirectory(args: args)
        case "exists":
            return try await fileExists(args: args)
        case "info":
            return try await fileInfo(args: args)
        case "mkdir":
            return try await createDirectory(args: args)
        default:
            throw CapabilityError.invalidArguments("Unknown operation: \(operation)")
        }
    }

    // MARK: - Operations

    private func readFile(args: [String: Any]) async throws -> [String: Any] {
        guard let path = args["path"] as? String else {
            throw CapabilityError.invalidArguments("Missing 'path' argument")
        }

        let url = URL(fileURLWithPath: path)
        let encoding = args["encoding"] as? String ?? "utf8"

        // Check file size
        let attributes = try FileManager.default.attributesOfItem(atPath: path)
        let fileSize = attributes[.size] as? Int ?? 0

        guard fileSize <= maxFileSize else {
            throw CapabilityError.invalidArguments("File too large (max \(maxFileSize / 1024 / 1024)MB)")
        }

        let data = try Data(contentsOf: url)

        if encoding == "base64" {
            return [
                "content": data.base64EncodedString(),
                "encoding": "base64",
                "size": data.count
            ]
        } else {
            guard let content = String(data: data, encoding: .utf8) else {
                // Fall back to base64 for binary files
                return [
                    "content": data.base64EncodedString(),
                    "encoding": "base64",
                    "size": data.count
                ]
            }
            return [
                "content": content,
                "encoding": "utf8",
                "size": data.count
            ]
        }
    }

    private func writeFile(args: [String: Any]) async throws -> [String: Any] {
        guard let path = args["path"] as? String,
              let content = args["content"] as? String else {
            throw CapabilityError.invalidArguments("Missing 'path' or 'content' argument")
        }

        let url = URL(fileURLWithPath: path)
        let encoding = args["encoding"] as? String ?? "utf8"
        let append = args["append"] as? Bool ?? false

        let data: Data
        if encoding == "base64" {
            guard let decodedData = Data(base64Encoded: content) else {
                throw CapabilityError.invalidArguments("Invalid base64 content")
            }
            data = decodedData
        } else {
            guard let textData = content.data(using: .utf8) else {
                throw CapabilityError.invalidArguments("Invalid UTF-8 content")
            }
            data = textData
        }

        guard data.count <= maxFileSize else {
            throw CapabilityError.invalidArguments("Content too large (max \(maxFileSize / 1024 / 1024)MB)")
        }

        if append, FileManager.default.fileExists(atPath: path) {
            let fileHandle = try FileHandle(forWritingTo: url)
            try fileHandle.seekToEnd()
            try fileHandle.write(contentsOf: data)
            try fileHandle.close()
        } else {
            try data.write(to: url)
        }

        return [
            "success": true,
            "path": path,
            "size": data.count
        ]
    }

    private func deleteFile(args: [String: Any]) async throws -> [String: Any] {
        guard let path = args["path"] as? String else {
            throw CapabilityError.invalidArguments("Missing 'path' argument")
        }

        try FileManager.default.removeItem(atPath: path)

        return [
            "success": true,
            "path": path
        ]
    }

    private func listDirectory(args: [String: Any]) async throws -> [String: Any] {
        guard let path = args["path"] as? String else {
            throw CapabilityError.invalidArguments("Missing 'path' argument")
        }

        let contents = try FileManager.default.contentsOfDirectory(atPath: path)

        var items: [[String: Any]] = []
        for item in contents {
            let itemPath = (path as NSString).appendingPathComponent(item)
            var isDirectory: ObjCBool = false
            FileManager.default.fileExists(atPath: itemPath, isDirectory: &isDirectory)

            items.append([
                "name": item,
                "path": itemPath,
                "isDirectory": isDirectory.boolValue
            ])
        }

        return [
            "path": path,
            "items": items
        ]
    }

    private func fileExists(args: [String: Any]) async throws -> [String: Any] {
        guard let path = args["path"] as? String else {
            throw CapabilityError.invalidArguments("Missing 'path' argument")
        }

        var isDirectory: ObjCBool = false
        let exists = FileManager.default.fileExists(atPath: path, isDirectory: &isDirectory)

        return [
            "exists": exists,
            "isDirectory": isDirectory.boolValue,
            "path": path
        ]
    }

    private func fileInfo(args: [String: Any]) async throws -> [String: Any] {
        guard let path = args["path"] as? String else {
            throw CapabilityError.invalidArguments("Missing 'path' argument")
        }

        let attributes = try FileManager.default.attributesOfItem(atPath: path)

        return [
            "path": path,
            "size": attributes[.size] as? Int ?? 0,
            "created": (attributes[.creationDate] as? Date)?.timeIntervalSince1970 ?? 0,
            "modified": (attributes[.modificationDate] as? Date)?.timeIntervalSince1970 ?? 0,
            "isDirectory": (attributes[.type] as? FileAttributeType) == .typeDirectory,
            "permissions": attributes[.posixPermissions] as? Int ?? 0
        ]
    }

    private func createDirectory(args: [String: Any]) async throws -> [String: Any] {
        guard let path = args["path"] as? String else {
            throw CapabilityError.invalidArguments("Missing 'path' argument")
        }

        let recursive = args["recursive"] as? Bool ?? true

        try FileManager.default.createDirectory(
            atPath: path,
            withIntermediateDirectories: recursive,
            attributes: nil
        )

        return [
            "success": true,
            "path": path
        ]
    }
}
