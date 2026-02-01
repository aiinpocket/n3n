import Foundation

/// Capability for executing shell commands.
public final class ShellCapability: Capability {

    public let id = "shell.execute"
    public let name = "Shell Execute"
    public let description = "Execute shell commands and return output"

    /// Maximum execution time in seconds
    private let maxTimeout: TimeInterval = 300

    public init() {}

    public func execute(args: [String: Any]) async throws -> Any? {
        guard let command = args["command"] as? String else {
            throw CapabilityError.invalidArguments("Missing 'command' argument")
        }

        let timeout = min(args["timeout"] as? TimeInterval ?? 60, maxTimeout)
        let workingDirectory = args["workingDirectory"] as? String
        let environment = args["environment"] as? [String: String]

        return try await executeCommand(
            command: command,
            timeout: timeout,
            workingDirectory: workingDirectory,
            environment: environment
        )
    }

    private func executeCommand(
        command: String,
        timeout: TimeInterval,
        workingDirectory: String?,
        environment: [String: String]?
    ) async throws -> [String: Any] {
        let process = Process()
        let stdoutPipe = Pipe()
        let stderrPipe = Pipe()

        process.executableURL = URL(fileURLWithPath: "/bin/zsh")
        process.arguments = ["-c", command]
        process.standardOutput = stdoutPipe
        process.standardError = stderrPipe

        if let workingDirectory = workingDirectory {
            process.currentDirectoryURL = URL(fileURLWithPath: workingDirectory)
        }

        if let environment = environment {
            var env = ProcessInfo.processInfo.environment
            for (key, value) in environment {
                env[key] = value
            }
            process.environment = env
        }

        // Start process
        do {
            try process.run()
        } catch {
            throw CapabilityError.executionFailed("Failed to start process: \(error.localizedDescription)")
        }

        // Create timeout task
        let timeoutTask = Task {
            try await Task.sleep(nanoseconds: UInt64(timeout * 1_000_000_000))
            if process.isRunning {
                process.terminate()
            }
        }

        // Wait for process
        process.waitUntilExit()
        timeoutTask.cancel()

        // Read output
        let stdoutData = stdoutPipe.fileHandleForReading.readDataToEndOfFile()
        let stderrData = stderrPipe.fileHandleForReading.readDataToEndOfFile()

        let stdout = String(data: stdoutData, encoding: .utf8) ?? ""
        let stderr = String(data: stderrData, encoding: .utf8) ?? ""

        return [
            "exitCode": process.terminationStatus,
            "stdout": stdout,
            "stderr": stderr,
            "success": process.terminationStatus == 0
        ]
    }
}
