# N3N Flow Platform 部署指南

本文檔說明如何在 Docker 和 Kubernetes 環境中部署 N3N Flow Platform。

---

## 目錄

- [前置需求](#前置需求)
- [Docker 部署](#docker-部署)
  - [使用 Docker Compose（開發環境）](#使用-docker-compose開發環境)
  - [使用 Docker Compose（生產環境）](#使用-docker-compose生產環境)
  - [單獨使用 Docker](#單獨使用-docker)
- [Kubernetes 部署](#kubernetes-部署)
  - [使用 Helm Chart](#使用-helm-chart)
  - [手動部署 YAML](#手動部署-yaml)
- [環境變數配置](#環境變數配置)
- [安全配置](#安全配置)
- [監控與日誌](#監控與日誌)
- [常見問題](#常見問題)

---

## 前置需求

### Docker 部署
- Docker 24.0+
- Docker Compose v2.20+
- 最少 2GB RAM
- 10GB 磁碟空間

### Kubernetes 部署
- Kubernetes 1.28+
- Helm 3.12+
- kubectl 已配置
- 最少 4GB RAM (建議 8GB+)
- 20GB 磁碟空間

---

## Docker 部署

### 使用 Docker Compose（開發環境）

開發環境使用預設的 `docker-compose.yml`，會啟動所有服務（App、Caddy、PostgreSQL、Redis、MongoDB）：

```bash
# 1. Clone 專案
git clone https://github.com/aiinpocket/n3n.git
cd n3n

# 2. 一鍵啟動所有服務
docker compose up -d

# 3. 確認服務狀態
docker compose ps

# 4. 開啟瀏覽器
open http://localhost:8080
```

> **零配置設計**：JWT Secret、Master Key 等安全金鑰皆自動產生，無需手動設定。
> 首次啟動時會引導建立管理員帳號，並顯示 Recovery Key 供備份。

**服務連線資訊：**

| 服務 | 連線位址 | 說明 |
|------|---------|------|
| N3N Web | http://localhost:8080 | 主應用程式 |
| PostgreSQL | 內部網路 (5432) | 預設不對外暴露 |
| Redis | 內部網路 (6379) | 預設不對外暴露 |

> **需要直接存取資料庫？** 使用開發覆蓋檔暴露端口：
> ```bash
> docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d
> ```
> 或使用 `docker compose exec postgres psql -U n3n` 直接進入。

### 使用 Docker Compose（生產環境）

生產環境建議加強資料庫密碼安全性。建立 `.env` 檔案：

```bash
# .env (生產環境 - 自訂密碼)
# Master Key 和 JWT Secret 會自動產生，不需要手動設定
# 如需多實例共享，可設定：
# JWT_SECRET=your-base64-encoded-32-byte-key

# 資料庫密碼（建議使用強密碼）
POSTGRES_PASSWORD=strong-db-password
REDIS_PASSWORD=strong-redis-password
```

啟動生產環境：

```bash
# 使用預設的 docker-compose.yml 啟動
docker compose up -d

# 查看日誌
docker compose logs -f app
```

> **安全提示**：`docker-compose.yml` 中的內部服務（PostgreSQL、Redis、MongoDB）預設不對外暴露端口。
> Master Key 自動產生並持久化到 `/data` 卷。Recovery Key 在首次設定時顯示。

### 單獨使用 Docker

如果只需要執行 N3N 應用程式（外部已有資料庫）：

```bash
# 建置映像檔
docker build -t n3n:latest .

# 執行容器（Master Key 會自動產生，掛載 /data 以持久化）
docker run -d \
  --name n3n \
  -p 8080:8080 \
  -v n3n_data:/data \
  -e DATABASE_URL="jdbc:postgresql://host.docker.internal:5432/n3n" \
  -e DATABASE_USERNAME="n3n" \
  -e DATABASE_PASSWORD="password" \
  -e REDIS_HOST="host.docker.internal" \
  -e REDIS_PORT="6379" \
  n3n:latest

# 查看日誌
docker logs -f n3n
```

---

## Kubernetes 部署

### 使用 Helm Chart

N3N 提供 Helm Chart 簡化 Kubernetes 部署。

#### 1. 新增 Helm Repository（如有）

```bash
# 如果有發布到 Helm 倉庫
helm repo add n3n https://aiinpocket.github.io/n3n
helm repo update
```

#### 2. 從本地部署

```bash
# Clone 專案
git clone https://github.com/aiinpocket/n3n.git
cd n3n

# 更新依賴
helm dependency update ./helm/n3n

# 檢視預設配置
helm show values ./helm/n3n

# 建立 namespace
kubectl create namespace n3n

# 建立 secrets
# 注意：多實例部署（replicas > 1）必須設定 master-key 和 jwt-secret
#    以確保所有 Pod 使用相同的加密密鑰
kubectl create secret generic n3n-secrets \
  --namespace n3n \
  --from-literal=db-password="$(openssl rand -base64 24)" \
  --from-literal=redis-password="$(openssl rand -base64 24)" \
  --from-literal=master-key="$(openssl rand -base64 32)" \
  --from-literal=jwt-secret="$(openssl rand -base64 64)"
```

#### 3. 安裝 Chart

**開發環境（內建 PostgreSQL + Redis）：**

```bash
helm install n3n ./helm/n3n \
  --namespace n3n
```

**生產環境（外部資料庫）：**

建立 `values-prod.yaml`：

```yaml
replicaCount: 3

image:
  repository: ghcr.io/aiinpocket/n3n
  tag: "1.0.0"

# 使用外部資料庫
database:
  external: true
  host: "your-postgres-host.rds.amazonaws.com"
  port: 5432
  name: n3n
  username: n3n_prod
  password: ""  # 使用 Secret 管理
  poolSize: 20
  minIdle: 5

redis:
  external: true
  host: "your-redis-host.cache.amazonaws.com"
  port: 6379
  password: ""  # 使用 Secret 管理

# 停用內建資料庫
postgresql:
  enabled: false

redis:
  enabled: false

# 資源配置
resources:
  limits:
    cpu: 2000m
    memory: 2Gi
  requests:
    cpu: 500m
    memory: 1Gi

# 自動擴展
autoscaling:
  enabled: true
  minReplicas: 3
  maxReplicas: 20
  targetCPUUtilizationPercentage: 70

# Ingress 配置
ingress:
  enabled: true
  className: "nginx"
  annotations:
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
  hosts:
    - host: n3n.your-domain.com
      paths:
        - path: /
          pathType: Prefix
  tls:
    - secretName: n3n-tls
      hosts:
        - n3n.your-domain.com

# 監控
monitoring:
  enabled: true
  serviceMonitor:
    enabled: true
    interval: 30s
```

安裝：

```bash
helm install n3n ./helm/n3n \
  --namespace n3n \
  --values values-prod.yaml \
  --set database.password="$(kubectl get secret n3n-secrets -n n3n -o jsonpath='{.data.db-password}' | base64 -d)" \
  --set redis.password="$(kubectl get secret n3n-secrets -n n3n -o jsonpath='{.data.redis-password}' | base64 -d)"
```

#### 4. 驗證部署

```bash
# 檢查 Pod 狀態
kubectl get pods -n n3n

# 檢查服務
kubectl get svc -n n3n

# 查看日誌
kubectl logs -f deployment/n3n -n n3n

# 進入 Pod 除錯
kubectl exec -it deployment/n3n -n n3n -- /bin/sh
```

#### 5. 升級部署

```bash
# 升級到新版本
helm upgrade n3n ./helm/n3n \
  --namespace n3n \
  --values values-prod.yaml \
  --set image.tag="1.1.0"

# 回滾
helm rollback n3n 1 -n n3n
```

### 手動部署 YAML

如果不使用 Helm，可以手動建立 Kubernetes 資源。

#### 1. Namespace 和 ConfigMap

```yaml
# namespace.yaml
apiVersion: v1
kind: Namespace
metadata:
  name: n3n
---
# configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: n3n-config
  namespace: n3n
data:
  SPRING_PROFILES_ACTIVE: "kubernetes"
  SPRING_DATASOURCE_URL: "jdbc:postgresql://n3n-postgres:5432/n3n"
  SPRING_DATA_REDIS_HOST: "n3n-redis"
  SPRING_DATA_REDIS_PORT: "6379"
```

#### 2. Secret

```yaml
# secret.yaml
apiVersion: v1
kind: Secret
metadata:
  name: n3n-secrets
  namespace: n3n
type: Opaque
stringData:
  db-username: "n3n"
  db-password: "your-db-password"
  redis-password: "your-redis-password"
  # 注意：多實例部署必須設定，確保所有 Pod 共用相同加密密鑰
  # 產生方式：openssl rand -base64 32
  N3N_MASTER_KEY: "your-base64-encoded-master-key"
  # 產生方式：openssl rand -base64 64
  JWT_SECRET: "your-base64-encoded-jwt-secret"
```

#### 3. Deployment

```yaml
# deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: n3n
  namespace: n3n
spec:
  replicas: 2
  selector:
    matchLabels:
      app: n3n
  template:
    metadata:
      labels:
        app: n3n
    spec:
      securityContext:
        runAsNonRoot: true
        runAsUser: 1000
        fsGroup: 1000
      containers:
        - name: n3n
          image: ghcr.io/aiinpocket/n3n:latest
          ports:
            - containerPort: 8080
          envFrom:
            - configMapRef:
                name: n3n-config
          env:
            # Master Key 自動產生，掛載 PVC 以持久化
            - name: SPRING_DATASOURCE_USERNAME
              valueFrom:
                secretKeyRef:
                  name: n3n-secrets
                  key: db-username
            - name: SPRING_DATASOURCE_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: n3n-secrets
                  key: db-password
          resources:
            requests:
              memory: "512Mi"
              cpu: "250m"
            limits:
              memory: "1Gi"
              cpu: "1000m"
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 60
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 5
```

#### 4. Service 和 Ingress

```yaml
# service.yaml
apiVersion: v1
kind: Service
metadata:
  name: n3n
  namespace: n3n
spec:
  selector:
    app: n3n
  ports:
    - port: 8080
      targetPort: 8080
  type: ClusterIP
---
# ingress.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: n3n
  namespace: n3n
  annotations:
    nginx.ingress.kubernetes.io/proxy-body-size: "50m"
spec:
  ingressClassName: nginx
  rules:
    - host: n3n.your-domain.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: n3n
                port:
                  number: 8080
```

套用配置：

```bash
kubectl apply -f namespace.yaml
kubectl apply -f configmap.yaml
kubectl apply -f secret.yaml
kubectl apply -f deployment.yaml
kubectl apply -f service.yaml
kubectl apply -f ingress.yaml
```

---

## 環境變數配置

| 變數 | 必要 | 說明 | 預設值 |
|------|:----:|------|--------|
| `DATABASE_URL` |  | PostgreSQL 連線字串 | jdbc:postgresql://postgres:5432/n3n |
| `DATABASE_USERNAME` |  | 資料庫帳號 | n3n |
| `DATABASE_PASSWORD` |  | 資料庫密碼 | n3n |
| `REDIS_HOST` |  | Redis 主機 | redis |
| `REDIS_PORT` |  | Redis 連接埠 | 6379 |
| `REDIS_PASSWORD` |  | Redis 密碼 | (無) |
| `JWT_SECRET` |  | JWT 簽章金鑰 | 自動產生並持久化到資料庫 |
| `N3N_SERVER_BASE_URL` | 生產環境建議 | 公開存取的 URL（影響 Webhook URL 與 Email 連結） | http://localhost:8080 |
| `ALLOWED_ORIGINS` |  | CORS 允許來源 | http://localhost:8080,http://localhost:3000 |
| `JAVA_OPTS` |  | JVM 參數 | -XX:MaxRAMPercentage=75.0 |

> **零配置原則**：所有安全金鑰（JWT Secret、Master Key）在首次啟動時自動產生。
> 只有在多實例叢集部署時，才需要手動設定 `JWT_SECRET` 以確保所有實例共用同一金鑰。

---

## 安全配置

### 金鑰管理

N3N 採用零配置安全設計：

- **Master Key**：首次啟動時自動產生，持久化到 `/data` 卷中的檔案
- **JWT Secret**：自動產生並存入資料庫，重啟後自動載入
- **Recovery Key**：12 個 BIP-39 英文單詞，首次設定時顯示給管理員備份

> **重要**：Recovery Key 是恢復加密憑證的唯一方式。請務必在首次設定時備份。

### 安全建議

1. **備份 Recovery Key** — 首次設定時務必抄寫或下載保存
2. 使用 Kubernetes Secrets 或 HashiCorp Vault 管理資料庫密碼
3. 啟用 TLS 加密所有通訊
4. 定期輪換資料庫密碼
5. 內部服務不暴露端口（預設已遵守）

---

## 監控與日誌

### Prometheus 指標

N3N 透過 Spring Boot Actuator 提供 Prometheus 指標：

```bash
# 指標端點
GET /actuator/prometheus

# Kubernetes ServiceMonitor 範例
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: n3n
spec:
  selector:
    matchLabels:
      app: n3n
  endpoints:
    - port: http
      path: /actuator/prometheus
      interval: 30s
```

### 日誌收集

建議使用 EFK (Elasticsearch, Fluentd, Kibana) 或 Loki 收集日誌：

```yaml
# 配置 JSON 格式日誌
logging:
  pattern:
    console: '{"timestamp":"%d{ISO8601}","level":"%level","logger":"%logger","message":"%msg"}%n'
```

---

## 常見問題

### Q: 應用程式無法連接資料庫

確認：
1. 資料庫服務是否正常運行
2. 連線字串是否正確
3. 防火牆規則是否允許連線
4. 帳號密碼是否正確

```bash
# Docker 環境測試連線
docker exec -it n3n-postgres psql -U n3n -d n3n -c "SELECT 1"

# Kubernetes 環境測試連線
kubectl exec -it deployment/n3n -n n3n -- wget -qO- http://localhost:8080/actuator/health
```

### Q: Pod 持續重啟

檢查：
1. 資源限制是否足夠（OOMKilled）
2. 健康檢查是否配置正確
3. 依賴服務是否就緒

```bash
# 查看 Pod 事件
kubectl describe pod -l app=n3n -n n3n

# 查看容器日誌
kubectl logs -f deployment/n3n -n n3n --previous
```

### Q: Ingress 無法存取

確認：
1. Ingress Controller 是否已安裝
2. DNS 是否指向正確 IP
3. TLS 憑證是否有效

```bash
# 檢查 Ingress 狀態
kubectl get ingress -n n3n
kubectl describe ingress n3n -n n3n
```

---

## 支援

如有問題，請到 GitHub Issues 回報：
https://github.com/aiinpocket/n3n/issues
