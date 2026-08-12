# N3N Quick Start Guide

English | [日本語](QUICK_START.ja.md) | [繁體中文](QUICK_START.md)

> Get your first AI-powered workflow running in 3 minutes

---

## Prerequisites

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (Windows/Mac) or [Docker Engine](https://docs.docker.com/engine/install/) (Linux) installed
- At least 4 GB RAM, 10 GB disk space

Verify Docker is installed and running:
```bash
docker --version
# Should show Docker version 24.x or newer
```

---

## Step 1: Download N3N

**Option A: Using Git**
```bash
git clone https://github.com/aiinpocket/n3n.git
cd n3n
```

**Option B: Download ZIP**

[Download ZIP](https://github.com/aiinpocket/n3n/archive/refs/heads/main.zip) → Extract → Open the `n3n` folder

---

## Step 2: Start the Service

```bash
docker compose up -d
```

First launch downloads container images (~2.3 GB) and may take 10-30 minutes depending on your network speed. Subsequent restarts take only 60-90 seconds.

**Track startup progress:**
```bash
docker compose logs -f app
```

When you see `Started N3nApplication`, the service is ready.

---

## Step 3: Start Using N3N

Open your browser and go to **http://localhost:8080**

### Initial Setup (First Time Only)

1. **Create admin account** — Enter name, email, and password
2. **Back up Recovery Key** — The system shows 12 English words; copy and save them securely
3. **Verify Recovery Key** — Enter the 12 words to confirm your backup
4. **Set up AI assistant** (optional) — Enter your AI service API key
5. Start building workflows!

> **Important**: The Recovery Key is shown only once. If lost, encrypted credentials cannot be recovered. Store it in a password manager.

---

## Build Your First Workflow

1. Click "Flows" in the sidebar → "New Flow"
2. Choose either:
   - **AI generation**: Click the AI assistant button and describe your workflow in natural language
   - **Manual build**: Drag nodes from the left panel onto the canvas
3. Connect nodes → Configure parameters → Click "Publish"
4. Click "Execute" to test your workflow

---

## Set Up AI Assistant (Optional)

Go to "Settings" → "AI Settings" and choose your AI service:

| AI Service | Cost | Sign Up |
|------------|------|---------|
| **Claude** | Paid | [console.anthropic.com](https://console.anthropic.com/) |
| **ChatGPT** | Paid | [platform.openai.com](https://platform.openai.com/api-keys) |
| **Gemini** | Paid | [aistudio.google.com](https://aistudio.google.com/apikey) |
| **Ollama** | Free (local) | [ollama.com](https://ollama.com/download) |

> Don't want to pay? Use Ollama to run AI locally for free!

---

## Common Operations

| Operation | Command |
|-----------|---------|
| Start service | `docker compose up -d` |
| Stop service | `docker compose down` |
| Check status | `docker compose ps` |
| View logs | `docker compose logs -f app` |
| Update | `git pull && docker compose down && docker compose up -d --build` |

---

## Troubleshooting

### Docker Desktop Not Running
- **Windows/Mac**: Open Docker Desktop and wait for the system tray icon to show "Running"
- **Linux**: Run `sudo systemctl start docker`

### Port 8080 Already in Use
```bash
N3N_PORT=9090 docker compose up -d
# Then use http://localhost:9090
```

---

## Next Steps

- [Full Feature Overview](README.en.md) — Learn about all features
- [Deployment Guide](docs/DEPLOYMENT.md) — Production deployment
- [Technical Documentation](TECHNICAL.md) — API reference and architecture
- [Contributing Guide](CONTRIBUTING.md) — Join development
