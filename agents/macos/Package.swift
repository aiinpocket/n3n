// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "N3NAgent",
    platforms: [
        .macOS(.v13)
    ],
    products: [
        .executable(name: "n3n-agent", targets: ["N3NAgent"]),
        .library(name: "N3NAgentCore", targets: ["N3NAgentCore"])
    ],
    dependencies: [
        // WebSocket client
        .package(url: "https://github.com/vapor/websocket-kit.git", from: "2.14.0"),
        // Argument parser for CLI
        .package(url: "https://github.com/apple/swift-argument-parser.git", from: "1.3.0"),
        // Logging
        .package(url: "https://github.com/apple/swift-log.git", from: "1.5.0"),
    ],
    targets: [
        // Main executable
        .executableTarget(
            name: "N3NAgent",
            dependencies: [
                "N3NAgentCore",
                .product(name: "ArgumentParser", package: "swift-argument-parser"),
                .product(name: "Logging", package: "swift-log"),
            ]
        ),
        // Core library (reusable for GUI app later)
        .target(
            name: "N3NAgentCore",
            dependencies: [
                .product(name: "WebSocketKit", package: "websocket-kit"),
                .product(name: "Logging", package: "swift-log"),
            ]
        ),
        // Tests
        .testTarget(
            name: "N3NAgentCoreTests",
            dependencies: ["N3NAgentCore"]
        ),
    ]
)
