import Foundation
import ArgumentParser
import Logging
import N3NAgentCore
import Darwin
#if os(macOS)
import AppKit
#endif

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

    @Option(name: .long, help: "Platform URL for auto-pairing")
    var platformUrl: String = "http://localhost:8080"

    func run() async throws {
        // Configure logging
        LoggingSystem.bootstrap { label in
            var handler = StreamLogHandler.standardOutput(label: label)
            handler.logLevel = parseLogLevel(logLevel)
            return handler
        }

        let logger = Logger(label: "n3n.agent.cli")

        // In CLI mode, use file storage to avoid Keychain blocking issues
        KeychainStorage.shared.useFileStorageOnly = true
        logger.info("Running in CLI mode, using file storage")

        let agent = Agent.shared

        // Check if paired - if not, try auto-pairing with config file
        if !agent.isPaired {
            logger.info("Agent is not paired. Looking for config file...")

            // Try to find and load config file (auto-pairing)
            if let configUrl = findConfigFile() {
                logger.info("Found config file: \(configUrl.path)")
                try await autoPairWithConfig(agent: agent, configUrl: configUrl, logger: logger)
            } else {
                // No config file found, start interactive wizard
                logger.info("No config file found. Starting setup wizard...")
                try await runSetupWizard(agent: agent, platformUrl: platformUrl, logger: logger)
            }
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

    /// Find config file in common locations
    private func findConfigFile() -> URL? {
        let fileManager = FileManager.default
        let configFileName = "n3n-agent-config.json"

        // Check locations in order:
        // 1. Same directory as the app bundle (for zip distribution)
        // 2. Inside the app's Resources folder
        // 3. Same directory as executable
        // 4. Current working directory
        // 5. Home directory
        // 6. Downloads folder

        var locations: [URL] = []

        // App bundle parent directory (where the .app is located)
        if let bundlePath = Bundle.main.bundlePath as NSString? {
            let appDir = (bundlePath as NSString).deletingLastPathComponent
            locations.append(URL(fileURLWithPath: appDir).appendingPathComponent(configFileName))
        }

        // Inside app bundle Resources
        if let resourcePath = Bundle.main.resourcePath {
            locations.append(URL(fileURLWithPath: resourcePath).appendingPathComponent(configFileName))
        }

        // Executable directory
        let executablePath = Bundle.main.executablePath ?? ""
        let execDir = (executablePath as NSString).deletingLastPathComponent
        locations.append(URL(fileURLWithPath: execDir).appendingPathComponent(configFileName))

        // Current working directory
        locations.append(URL(fileURLWithPath: fileManager.currentDirectoryPath).appendingPathComponent(configFileName))

        // Home directory
        locations.append(fileManager.homeDirectoryForCurrentUser.appendingPathComponent(configFileName))

        // Downloads folder
        if let downloadsUrl = fileManager.urls(for: .downloadsDirectory, in: .userDomainMask).first {
            locations.append(downloadsUrl.appendingPathComponent(configFileName))
        }

        for location in locations {
            if fileManager.fileExists(atPath: location.path) {
                return location
            }
        }

        return nil
    }

    /// Auto-pair using config file
    private func autoPairWithConfig(agent: Agent, configUrl: URL, logger: Logger) async throws {
        print("")
        print("╔══════════════════════════════════════════════════════════════╗")
        print("║              N3N Agent 自動配對                               ║")
        print("╠══════════════════════════════════════════════════════════════╣")
        print("║  找到配置檔案，正在自動完成配對...                             ║")
        print("╚══════════════════════════════════════════════════════════════╝")
        print("")

        // Load config
        let config = try RegistrationConfig.load(from: configUrl)

        // Get device name
        let deviceName = Host.current().localizedName ?? "My Mac"

        print("配置資訊：")
        print("  平台: \(config.gateway.domain):\(config.gateway.port)")
        print("  裝置: \(deviceName)")
        print("")
        print("正在配對...")

        // Register with token
        _ = try await PairingService.shared.registerWithToken(config: config, deviceName: deviceName)

        print("")
        print("✅ 配對成功！")
        print("")

        // Optionally delete config file after successful pairing (for security)
        try? FileManager.default.removeItem(at: configUrl)
        logger.info("Config file removed after successful pairing")
    }

    private func runSetupWizard(agent: Agent, platformUrl: String, logger: Logger) async throws {
        print("")
        print("╔══════════════════════════════════════════════════════════════╗")
        print("║              N3N Agent 設定精靈                               ║")
        print("╠══════════════════════════════════════════════════════════════╣")
        print("║                                                              ║")
        print("║  歡迎使用 N3N Agent！                                         ║")
        print("║  請依照以下步驟完成配對：                                      ║")
        print("║                                                              ║")
        print("║  1. 開啟瀏覽器並登入 N3N 平台                                  ║")
        print("║  2. 進入「設備管理」頁面                                       ║")
        print("║  3. 點擊「新增 Agent」產生配置                                 ║")
        print("║  4. 複製產生的 Token 貼到下方                                  ║")
        print("║                                                              ║")
        print("╚══════════════════════════════════════════════════════════════╝")
        print("")

        // Open browser to the pairing page
        let pairingUrl = "\(platformUrl)/devices?action=add-agent"
        print("正在開啟瀏覽器... \(pairingUrl)")
        openBrowser(url: pairingUrl)

        print("")
        print("請輸入 Token（從網頁複製）：")
        print("> ", terminator: "")

        guard let token = readLine()?.trimmingCharacters(in: .whitespacesAndNewlines), !token.isEmpty else {
            print("❌ Token 不能為空")
            throw ExitCode.failure
        }

        // Get device name
        let defaultName = Host.current().localizedName ?? "My Mac"
        print("")
        print("裝置名稱 (按 Enter 使用預設: \(defaultName))：")
        print("> ", terminator: "")

        let inputName = readLine()?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let deviceName = inputName.isEmpty ? defaultName : inputName

        print("")
        print("正在配對...")

        do {
            try await agent.pairWithToken(
                platformUrl: platformUrl,
                token: token,
                deviceName: deviceName
            )

            print("")
            print("✅ 配對成功！")
            print("   裝置名稱: \(deviceName)")
            print("   平台: \(platformUrl)")
            print("")

        } catch {
            print("")
            print("❌ 配對失敗: \(error.localizedDescription)")
            print("")
            print("請確認：")
            print("  - Token 是否正確")
            print("  - Token 是否已過期（24小時有效）")
            print("  - 網路連線是否正常")
            throw ExitCode.failure
        }
    }

    private func openBrowser(url: String) {
        #if os(macOS)
        if let url = URL(string: url) {
            NSWorkspace.shared.open(url)
        }
        #endif
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
