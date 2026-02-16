# N3N クイックスタートガイド

[English](QUICK_START.en.md) | 日本語 | [繁體中文](QUICK_START.md)

> 3 分で最初の AI 自動化ワークフローを起動

---

## 前提条件

- [Docker Desktop](https://www.docker.com/products/docker-desktop/)（Windows/Mac）または [Docker Engine](https://docs.docker.com/engine/install/)（Linux）がインストール済み
- 最低 4 GB メモリ、10 GB ディスク容量

Docker がインストールされ、起動していることを確認：
```bash
docker --version
# Docker version 24.x 以降が表示されること
```

---

## ステップ 1：N3N をダウンロード

**方法 A：Git を使用**
```bash
git clone https://github.com/aiinpocket/n3n.git
cd n3n
```

**方法 B：ZIP をダウンロード**

[ZIP をダウンロード](https://github.com/aiinpocket/n3n/archive/refs/heads/main.zip) → 解凍 → `n3n` フォルダを開く

---

## ステップ 2：サービスを起動

```bash
docker compose up -d
```

初回起動時はコンテナイメージのダウンロード（約 2.3 GB）が必要で、ネットワーク速度に応じて 10〜30 分かかります。
以降の再起動は 60〜90 秒です。

**起動状況を確認：**
```bash
docker compose logs -f app
```

`Started N3nApplication` と表示されたら、サービスの準備完了です。

---

## ステップ 3：使い始める

ブラウザで **http://localhost:8080** にアクセス

### 初期設定（初回のみ）

1. **管理者アカウント作成** — 名前、メール、パスワードを入力
2. **Recovery Key のバックアップ** — 12 個の英単語が表示されるので、必ずコピーして保存
3. **Recovery Key の検証** — 12 個の単語を入力してバックアップを確認
4. **AI アシスタント設定**（任意） — AI サービスの API キーを入力
5. ワークフロー作成開始！

> **重要**：Recovery Key は一度しか表示されません。紛失すると暗号化された認証情報を復元できません。パスワードマネージャーに保存してください。

---

## 最初のワークフローを作成

1. サイドバーの「フロー」→「新規フロー」をクリック
2. 以下のいずれかを選択：
   - **AI 生成**：AI アシスタントボタンをクリックし、自然言語でワークフローを説明
   - **手動作成**：左パネルからノードをキャンバスにドラッグ
3. ノードを接続 → パラメータ設定 → 「公開」をクリック
4. 「実行」をクリックしてテスト

---

## AI アシスタントの設定（任意）

「設定」→「AI 設定」で AI サービスを選択：

| AI サービス | 料金 | 申し込み |
|------------|------|---------|
| **Claude** | 有料 | [console.anthropic.com](https://console.anthropic.com/) |
| **ChatGPT** | 有料 | [platform.openai.com](https://platform.openai.com/api-keys) |
| **Gemini** | 有料 | [aistudio.google.com](https://aistudio.google.com/apikey) |
| **Ollama** | 無料（ローカル） | [ollama.com](https://ollama.com/download) |

> 無料で使いたい？Ollama を使えばローカルで AI を実行できます！

---

## よく使うコマンド

| 操作 | コマンド |
|------|---------|
| サービス起動 | `docker compose up -d` |
| サービス停止 | `docker compose down` |
| 状態確認 | `docker compose ps` |
| ログ表示 | `docker compose logs -f app` |
| アップデート | `git pull && docker compose down && docker compose up -d --build` |

---

## トラブルシューティング

### Docker Desktop が起動していない
- **Windows/Mac**：Docker Desktop を開き、システムトレイアイコンが「Running」と表示されるまで待つ
- **Linux**：`sudo systemctl start docker` を実行

### ポート 8080 が使用中
```bash
N3N_PORT=9090 docker compose up -d
# http://localhost:9090 を使用
```

### メモリ不足
`.env` に `FLOW_OPTIMIZER_ENABLED=false` を設定すると、2〜4 GB のメモリを節約できます。

---

## 次のステップ

- [機能一覧](README.ja.md) — すべての機能を確認
- [デプロイガイド](docs/DEPLOYMENT.md) — 本番環境のデプロイ
- [技術ドキュメント](TECHNICAL.md) — API リファレンスとアーキテクチャ
- [コントリビューションガイド](CONTRIBUTING.md) — 開発への参加
