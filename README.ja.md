# N3N Flow Platform

[English](README.en.md) | 日本語 | [繁體中文](README.md)

> 言葉で説明するだけで自動化ワークフローを作成 - AIがあなたのアイデアを実行可能なワークフローに変換

> **初めてですか？** [クイックスタートガイド](QUICK_START.ja.md)を読んで、3 分で起動しましょう。

---

## ハードウェア要件

N3Nには複数のサービスが組み込まれています。使用状況に応じて適切なハードウェアを選択してください：

### 最低要件（基本動作）

| 項目 | スペック |
|------|---------|
| **CPU** | 2コア |
| **メモリ** | 4 GB |
| **ディスク** | 10 GB SSD |
| **OS** | Windows 10/11、macOS 12+、Ubuntu 20.04+ |

> 対象：個人利用、シンプルなフロー、AI機能なし

### 推奨要件（クラウドAI利用）

| 項目 | スペック |
|------|---------|
| **CPU** | 4コア |
| **メモリ** | 8 GB |
| **ディスク** | 20 GB SSD |
| **ネットワーク** | 安定したインターネット接続 |

> 対象：日常利用、中程度の複雑さのフロー、OpenAI/Claude/GeminiなどクラウドAIの利用

### 組み込みサービスのリソース使用量

| サービス | メモリ | 説明 |
|---------|--------|------|
| **N3N App** | ~512 MB | Spring Bootアプリケーション |
| **PostgreSQL** | ~256 MB | リレーショナルデータベース |
| **Redis** | ~128 MB | キャッシュと実行状態 |
| **MongoDB** | ~256 MB | NoSQLデータベース（ワークフローノードで使用） |

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

ターミナルを開いてください：
- **Windows**: `Win + R` を押し、`cmd` と入力してEnter
- **Mac**: `Cmd + Space` を押し、`Terminal` と入力してEnter
- **Linux**: `Ctrl + Alt + T` を押す

以下のコマンドを実行してください：

```bash
# プロジェクトをクローン（Gitが必要、またはGitHubからZIPをダウンロードして解凍）
git clone https://github.com/aiinpocket/n3n.git
cd n3n

# サービスを起動（初回はDockerイメージとAIモデルのダウンロードに10〜30分かかる場合があります）
docker compose up -d
```

> **Gitがない場合**：[ZIPファイルをダウンロード](https://github.com/aiinpocket/n3n/archive/refs/heads/main.zip)して解凍し、そのフォルダーで `docker compose up -d` を実行してください。

> **ゼロ設定で起動**：N3Nはすぐに使えるよう設計されています。手動での設定は一切不要です：
> - データベース（PostgreSQL / Redis / MongoDB）は自動的に起動・接続されます
> - JWT署名鍵は初回起動時にランダムに自動生成されます
> - データ暗号化マスターキーは自動生成され、安全に保存されます
> - 環境変数の設定は不要です

### 3. 使い始める

> **初回起動には時間がかかります**：初回はDockerイメージのダウンロードが必要なため、ネットワーク速度に応じて数分かかることがあります。2回目以降の再起動は60〜90秒で完了します。以下のコマンドで進捗を確認できます：
> ```bash
> # 起動状況をリアルタイムで確認
> docker compose logs -f app
> # 全コンテナのステータス確認
> docker compose ps
> ```
> ログに `Started N3nApplication` と表示されたら、ブラウザでアクセスできます。

ブラウザを開いて、次のアドレスにアクセス：**http://localhost:8080**

初回セットアップでは以下をガイドします：
1. **管理者アカウントの作成** — 名前、メール、パスワードを入力（12〜128文字、大文字・小文字・数字・特殊文字のうち3種以上を含むこと）
2. **Recovery Key のバックアップ** — システムが12個の英単語を表示します。これは暗号化データを復元する唯一の方法です。安全な場所に書き留めるかコピーしてください
3. **Recovery Key の確認** — 先ほどの12個の単語を入力してバックアップを確認
4. AIアシスタントの設定（お持ちのAIサービスを選択）
5. 最初のワークフローを作成！

> **重要**：Recovery Key は初回セットアップ時にのみ表示されます。紛失した場合、暗号化された認証情報を復元できません。
>
> **バックアップのヒント**：Recovery Key はパスワードマネージャー（1Password、Bitwardenなど）に保存するか、紙に書き留めて安全な場所に保管してください。スクリーンショットや暗号化されていないテキストファイルでの保存はお避けください。

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

### Dockerがインストールされているか確認するには？

```bash
docker --version
# Docker version 24.0.6 のように表示されるはずです
```

応答がない場合、Dockerがインストールされていないか起動していません。Windows/Macユーザーは先にDocker Desktopアプリケーションを起動してください。

### サービスが起動しているか確認するには？

```bash
docker compose ps
# app、postgres、redisなどのコンテナがrunning状態で表示されるはずです
```

### 起動に失敗した場合は？

まずログを確認して原因を特定してください：
```bash
# リアルタイムログを表示
docker compose logs -f app

# 全サービスの状態を確認
docker compose ps

# データベース移行ログを確認（DB問題の場合）
docker compose logs postgres
```

問題が解決しない場合は、再起動してください：
```bash
docker compose down
docker compose up -d
```

### ポートが使用中の場合は？

`port 8080 is already in use` というエラーが表示された場合、他のプログラムがポート8080を使用しています：
```bash
# 別のポートで起動する（例：9090）
N3N_PORT=9090 docker compose up -d
# http://localhost:9090 でアクセス
```

### サービスを停止するには？

```bash
docker compose down
```

### 最新バージョンに更新するには？

```bash
# 推奨：事前にデータベースをバックアップ（念のため）
docker compose exec postgres pg_dump -U n3n n3n > backup_$(date +%Y%m%d).sql

# 更新して再起動
git pull
docker compose down
docker compose up -d --build
```

> **データの安全性**：更新は通常データに影響しません（Flywayがデータベース移行を自動処理します）が、バックアップは常に良い習慣です。

### データベースにアクセスするには（開発用）？

内部サービス（PostgreSQL、Redis、MongoDB）はデフォルトでポートを公開しません。直接アクセスするには：

```bash
# 方法1：Docker exec を使用
docker compose exec postgres psql -U n3n

# 方法2：開発モードを使用（全ポート公開）
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d
```

### データベースバックアップの復元方法

```bash
# SQLバックアップからの復元
docker compose exec -T postgres psql -U n3n n3n < backup_20260216.sql
```

### Docker Desktop が起動していない場合

`docker compose up` で `Cannot connect to the Docker daemon` エラーが出る場合：

- **Windows / Mac**: Docker Desktop アプリを先に起動し、システムトレイアイコンが「Running」になるまで待つ
- **Linux**: `sudo systemctl start docker` を実行

### ディスク容量不足の場合

初回起動時は約 3-4 GB のイメージをダウンロードします。空き容量を確認：

```bash
# Mac/Linux
df -h .
# Windows (PowerShell)
Get-PSDrive C
```

容量不足の場合、古い Docker イメージを削除：`docker system prune -a`

---

## 本番デプロイセキュリティチェックリスト

N3Nを公開環境にデプロイする前に、以下のセキュリティ設定を完了してください：

```bash
# 1. 環境変数テンプレートをコピー
cp .env.example .env

# 2. .envを編集して以下を設定
```

| 設定項目 | 説明 | 例 |
|----------|------|-----|
| `POSTGRES_PASSWORD` | DBパスワード（デフォルトの`n3n`を変更） | ランダムパスワード |
| `REDIS_PASSWORD` | Redisパスワード（デフォルト：なし） | ランダムパスワード |
| `ALLOWED_ORIGINS` | ドメイン名（localhostを変更） | `https://n3n.example.com` |
| `N3N_PORT` | 公開ポート | `8080`（デフォルト） |

> **パスワード生成**: `openssl rand -base64 24` でランダムパスワードを生成できます。

### HTTPSリバースプロキシ設定

N3NはデフォルトでHTTPを提供します。本番環境ではリバースプロキシでHTTPSを有効にしてください：

**Caddy使用（最も簡単、自動HTTPS）：**

```bash
# Caddyインストール後、Caddyfileを作成
echo 'n3n.example.com {
  reverse_proxy localhost:8080
}' > Caddyfile

caddy start
```

**Nginx使用：**

```nginx
server {
    listen 443 ssl;
    server_name n3n.example.com;

    ssl_certificate /etc/letsencrypt/live/n3n.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/n3n.example.com/privkey.pem;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        # WebSocket対応
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

> リバースプロキシ設定後、`.env`の`ALLOWED_ORIGINS=https://n3n.example.com`を更新してください。

---

## 機能特徴

- **AIワークフロー生成** - 自然言語で説明すれば、AIがワークフローを作成
- **ビジュアルエディター** - ドラッグ＆ドロップで直感的に調整
- **エラーハンドリングルート** - 正常フローとエラー処理パスを視覚的に区別（緑/赤/青の接続線）
- **リアルタイム監視** - ワークフロー実行の各ステップをリアルタイムで確認
- **Webhookトリガー** - 外部システム（GitHub、Slackなど）からワークフローを自動起動
- **フローテンプレートライブラリ** - 公式テンプレートを専用ページで閲覧・検索、既存フローからテンプレートを作成しプラットフォーム全体で共有可能
- **実行承認システム** - 承認ノードでワークフローが一時停止し、承認者がコメント付きで承認または却下可能
- **スキルシステム** - 内蔵の自動化スキル、追加設定不要
- **安全なストレージ** - APIキーとパスワードはAES-256で暗号化して保存
- **カスタムDockerツール** - 90以上の内蔵ツールノードに加え、Docker Hubから追加ツールコンテナをプルしてフローノードとして自動登録
- **承認ダッシュボード** - 専用ページで全ての承認待ちアイテムを一元管理、ワンクリックで承認・却下
- **システムクリーンアップ** - 管理者が統計を確認し、期限切れの実行履歴を手動クリーンアップ可能
- **デバイス管理** - ローカルエージェントを接続して、ワークフローからPCを制御
- **フロー共有とコラボレーション** - チームメンバーとフローを共有、閲覧/編集権限をサポート
- **OAuth2連携** - サードパーティOAuth2サービス接続による認証の簡素化
- **リアルタイムログビューアー** - 管理者がSSEでシステムログをリアルタイムにストリーミング
- **システム監視ダッシュボード** - JVMメモリ、CPU、フロー実行統計をリアルタイムで確認
- **フロー検証** - エディターツールバーの検証ボタンでDAG構造を公開前に即時チェック
- **スケジュール管理** - Cronスケジュールでフローをトリガー、一時停止/再開/即時実行に対応（Quartz統合）
- **Webhookテスト** - Webhook管理ページから直接テストトリガーを実行、フロー連動を即時確認
- **プラグイン評価** - カスタムツールマーケットプレイスでコミュニティ評価とレビューに対応
- **テンプレートとして保存** - 既存フローバージョンをワンクリックで再利用可能なテンプレートに変換
- **フォームトリガー** - 公開フォームを作成し、送信時にワークフローを自動実行（ログイン不要）
- **クラウド暗号化バックアップ** - フロー、認証情報、設定をS3/GCS/R2/SFTPに自動暗号化バックアップ、リカバリーキーで復元
- **多言語サポート** - 英語、繁体字中国語、日本語の完全なインターフェース（2,400+の翻訳キー）
- **OpenAPIドキュメント** - 260+のAPIエンドポイントを完全にドキュメント化したSwagger UI内蔵
- **キーボードショートカット** - 16種のエディターショートカット（保存、公開、元に戻す、AIアシスタント等）と完全なヘルプモーダル
- **スマート検索＆フィルター** - 認証情報、コンポーネント、承認、実行リストでの即時検索とカテゴリフィルタリング
- **カラムソート** - フロー、実行、スケジューラテーブルで名前、時間、ステータスによるソート
- **ダッシュボードディープリンク** - 統計カードをクリックしてフィルタリングされたリストページに遷移
- **外部サービス管理** - OpenAPIスペックをインポートし、APIコールノードを自動生成して接続を管理
- **コンポーネントレジストリ** - カスタムコンポーネントのバージョン管理（有効化/非推奨/ロールバック）
- **アクティビティ履歴** - ユーザー操作、Webhookトリガー、ログインなどをセキュリティ監査用に記録
- **ゲートウェイペアリング管理** - ペアリングコードを生成してローカルエージェントを接続、リアルタイムWebSocket通信

### エラーハンドリングルート

N3Nは3種類の接続タイプをサポートし、正常フローとエラー処理を明確に区別します：

| 接続タイプ | 色 | 説明 |
|-----------|-----|------|
| **成功パス** | 緑 | ノード実行成功後のルート |
| **エラーパス** | 赤（破線） | ノード実行失敗時のルート |
| **常時実行** | 青 | 成功・失敗に関わらず実行 |

フローエディターで接続線をクリックしてタイプを設定できます。

---

## カスタムDockerツール

N3NではDocker Hubからツールコンテナをプルし、利用可能なフローノードとして自動登録できます：

### 使い方

1. 「カスタムDockerツール」ページにアクセス
2. Docker Hubのイメージ名を入力（例：`n3n/tool-slack`）
3. 「プル」をクリック — システムが自動でダウンロードして登録
4. 対応する認証情報（APIキーなど）を設定
5. フローエディターで新しいノードとして利用可能に

---

## ローカルエージェント

ワークフローからPCを制御したい場合、ローカルエージェントをインストールしてください：

### エージェントのダウンロード

| OS | ダウンロード | 説明 |
|---------|---------|------|
| Windows | [GitHub Release](https://github.com/aiinpocket/n3n/releases) | .NET 8 自己完結型実行ファイル |
| macOS | [GitHub Release](https://github.com/aiinpocket/n3n/releases) | Swiftアプリケーション（Apple Silicon） |

### エージェント機能

- **ファイル操作** - ファイルの読み取り、書き込み、コピー、移動
- **クリップボード** - クリップボード内容の読み取りと設定
- **デスクトップ通知** - システム通知の表示
- **アプリケーション起動** - ローカルアプリケーションの起動
- **スクリーンショット** - 画面キャプチャ

### ペアリング手順

1. N3Nのウェブインターフェースで「デバイス管理」に移動
2. 「デバイスを追加」をクリックして6桁のペアリングコードを取得
3. エージェントにペアリングコードを入力
4. ペアリング完了後、フローでローカルノードが利用可能に

### セキュリティ

- **X25519 ECDH** - エンドツーエンド暗号化鍵交換
- **AES-256-GCM** - すべてのコマンドを暗号化して送信
- **ペアリングコード認証** - 自分だけがデバイスをペアリングできることを保証
- **認証情報の安全な保存** - WindowsはCredential Manager、macOSはKeychainを使用

---

## 環境変数（オプション）

N3Nは**ゼロ設定設計**を採用しており、すべての設定にはデフォルト値があります。以下の環境変数は特別な要件がある場合のみ設定してください：

### データベース接続（外部データベース）

| 変数 | デフォルト値 | 説明 |
|------|-----------|------|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/n3n` | PostgreSQL接続文字列 |
| `DATABASE_USERNAME` | `n3n` | データベースユーザー名 |
| `DATABASE_PASSWORD` | `n3n` | データベースパスワード |
| `REDIS_HOST` | `localhost` | Redisホスト |
| `REDIS_PORT` | `6379` | Redisポート |
| `REDIS_PASSWORD` | （空） | Redisパスワード（設定すると自動的に認証が有効化） |
| `MONGO_USER` | `n3n_admin` | MongoDBユーザー名 |
| `MONGO_PASSWORD` | `n3n_dev_only` | MongoDBパスワード |
| `MONGO_DB` | `n3n_test` | MongoDBデータベース名 |

### セキュリティ

| 変数 | デフォルト値 | 説明 |
|------|-----------|------|
| `JWT_SECRET` | 自動生成 | JWT署名鍵（クラスターデプロイ時に統一設定が必要） |
| `N3N_MASTER_KEY` | 自動生成 | データ暗号化マスターキー（**本番環境では必須設定**） |
| `ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:8080` | CORS許可オリジン |

> **本番環境の注意**：`N3N_MASTER_KEY` は開発環境では自動生成され `/data/keys/master.key` に保存されますが、本番環境（`SPRING_PROFILES_ACTIVE=prod`）では**手動設定が必須**です。設定しない場合、アプリケーションは起動を拒否します。生成方法：`openssl rand -base64 32`

### コンテナオーケストレーション（プラグインシステム）

| 変数 | デフォルト値 | 説明 |
|------|-----------|------|
| `ORCHESTRATOR_TYPE` | `docker` | コンテナエンジン（`docker` / `kubernetes` / `auto`） |
| `K8S_NAMESPACE` | `n3n` | Kubernetesメイン名前空間 |
| `K8S_PLUGIN_NAMESPACE` | `n3n-plugins` | Kubernetesプラグイン名前空間 |
| `K8S_SERVICE_ACCOUNT` | `n3n-plugin-manager` | Kubernetesサービスアカウント |

> **autoモード**：起動時にシステムが環境を自動検出（K8s Service Account → Docker Socket → Docker CLI）し、適切なコンテナエンジンを選択します。

### AIプロバイダー（クラウド）

N3NのAI機能（フロー生成、マルチモーダルノード、フロー最適化）はクラウドAIプロバイダーを使用します。
「認証情報」または「AI設定」ページでAPIキーを入力してください：

| プロバイダー | 用途 | 残高照会 |
|------------|------|---------|
| OpenRouter | 統一ゲートウェイ。1つのキーで100以上のモデル（Claude / GPT / Gemini） | 対応（公式API） |
| OpenAI | チャット、画像理解、TTS、画像生成 | ローカル推定 |
| Anthropic (Claude) | チャット、フロー生成 | ローカル推定 |
| Google (Gemini) | チャット、マルチモーダル | ローカル推定 |
| fal.ai | AI画像生成（FLUX）、AI動画生成（Veo / Kling / Hailuo） | 対応（公式API） |
| ElevenLabs | AI音声合成 | 対応（文字クォータ） |

「AI残高管理」ページですべてのプロバイダーの残高と使用量を一括確認できます。


### Google ログイン（任意）

| 変数 | デフォルト | 説明 |
|------|-----------|------|
| `GOOGLE_OAUTH_CLIENT_ID` | （空、無効） | Google OAuth Client ID。設定するとログインページに「Google でログイン」ボタンが表示され、初回ログインで自動的にアカウントが作成されます |

> [Google Cloud Console](https://console.cloud.google.com/apis/credentials) で OAuth Client ID（Web application）を作成し、Authorized JavaScript origins にサイトのオリジンを追加してください。redirect URI は不要です。

### 成果物ライブラリ（生成ファイルの保存）

フローが生成した AI 動画・音声・画像・ドキュメントは各ユーザー専用の成果物ライブラリに保存され、「成果物」ページでプレビュー・ダウンロード・削除できます。

| 変数 | デフォルト | 説明 |
|------|-----------|------|
| `ARTIFACT_STORAGE_PATH` | `./data/artifacts` | 成果物の保存パス（Docker では volume をマウント） |
| `ARTIFACT_MAX_FILE_SIZE_MB` | `512` | ファイルごとのサイズ上限（MB） |

### スケジュールの自動登録

フローに「スケジュールトリガー」ノードが含まれる場合、フローの公開時に自動的にスケジュールが登録されます。ブラウザを閉じてもサーバー側で実行され続けます。

---

## 詳細情報

開発者で技術的な詳細を知りたい場合は、[TECHNICAL.md](TECHNICAL.md)をご覧ください。

---

## ライセンス

Apache License 2.0 - [LICENSE](LICENSE)を参照
