import Foundation
import AppKit

/// Capability for creating and managing Apple Notes.
/// Uses AppleScript to interact with the Notes application.
public final class NotesCapability: Capability {

    public let id = "notes"
    public let name = "Apple Notes"
    public let description = "Create and manage notes in Apple Notes app"

    public init() {}

    public func execute(args: [String: Any]) async throws -> Any? {
        guard let action = args["action"] as? String else {
            throw CapabilityError.invalidArguments("Missing 'action' argument")
        }

        switch action {
        case "create":
            return try await createNote(args: args)
        case "append":
            return try await appendToNote(args: args)
        case "list":
            return try await listNotes(args: args)
        default:
            throw CapabilityError.invalidArguments("Unknown action: \(action)")
        }
    }

    private func createNote(args: [String: Any]) async throws -> [String: Any] {
        guard let title = args["title"] as? String else {
            throw CapabilityError.invalidArguments("Missing 'title' argument for create action")
        }

        let body = args["body"] as? String ?? ""
        let folder = args["folder"] as? String ?? "Notes"

        // Escape special characters for AppleScript
        let escapedTitle = escapeForAppleScript(title)
        let escapedBody = escapeForAppleScript(body)
        let escapedFolder = escapeForAppleScript(folder)

        let script = """
        tell application "Notes"
            tell account "iCloud"
                set targetFolder to folder "\(escapedFolder)"
                set newNote to make new note at targetFolder with properties {name:"\(escapedTitle)", body:"\(escapedBody)"}
                return id of newNote
            end tell
        end tell
        """

        let result = try executeAppleScript(script)

        return [
            "success": true,
            "noteId": result,
            "title": title,
            "folder": folder
        ]
    }

    private func appendToNote(args: [String: Any]) async throws -> [String: Any] {
        guard let title = args["title"] as? String else {
            throw CapabilityError.invalidArguments("Missing 'title' argument for append action")
        }
        guard let content = args["content"] as? String else {
            throw CapabilityError.invalidArguments("Missing 'content' argument for append action")
        }

        let folder = args["folder"] as? String ?? "Notes"

        let escapedTitle = escapeForAppleScript(title)
        let escapedContent = escapeForAppleScript(content)
        let escapedFolder = escapeForAppleScript(folder)

        let script = """
        tell application "Notes"
            tell account "iCloud"
                set targetFolder to folder "\(escapedFolder)"
                set targetNote to first note of targetFolder whose name is "\(escapedTitle)"
                set currentBody to body of targetNote
                set body of targetNote to currentBody & "<br>" & "\(escapedContent)"
                return "appended"
            end tell
        end tell
        """

        _ = try executeAppleScript(script)

        return [
            "success": true,
            "title": title,
            "action": "appended"
        ]
    }

    private func listNotes(args: [String: Any]) async throws -> [String: Any] {
        let folder = args["folder"] as? String ?? "Notes"
        let limit = args["limit"] as? Int ?? 10
        let escapedFolder = escapeForAppleScript(folder)

        let script = """
        tell application "Notes"
            tell account "iCloud"
                set targetFolder to folder "\(escapedFolder)"
                set noteList to {}
                set noteCount to count of notes in targetFolder
                set maxNotes to \(limit)
                if noteCount < maxNotes then
                    set maxNotes to noteCount
                end if
                repeat with i from 1 to maxNotes
                    set currentNote to note i of targetFolder
                    set end of noteList to {noteName:name of currentNote, noteId:id of currentNote}
                end repeat
                return noteList
            end tell
        end tell
        """

        let result = try executeAppleScript(script)

        return [
            "success": true,
            "notes": result,
            "folder": folder
        ]
    }

    private func executeAppleScript(_ source: String) throws -> String {
        var error: NSDictionary?
        let script = NSAppleScript(source: source)
        let result = script?.executeAndReturnError(&error)

        if let error = error {
            let errorMessage = error[NSAppleScript.errorMessage] as? String ?? "Unknown error"
            throw CapabilityError.executionFailed("AppleScript error: \(errorMessage)")
        }

        return result?.stringValue ?? ""
    }

    private func escapeForAppleScript(_ string: String) -> String {
        return string
            .replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: "\"", with: "\\\"")
            .replacingOccurrences(of: "\n", with: "<br>")
    }
}
