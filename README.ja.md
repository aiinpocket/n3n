# N3N Flow Platform

[English](README.en.md) | 日本語 | [繁體中文](README.md)

> 言葉で説明するだけで自動化ワークフローを作成 - AIがあなたのアイデアを実行可能なワークフローに変換

---

## これは何ですか？

N3Nは**ビジュアルワークフロー自動化プラットフォーム**です：

- **自然言語で説明**するだけで、AIアシスタントがワークフローを生成
- **ドラッグ＆ドロップ**でフローチャートを調整、コーディング不要
- **外部サービス**（API、データベースなど）に接続して日常業務を自動化

**プログラミングができなくてもワークフローを自動化したい人**、**計画を立てるのが好きな人**にも最適です。

---

## クイックスタート

### 1. Dockerをインストール

まだDockerをお持ちでない場合は、先にインストールしてください：

| OS | ダウンロードリンク |
|---------|---------|
| Windows | [Docker Desktop for Windows](https://www.docker.com/products/docker-desktop/) |
| Mac | [Docker Desktop for Mac](https://www.docker.com/products/docker-desktop/) |
| Linux | [Docker Engine](https://docs.docker.com/engine/install/) |

### 2. N3Nを起動

ターミナルを開いて、以下のコマンドを実行してください：

```bash
# プロジェクトをクローン
git clone https://github.com/aiinpocket/n3n.git
cd n3n

# サービスを起動（初回は数分かかる場合があります）
docker compose up -d
```

### 3. 使い始める

ブラウザを開いて、次のアドレスにアクセス：**http://localhost:8080**

初回セットアップでは以下をガイドします：
1. 管理者アカウントの作成
2. AIアシスタントの設定（お持ちのAIサービスを選択）
3. 最初のワークフローを作成！

---

## AIアシスタントの設定

N3Nは複数のAIサービスをサポートしています。お好みのものを選べます：

| AIサービス | 説明 | 申請リンク |
|--------|------|---------|
| **Claude** | AnthropicのAI、分析と推論に優れる | [APIキーを取得](https://console.anthropic.com/) |
| **ChatGPT** | OpenAIのAI、幅広い知識とコーディング能力 | [APIキーを取得](https://platform.openai.com/api-keys) |
| **Gemini** | GoogleのAI、マルチモーダル対応 | [APIキーを取得](https://aistudio.google.com/apikey) |
| **Ollama** | ローカル実行、無料でプライベート | [Ollamaをダウンロード](https://ollama.com/download) |

> **ヒント**：有料サービスを使いたくない場合は、Ollamaを選んで自分のPCでAIを実行できます。完全無料！

---

## よくある質問

### 起動に失敗した場合は？

Dockerが実行中であることを確認してから、再試行してください：
```bash
docker compose down
docker compose up -d
```

### サービスを停止するには？

```bash
docker compose down
```

### 最新バージョンに更新するには？

```bash
git pull
docker compose down
docker compose up -d --build
```

---

## 機能特徴

- **AIワークフロー生成** - 自然言語で説明すれば、AIがワークフローを作成
- **ビジュアルエディター** - ドラッグ＆ドロップで直感的に調整
- **リアルタイム監視** - ワークフロー実行の各ステップを確認
- **安全なストレージ** - APIキーとパスワードは暗号化されて保存

---

## 詳細情報

開発者で技術的な詳細を知りたい場合は、[TECHNICAL.md](TECHNICAL.md)をご覧ください。

---

## ライセンス

Apache License 2.0 - [LICENSE](LICENSE)を参照
