# N3N Flow Platform

English | [日本語](README.ja.md) | [繁體中文](README.md)

> Build automation workflows just by describing them - Let AI turn your ideas into executable workflows

---

## Hardware Requirements

N3N includes multiple built-in services. Choose the appropriate hardware based on your use case:

### Minimum Requirements (Basic)

| Item | Spec |
|------|------|
| **CPU** | 2 cores |
| **RAM** | 4 GB |
| **Disk** | 10 GB SSD |
| **OS** | Windows 10/11, macOS 12+, Ubuntu 20.04+ |

> Suitable for: personal use, simple flows, no AI features

### Recommended (with Cloud AI)

| Item | Spec |
|------|------|
| **CPU** | 4 cores |
| **RAM** | 8 GB |
| **Disk** | 20 GB SSD |
| **Network** | Stable internet connection |

> Suitable for: daily use, moderate complexity flows, using OpenAI/Claude/Gemini cloud AI

### Advanced (Local AI Optimizer)

| Item | Spec |
|------|------|
| **CPU** | 8+ cores |
| **RAM** | 16 GB+ (32 GB recommended) |
| **Disk** | 50 GB SSD |
| **GPU** | Optional: NVIDIA GPU 8GB+ VRAM for faster inference |

> Suitable for: local AI flow optimizer, high-load/parallel flows, enterprise deployment

### Built-in Service Resource Usage

| Service | Memory | Description |
|---------|--------|-------------|
| **N3N App** | ~512 MB | Spring Boot application |
| **PostgreSQL** | ~256 MB | Relational database |
| **Redis** | ~128 MB | Cache and execution state |
| **MongoDB** | ~256 MB | NoSQL database (used by workflow nodes) |
| **Flow Optimizer** | ~2-4 GB | Local LLM (optional) |

---

## What is this?

N3N is a **visual workflow automation platform** that lets you:

- **Describe in natural language** the workflow you want, and the AI assistant generates it for you
- **Drag and drop** to adjust the flowchart, no coding required
- **Connect external services** (APIs, databases, etc.) to automate your daily tasks

Perfect for **people who can't code but want to automate workflows**, and also for **people who enjoy planning** to participate in design.

---

## Quick Start

### 1. Install Docker

If you don't have Docker yet, please install it first:

| OS | Download Link |
|---------|---------|
| Windows | [Docker Desktop for Windows](https://www.docker.com/products/docker-desktop/) |
| Mac | [Docker Desktop for Mac](https://www.docker.com/products/docker-desktop/) |
| Linux | [Docker Engine](https://docs.docker.com/engine/install/) |

### 2. Start N3N

Open your terminal:
- **Windows**: Press `Win + R`, type `cmd`, press Enter
- **Mac**: Press `Cmd + Space`, type `Terminal`, press Enter
- **Linux**: Press `Ctrl + Alt + T`

Run the following commands:

```bash
# Clone the project (requires Git, or download ZIP from GitHub)
git clone https://github.com/aiinpocket/n3n.git
cd n3n

# Start the service (first time takes 2-5 minutes to download images)
docker compose up -d
```

> **No Git?** You can [download the ZIP file](https://github.com/aiinpocket/n3n/archive/refs/heads/main.zip) directly, extract it, then run `docker compose up -d` from the extracted folder.

> **Zero-Configuration Startup**: N3N is designed to work out-of-the-box — you don't need to configure anything:
> - Databases (PostgreSQL / Redis / MongoDB) start automatically and connect
> - JWT signing key is randomly auto-generated on first startup
> - Data encryption master key is auto-generated and securely stored
> - No environment variables required to run

### 3. Start Using

Open your browser and go to: **http://localhost:8080**

First-time setup will guide you through:
1. **Create admin account** — Enter your name, email, and password (min 8 characters, must include at least 3 of: uppercase, lowercase, digit, special character)
2. **Back up Recovery Key** — The system will display 12 English words. This is the only way to recover your encrypted data. Use the "Copy" button or write them down in a safe place
3. **Verify Recovery Key** — Enter the 12 words to confirm you have backed them up
4. Set up AI assistant (choose your AI service)
5. Create your first workflow!

> **Important**: The Recovery Key is only shown once during initial setup. If lost, encrypted credentials cannot be recovered.
>
> **Backup tips**: Store your Recovery Key in a password manager (e.g., 1Password, Bitwarden) or write it down on paper and keep it in a safe place. Do not save it as a screenshot or unencrypted text file.

---

## Setting Up AI Assistant

N3N supports multiple AI services, you can choose any of them:

| AI Service | Description | Sign Up Link |
|--------|------|---------|
| **Claude** | Anthropic's AI, excellent at analysis and reasoning | [Get API Key](https://console.anthropic.com/) |
| **ChatGPT** | OpenAI's AI, broad knowledge and coding ability | [Get API Key](https://platform.openai.com/api-keys) |
| **Gemini** | Google's AI, multimodal support | [Get API Key](https://aistudio.google.com/apikey) |
| **Ollama** | Run locally, free and private | [Download Ollama](https://ollama.com/download) |

> **Tip**: If you don't want to pay, you can choose Ollama to run AI on your own computer, completely free!

---

## FAQ

### How to verify Docker is installed?

```bash
docker --version
# Should show something like: Docker version 24.0.6
```

If there's no response, Docker is not installed or not running. Windows/Mac users should start the Docker Desktop application first.

### How to verify the service is running?

```bash
docker compose ps
# Should show app, postgres, redis etc. with status "running"
```

### What if startup fails?

Make sure Docker is running, then retry:
```bash
docker compose down
docker compose up -d
```

### Port already in use?

If you see a `port 8080 is already in use` error, another program is using port 8080. You can:
```bash
# Start with a different port (e.g., 9090)
N3N_PORT=9090 docker compose up -d
# Then open http://localhost:9090
```

### How to stop the service?

```bash
docker compose down
```

### How to update to the latest version?

```bash
git pull
docker compose down
docker compose up -d --build
```

### How to access the database (for development)?

Internal services (PostgreSQL, Redis, MongoDB) do not expose ports by default. To access them directly:

```bash
# Method 1: Use Docker exec
docker compose exec postgres psql -U n3n

# Method 2: Use development mode (exposes all ports)
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d
```

---

## Features

- **AI Workflow Generation** - Describe in natural language, AI creates the workflow for you
- **Visual Editor** - Drag and drop interface, intuitive adjustments
- **Error Handling Routes** - Visually distinguish normal flow and error handling paths (green/red/blue connections)
- **Real-time Monitoring** - See every step of workflow execution with live node status updates
- **Webhook Triggers** - Let external systems (GitHub, Slack, etc.) automatically trigger workflows
- **Flow Template Library** - Official templates with a dedicated page for browsing, searching, creating templates from existing flows, and sharing across the platform
- **Execution Approval System** - Workflows pause at approval nodes; approvers can approve or reject with comments from the Execution detail page
- **Skills System** - Built-in automation skills, no extra setup needed
- **Secure Storage** - Your API keys and passwords are protected with AES-256 encryption
- **Custom Docker Tools** - Pull tool containers from Docker Hub, auto-register as flow nodes, with featured recommendations and community ratings
- **Approval Dashboard** - Dedicated page to manage all pending approval items, approve or reject with one click
- **System Housekeeping** - Admins can view statistics and manually trigger cleanup of expired execution records
- **Device Management** - Connect a local agent to let workflows control your computer
- **Flow Sharing & Collaboration** - Share flows with team members, supporting view/edit permission control
- **OAuth2 Integration** - Third-party OAuth2 service connections for simplified authentication
- **Real-time Log Viewer** - Admins can stream system logs in real-time via SSE
- **System Monitoring Dashboard** - Live JVM memory, CPU, and flow execution statistics
- **Flow Validation** - Built-in validation button in editor toolbar to check DAG structure before publishing
- **Schedule Management** - Cron-based flow triggers with pause/resume/trigger-now support (Quartz integration)
- **Webhook Testing** - Test triggers directly from the Webhook management page with instant flow activation confirmation
- **Plugin Ratings** - Community ratings and reviews for custom Docker tools in the plugin marketplace
- **Save as Template** - One-click conversion of existing flow versions into reusable templates
- **Form Triggers** - Create public forms that trigger workflow execution on submission, no login required
- **Cloud Encrypted Backup** - Auto-encrypt and back up flows, credentials, settings to S3/GCS/R2/SFTP, restore via Recovery Key
- **Multi-language Support** - Full English, Traditional Chinese, and Japanese interface (2,400+ translation keys)
- **OpenAPI Documentation** - Built-in Swagger UI with 260+ fully documented API endpoints
- **Keyboard Shortcuts** - 16 editor shortcuts (save, publish, undo, AI assistant, etc.) with complete help modal
- **Smart Search & Filter** - Credentials, components, approvals, and executions support instant search and category filtering
- **Column Sorting** - Flow, execution, and scheduler tables support sorting by name, time, and status
- **Dashboard Deep Links** - Stat cards are clickable and navigate to filtered list pages
- **External Service Management** - Import OpenAPI specs, auto-generate API call nodes, and manage connections
- **Component Registry** - Custom component version control with activate/deprecate/rollback management
- **Activity History Tracking** - Log user actions, webhook triggers, logins, and other events for security auditing
- **Gateway Pairing Management** - Generate pairing codes to connect local agents, with real-time WebSocket communication

### Error Handling Routes

N3N supports three connection types to clearly distinguish normal flow and error handling:

| Connection Type | Color | Description |
|----------------|-------|-------------|
| **Success Path** | Green | Route taken when node executes successfully |
| **Error Path** | Red dashed | Route taken when node execution fails |
| **Always Execute** | Blue | Executes regardless of success or failure |

Click any connection in the flow editor to set its type.

---

## Custom Docker Tools

N3N lets you pull tool containers from Docker Hub and auto-register them as available flow nodes:

### How to Use

1. Go to the "Custom Docker Tools" page
2. Enter a Docker Hub image name (e.g., `n3n/tool-slack`)
3. Click "Pull" — the system downloads and registers it automatically
4. Set up the corresponding credentials (API keys, etc.)
5. The new node is now available in the flow editor

---

## Local Agent

Want workflows to control your computer? Install the local agent:

### Download Agent

| OS | Download | Description |
|---------|---------|-------------|
| Windows | [GitHub Release](https://github.com/aiinpocket/n3n/releases) | .NET 8 self-contained executable |
| macOS | [GitHub Release](https://github.com/aiinpocket/n3n/releases) | Swift application (Apple Silicon) |

### Agent Features

- **File Operations** - Read, write, copy, move files
- **Clipboard** - Read and set clipboard content
- **Desktop Notifications** - Show system notifications
- **Application Launch** - Open local applications
- **Screenshots** - Capture screen images

### Pairing Process

1. In the N3N web interface, go to "Device Management"
2. Click "Add Device" to get a 6-digit pairing code
3. Enter the pairing code in the agent
4. Once paired, you can use local nodes in your workflows

### Security

- **X25519 ECDH** - End-to-end encrypted key exchange
- **AES-256-GCM** - All commands are encrypted in transit
- **Pairing Code Verification** - Ensures only you can pair devices
- **Secure Credential Storage** - Windows uses Credential Manager, macOS uses Keychain

---

## Environment Variables (Optional)

N3N uses a **zero-configuration design** — all settings have sensible defaults. The following environment variables are only needed for special requirements:

### Database Connection (External Database)

| Variable | Default | Description |
|----------|---------|-------------|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/n3n` | PostgreSQL connection string |
| `DATABASE_USERNAME` | `n3n` | Database username |
| `DATABASE_PASSWORD` | `n3n` | Database password |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_PASSWORD` | (empty) | Redis password (automatically enables authentication when set) |

### Security

| Variable | Default | Description |
|----------|---------|-------------|
| `JWT_SECRET` | Auto-generated | JWT signing key (must be unified for cluster deployment) |
| `N3N_MASTER_KEY` | Auto-generated | Data encryption master key (**required in production**) |
| `ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:8080` | CORS allowed origins |

> **Production Note**: `N3N_MASTER_KEY` is auto-generated in development and persisted to `/data/keys/master.key`. In production (`SPRING_PROFILES_ACTIVE=prod`), it **must be set manually** or the application will refuse to start. Generate with: `openssl rand -base64 32`

### Container Orchestration (Plugin System)

| Variable | Default | Description |
|----------|---------|-------------|
| `ORCHESTRATOR_TYPE` | `docker` | Container engine (`docker` / `kubernetes` / `auto`) |
| `K8S_NAMESPACE` | `n3n` | Kubernetes main namespace |
| `K8S_PLUGIN_NAMESPACE` | `n3n-plugins` | Kubernetes plugin namespace |
| `K8S_SERVICE_ACCOUNT` | `n3n-plugin-manager` | Kubernetes service account |

> **Auto mode**: On startup, the system auto-detects the environment (K8s Service Account → Docker Socket → Docker CLI) and selects the appropriate container engine.

### AI Flow Optimizer (Enabled by Default)

| Variable | Default | Description |
|----------|---------|-------------|
| `FLOW_OPTIMIZER_ENABLED` | `true` | Local AI optimizer (enabled by default) |
| `FLOW_OPTIMIZER_URL` | `http://flow-optimizer:8081` | Optimizer service URL |

The local AI optimizer starts automatically with `docker compose up -d`, no extra setup or API keys required.

> **Note**: The local AI optimizer runs on your machine. First startup requires downloading the model (~2.3GB) and at least 4GB of memory.

---

## Advanced Information

If you're a developer and want to learn technical details, please refer to [TECHNICAL.md](TECHNICAL.md).

---

## License

Apache License 2.0 - See [LICENSE](LICENSE)
