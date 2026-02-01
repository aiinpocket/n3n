# N3N Agent for Windows

Windows agent for the N3N visual workflow platform. Enables remote control and automation of Windows machines.

## Features

- **Secure Communication**: X25519 ECDH key exchange with AES-256-GCM encryption
- **Screen Capture**: Capture screenshots of displays
- **Shell Execution**: Run commands via cmd.exe
- **File System**: Read, write, copy, move, delete files and directories
- **Clipboard**: Read and write clipboard content
- **Notifications**: Display Windows toast notifications

## Requirements

- Windows 10/11 or Windows Server 2016+
- .NET 8.0 Runtime (included in self-contained build)

## Installation

### Option 1: Download Pre-built Binary

Download the latest release from the N3N platform's download page.

### Option 2: Build from Source

```powershell
# Clone the repository
git clone https://github.com/your-org/n3n_agent_windows.git
cd n3n_agent_windows

# Build self-contained executable
dotnet publish src/N3NAgent.CLI -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -o ./publish

# The executable will be at ./publish/n3n-agent.exe
```

## Usage

### Pair with Platform

1. Go to the N3N platform web interface
2. Navigate to **Device Management**
3. Click **Register New Device**
4. Copy the 6-digit pairing code

```powershell
# Pair the agent
n3n-agent pair --url https://your-n3n-platform.com --code 123456
```

### Run the Agent

```powershell
# Start the agent (connects to platform)
n3n-agent run
```

The agent will:
- Connect to the platform via secure WebSocket
- Authenticate using stored credentials
- Listen for capability invocations
- Automatically reconnect on disconnection

### Check Status

```powershell
n3n-agent status
```

### Unpair

```powershell
n3n-agent unpair
```

## Configuration

Credentials are stored securely in Windows Credential Manager under:
- `N3NAgent:DeviceId`
- `N3NAgent:DeviceToken`
- `N3NAgent:SessionKey`
- `N3NAgent:PlatformUrl`

## Capabilities

| Capability | Description |
|------------|-------------|
| `screen.capture` | Capture screenshots |
| `shell.execute` | Execute shell commands |
| `fs.operation` | File system operations |
| `clipboard.operation` | Clipboard read/write |
| `notification.show` | Display notifications |

## Security

- **End-to-end Encryption**: All messages are encrypted with AES-256-GCM
- **Key Exchange**: X25519 ECDH with HKDF for key derivation
- **Replay Protection**: Timestamps and sequence numbers prevent replay attacks
- **Secure Storage**: Credentials stored in Windows Credential Manager

## Running as a Service

To run the agent as a Windows service:

```powershell
# Install NSSM (Non-Sucking Service Manager)
choco install nssm

# Create service
nssm install N3NAgent "C:\path\to\n3n-agent.exe" run

# Start service
nssm start N3NAgent
```

Or use the built-in Windows Service Manager with a service wrapper.

## Troubleshooting

### Agent not connecting

1. Check if the platform URL is correct
2. Verify firewall allows outbound WebSocket connections
3. Run `n3n-agent status` to check configuration

### Screen capture not working

- Ensure the agent has permission to capture the screen
- On some systems, may require running as Administrator

### Commands timing out

- Default timeout is 60 seconds
- Maximum timeout is 300 seconds
- Long-running commands may need timeout adjustment

## License

See the main N3N project for license information.
