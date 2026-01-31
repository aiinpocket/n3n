# N3N Flow Platform

English | [日本語](README.ja.md) | [繁體中文](README.md)

> Build automation workflows just by describing them - Let AI turn your ideas into executable workflows

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
- **Real-time Monitoring** - See every step of workflow execution with live node status updates
- **Webhook Triggers** - Let external systems (GitHub, Slack, etc.) automatically trigger workflows
- **Skills System** - Built-in automation skills, no extra setup needed
- **Secure Storage** - Your API keys and passwords are protected with AES-256 encryption

---

## Advanced Information

If you're a developer and want to learn technical details, please refer to [TECHNICAL.md](TECHNICAL.md).

---

## License

Apache License 2.0 - See [LICENSE](LICENSE)
