import Foundation
import ArgumentParser
import Logging
import N3NAgentCore
import Darwin

@main
struct N3NAgentCLI: AsyncParsableCommand {

    static var configuration = CommandConfiguration(
        commandName: "n3n-agent",
        abstract: "N3N Local Agent - Connect your Mac to the N3N platform",
        version: "1.0.0",
        subcommands: [
            Run.self,
            Pair.self,
            Unpair.self,
            Status.self,
            Config.self
        ],
        defaultSubcommand: Run.self
    )
}

// MARK: - Run Command

struct Run: AsyncParsableCommand {

    static var configuration = CommandConfiguration(
        abstract: "Run the agent (default command)"
    )

    @Flag(name: .shortAndLong, help: "Run in foreground (don't daemonize)")
    var foreground: Bool = false

    @Option(name: .shortAndLong, help: "Log level (trace, debug, info, warning, error)")
    var logLevel: String = "info"

    func run() async throws {
        // Configure logging
        LoggingSystem.bootstrap { label in
            var handler = StreamLogHandler.standardOutput(label: label)
            handler.logLevel = parseLogLevel(logLevel)
            return handler
        }

        let logger = Logger(label: "n3n.agent.cli")
        let agent = Agent.shared

        // Check if paired
        guard agent.isPaired else {
            logger.error("Agent is not paired. Run 'n3n-agent pair' first.")
            throw ExitCode.failure
        }

        logger.info("Starting N3N Agent...")

        // Set up signal handlers
        let signalSource = DispatchSource.makeSignalSource(signal: SIGINT, queue: .main)
        signalSource.setEventHandler {
            logger.info("Received SIGINT, shutting down...")
            Task {
                await agent.stop()
                Darwin.exit(0)
            }
        }
        signal(SIGINT, SIG_IGN)
        signalSource.resume()

        // Start agent
        do {
            try await agent.start()

            logger.info("Agent running. Press Ctrl+C to stop.")

            // Use dispatchMain to keep the process alive
            dispatchMain()

        } catch {
            logger.error("Failed to start agent: \(error.localizedDescription)")
            throw ExitCode.failure
        }
    }

    private func parseLogLevel(_ level: String) -> Logger.Level {
        switch level.lowercased() {
        case "trace": return .trace
        case "debug": return .debug
        case "info": return .info
        case "warning", "warn": return .warning
        case "error": return .error
        default: return .info
        }
    }
}

// MARK: - Pair Command

struct Pair: AsyncParsableCommand {

    static var configuration = CommandConfiguration(
        abstract: "Pair with an N3N platform"
    )

    @Option(name: .shortAndLong, help: "Platform URL (e.g., https://n3n.example.com)")
    var url: String

    @Option(name: .shortAndLong, help: "6-digit pairing code")
    var code: String

    @Option(name: .shortAndLong, help: "Device name (default: hostname)")
    var name: String?

    func run() async throws {
        LoggingSystem.bootstrap(StreamLogHandler.standardOutput)
        let logger = Logger(label: "n3n.agent.cli")

        let agent = Agent.shared
        let deviceName = name ?? Host.current().localizedName ?? "My Mac"

        logger.info("Pairing with platform: \(url)")
        logger.info("Device name: \(deviceName)")

        do {
            try await agent.pair(
                platformUrl: url,
                pairingCode: code,
                deviceName: deviceName
            )

            print("✓ Pairing successful!")
            print("  Run 'n3n-agent' to start the agent.")

        } catch {
            logger.error("Pairing failed: \(error.localizedDescription)")
            throw ExitCode.failure
        }
    }
}

// MARK: - Unpair Command

struct Unpair: AsyncParsableCommand {

    static var configuration = CommandConfiguration(
        abstract: "Unpair from the platform"
    )

    @Flag(name: .shortAndLong, help: "Skip confirmation prompt")
    var force: Bool = false

    func run() async throws {
        let agent = Agent.shared

        guard agent.isPaired else {
            print("Agent is not paired with any platform.")
            return
        }

        if !force {
            print("Are you sure you want to unpair? This will remove all stored credentials.")
            print("Type 'yes' to confirm: ", terminator: "")

            guard let response = readLine(), response.lowercased() == "yes" else {
                print("Cancelled.")
                return
            }
        }

        try await agent.unpair()
        print("✓ Unpairing successful.")
    }
}

// MARK: - Status Command

struct Status: AsyncParsableCommand {

    static var configuration = CommandConfiguration(
        abstract: "Show agent status"
    )

    func run() async throws {
        let agent = Agent.shared

        print("N3N Agent Status")
        print("================")

        if agent.isPaired {
            print("Paired: Yes")

            if let config = try agent.getConfig() {
                print("Platform: \(config.platformUrl)")
                print("Device Name: \(config.deviceName)")
                print("Direct Connection: \(config.enableDirectConnection ? "Enabled" : "Disabled")")
                if config.enableDirectConnection {
                    print("Listen Port: \(config.listenPort)")
                    if let addr = config.externalAddress {
                        print("External Address: \(addr)")
                    }
                }
            }

            print("Connected: \(agent.isConnected ? "Yes" : "No")")
        } else {
            print("Paired: No")
            print("")
            print("Run 'n3n-agent pair --url <platform-url> --code <pairing-code>' to pair.")
        }
    }
}

// MARK: - Config Command

struct Config: AsyncParsableCommand {

    static var configuration = CommandConfiguration(
        abstract: "Manage agent configuration",
        subcommands: [
            ConfigShow.self,
            ConfigSet.self
        ],
        defaultSubcommand: ConfigShow.self
    )
}

struct ConfigShow: AsyncParsableCommand {

    static var configuration = CommandConfiguration(
        commandName: "show",
        abstract: "Show current configuration"
    )

    func run() async throws {
        let agent = Agent.shared

        guard let config = try agent.getConfig() else {
            print("No configuration found. Pair with a platform first.")
            return
        }

        print("Configuration")
        print("=============")
        print("Platform URL: \(config.platformUrl)")
        print("Device Name: \(config.deviceName)")
        print("Auto Start: \(config.autoStart)")
        print("Direct Connection: \(config.enableDirectConnection)")
        print("Listen Port: \(config.listenPort)")
        print("External Address: \(config.externalAddress ?? "Not set")")
        print("Log Level: \(config.logLevel)")
    }
}

struct ConfigSet: AsyncParsableCommand {

    static var configuration = CommandConfiguration(
        commandName: "set",
        abstract: "Update configuration"
    )

    @Option(name: .long, help: "Device name")
    var deviceName: String?

    @Option(name: .long, help: "Enable direct connection (true/false)")
    var directConnection: String?

    @Option(name: .long, help: "Listen port for direct connection")
    var listenPort: Int?

    @Option(name: .long, help: "External address for direct connection")
    var externalAddress: String?

    @Option(name: .long, help: "Log level")
    var logLevel: String?

    func run() async throws {
        let agent = Agent.shared

        guard var config = try agent.getConfig() else {
            print("No configuration found. Pair with a platform first.")
            throw ExitCode.failure
        }

        var updated = false

        if let deviceName = deviceName {
            config.deviceName = deviceName
            updated = true
        }

        if let dc = directConnection {
            config.enableDirectConnection = dc.lowercased() == "true"
            updated = true
        }

        if let port = listenPort {
            config.listenPort = port
            updated = true
        }

        if let addr = externalAddress {
            config.externalAddress = addr.isEmpty ? nil : addr
            updated = true
        }

        if let level = logLevel {
            config.logLevel = level
            updated = true
        }

        if updated {
            try agent.updateConfig(config)
            print("✓ Configuration updated.")
        } else {
            print("No changes specified.")
        }
    }
}
