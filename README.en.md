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

Open Terminal and run the following commands:

```bash
# Clone the project
git clone https://github.com/aiinpocket/n3n.git
cd n3n

# Start the service (first time may take a few minutes)
docker compose up -d
```

> **Zero-Configuration Startup**: N3N is designed to work out-of-the-box with sensible defaults:
> - PostgreSQL / Redis start automatically and connect
> - JWT secret key is auto-generated on first startup
> - No environment variables required to run

### 3. Start Using

Open your browser and go to: **http://localhost:8080**

First-time setup will guide you through:
1. Creating an admin account
2. Setting up AI assistant (choose your AI service)
3. Creating your first workflow!

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

### What if startup fails?

Make sure Docker is running, then retry:
```bash
docker compose down
docker compose up -d
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

---

## Features

- **AI Workflow Generation** - Describe in natural language, AI creates the workflow for you
- **Visual Editor** - Drag and drop interface, intuitive adjustments
- **Error Handling Routes** - Visually distinguish normal flow and error handling paths (green/red/blue connections)
- **Real-time Monitoring** - See every step of workflow execution with live node status updates
- **Webhook Triggers** - Let external systems (GitHub, Slack, etc.) automatically trigger workflows
- **58+ Official Templates** - Includes scheduling, notifications, data processing, AI, monitoring, approvals, integrations, web scraping, and more
- **Skills System** - Built-in automation skills, no extra setup needed
- **Secure Storage** - Your API keys and passwords are protected with AES-256 encryption
- **Custom Docker Tools** - Pull tool containers from Docker Hub, auto-register as flow nodes
- **Device Management** - Connect a local agent to let workflows control your computer

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

### Security

| Variable | Default | Description |
|----------|---------|-------------|
| `JWT_SECRET` | Auto-generated | JWT signing key (must be unified for cluster deployment) |
| `ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:8080` | CORS allowed origins |

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
