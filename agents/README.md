# N3N Agents

Cross-platform agents for the N3N visual workflow platform. Each agent connects local devices to the N3N platform with end-to-end encryption.

## Platforms

| Directory | Platform | Technology | Status |
|-----------|----------|------------|--------|
| `macos/` | macOS 13+ | Swift 5.9 | Active |
| `windows/` | Windows 10/11 | .NET 8.0 / C# | Active |
| `linux/` | Linux | TBD | Planned |

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     N3N Agents                               │
│                                                              │
│  ┌──────────────────┐  ┌──────────────────┐                 │
│  │   macOS Agent    │  │  Windows Agent   │                 │
│  │   (Swift 5.9)    │  │  (.NET 8.0)      │                 │
│  └────────┬─────────┘  └────────┬─────────┘                 │
│           │                     │                            │
│           └──────────┬──────────┘                            │
│                      │                                       │
│                      ▼                                       │
│           ┌──────────────────────┐                          │
│           │   Common Protocol    │                          │
│           │  • X25519 Key Exchange                          │
│           │  • AES-256-GCM Encryption                       │
│           │  • WebSocket Transport                          │
│           └──────────────────────┘                          │
│                      │                                       │
└──────────────────────│───────────────────────────────────────┘
                       │
                       │ WebSocket (wss://)
                       │ End-to-End Encrypted
                       ▼
              ┌────────────────────┐
              │   N3N Platform     │
              │   /gateway         │
              └────────────────────┘
```

## Common Features

All agents share the same capabilities:

| Capability | Description |
|------------|-------------|
| `screen.capture` | Capture screenshots |
| `shell.execute` | Execute shell commands |
| `filesystem` | File system operations |
| `clipboard` | Clipboard read/write |
| `notification` | System notifications |

## Security

All agents implement identical security measures:

- **Key Exchange**: X25519 ECDH
- **Encryption**: AES-256-GCM
- **Key Derivation**: HKDF-SHA256
- **Replay Protection**: Timestamps + sequence numbers
- **Secure Storage**: Platform-specific (Keychain / Credential Manager)

## Build Instructions

### macOS Agent

```bash
cd macos
swift build -c release
# Binary: .build/release/n3n-agent
```

### Windows Agent

```powershell
cd windows
dotnet publish src/N3NAgent.CLI -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -o ./publish
# Binary: ./publish/n3n-agent.exe
```

## Usage

All agents share the same CLI interface:

```bash
# Pair with platform
n3n-agent pair --url https://your-platform.com --code 123456

# Run agent
n3n-agent run

# Check status
n3n-agent status

# Unpair
n3n-agent unpair
```

## Development

See individual platform directories for detailed development guides:

- [macOS Agent](./macos/README.md)
- [Windows Agent](./windows/README.md)

## License

See the main N3N project for license information.
