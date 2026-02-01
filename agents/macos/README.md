# N3N Agent

macOS Local Agent for the N3N platform. Connects your Mac to the N3N workflow orchestration platform with end-to-end encryption.

## Features

- **Secure Communication**: End-to-end encryption using X25519 key exchange and AES-256-GCM
- **Device Pairing**: Simple 6-digit pairing code for initial setup
- **Keychain Storage**: All sensitive data stored securely in macOS Keychain
- **Multiple Capabilities**:
  - Screen capture
  - Shell command execution
  - File system operations
  - Clipboard access
  - System notifications

## Requirements

- macOS 13.0 or later
- Swift 5.9+

## Installation

### From Release

Download the latest release from the releases page and copy `n3n-agent` to `/usr/local/bin/` or any directory in your PATH.

### From Source

```bash
# Clone the repository
git clone https://github.com/aiinpocket/n3n-agent.git
cd n3n-agent

# Build release
swift build -c release

# The binary will be at .build/release/n3n-agent
```

## Usage

### Pair with Platform

First, initiate pairing from the N3N platform to get a 6-digit pairing code, then:

```bash
n3n-agent pair --url https://your-n3n-platform.com --code 123456
```

### Run the Agent

```bash
# Run in foreground
n3n-agent run --foreground

# Or simply (runs as daemon)
n3n-agent
```

### Check Status

```bash
n3n-agent status
```

### Configuration

```bash
# Show configuration
n3n-agent config show

# Update settings
n3n-agent config set --device-name "My MacBook Pro"
n3n-agent config set --direct-connection true --listen-port 9999
```

### Unpair

```bash
n3n-agent unpair
```

## Capabilities

| ID | Description |
|----|-------------|
| `screen.capture` | Capture screenshots |
| `shell.execute` | Execute shell commands |
| `filesystem` | Read/write files, list directories |
| `clipboard` | Read/write clipboard |
| `notification` | Show system notifications |

## Security

- **Key Exchange**: X25519 ECDH
- **Encryption**: AES-256-GCM
- **Key Derivation**: HKDF-SHA256
- **Replay Protection**: Timestamps + sequence numbers
- **Secure Storage**: macOS Keychain

All communication is encrypted end-to-end. Even if HTTPS is compromised, messages remain secure.

## Architecture

```
┌─────────────────────────────────────────────────┐
│  N3N Agent                                       │
│  ┌───────────────────────────────────────────┐  │
│  │  CLI Interface (ArgumentParser)            │  │
│  └───────────────────┬───────────────────────┘  │
│  ┌───────────────────▼───────────────────────┐  │
│  │  Agent Core                                │  │
│  │  ┌─────────────┐  ┌─────────────────────┐ │  │
│  │  │ Connection  │  │ Capability Registry │ │  │
│  │  │ (WebSocket) │  │                     │ │  │
│  │  └──────┬──────┘  └─────────────────────┘ │  │
│  │  ┌──────▼──────┐  ┌─────────────────────┐ │  │
│  │  │ SecureMsg   │  │ Pairing Service     │ │  │
│  │  │ Service     │  │                     │ │  │
│  │  └──────┬──────┘  └─────────────────────┘ │  │
│  │  ┌──────▼──────────────────────────────┐  │  │
│  │  │  AgentCrypto (CryptoKit)            │  │  │
│  │  │  • X25519 Key Exchange              │  │  │
│  │  │  • AES-256-GCM Encryption           │  │  │
│  │  │  • HKDF Key Derivation              │  │  │
│  │  └─────────────────────────────────────┘  │  │
│  │  ┌─────────────────────────────────────┐  │  │
│  │  │  Keychain Storage                   │  │  │
│  │  └─────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
           │
           │ WebSocket (wss://)
           │ End-to-End Encrypted
           ▼
┌─────────────────────────────────────────────────┐
│  N3N Platform                                    │
└─────────────────────────────────────────────────┘
```

## License

MIT License - See LICENSE file for details.
