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

開發環境使用預設的 `docker-compose.yml`，會啟動 PostgreSQL 和 Redis：

```bash
# 1. Clone 專案
git clone https://github.com/aiinpocket/n3n.git
cd n3n

# 2. 啟動依賴服務
docker compose up -d

# 3. 確認服務狀態
docker compose ps

# 4. 編譯並啟動應用程式
./mvnw spring-boot:run
```

**服務連線資訊：**

| 服務 | 連線位址 | 帳號/密碼 |
|------|---------|----------|
| PostgreSQL | localhost:5432 | n3n / n3n |
| Redis | localhost:6379 | - |
| N3N Web | http://localhost:8080 | - |

### 使用 Docker Compose（生產環境）

生產環境需要額外配置安全設定。建立 `docker-compose.prod.yml`：

```yaml
services:
  n3n:
    image: ghcr.io/aiinpocket/n3n:latest
    container_name: n3n-app
    ports:
      - "8080:8080"
    environment:
      # 必要配置 - 使用強密碼
      N3N_MASTER_KEY: ${N3N_MASTER_KEY}
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/n3n
      SPRING_DATASOURCE_USERNAME: ${DB_USERNAME}
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      SPRING_DATA_REDIS_HOST: redis
      SPRING_DATA_REDIS_PORT: 6379
      SPRING_DATA_REDIS_PASSWORD: ${REDIS_PASSWORD}
      # 可選配置
      JAVA_OPTS: "-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "wget", "-q", "--spider", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s

  postgres:
    image: postgres:15-alpine
    container_name: n3n-postgres
    environment:
      POSTGRES_DB: n3n
      POSTGRES_USER: ${DB_USERNAME}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    restart: unless-stopped
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USERNAME}"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    container_name: n3n-redis
    command: redis-server --appendonly yes --requirepass ${REDIS_PASSWORD}
    volumes:
      - redis_data:/data
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "redis-cli", "-a", "${REDIS_PASSWORD}", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  postgres_data:
  redis_data:
```

建立 `.env` 檔案：

```bash
# .env (生產環境請使用強密碼)
N3N_MASTER_KEY=your-32-byte-base64-encoded-key
DB_USERNAME=n3n_prod
DB_PASSWORD=strong-password-here
REDIS_PASSWORD=strong-redis-password
```

啟動生產環境：

```bash
# 使用生產配置啟動
docker compose -f docker-compose.prod.yml up -d

# 查看日誌
docker compose -f docker-compose.prod.yml logs -f n3n
```

### 單獨使用 Docker

如果只需要執行 N3N 應用程式（外部已有資料庫）：

```bash
# 建置映像檔
docker build -t n3n:latest .

# 執行容器
docker run -d \
  --name n3n \
  -p 8080:8080 \
  -e N3N_MASTER_KEY="your-master-key" \
  -e SPRING_DATASOURCE_URL="jdbc:postgresql://host.docker.internal:5432/n3n" \
  -e SPRING_DATASOURCE_USERNAME="n3n" \
  -e SPRING_DATASOURCE_PASSWORD="password" \
  -e SPRING_DATA_REDIS_HOST="host.docker.internal" \
  -e SPRING_DATA_REDIS_PORT="6379" \
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

# 建立 secrets（必要）
kubectl create secret generic n3n-secrets \
  --namespace n3n \
  --from-literal=master-key="your-32-byte-base64-encoded-key" \
  --from-literal=jwt-secret="your-jwt-secret" \
  --from-literal=db-password="strong-db-password" \
  --from-literal=redis-password="strong-redis-password"
```

#### 3. 安裝 Chart

**開發環境（內建 PostgreSQL + Redis）：**

```bash
helm install n3n ./helm/n3n \
  --namespace n3n \
  --set config.jwtSecret="your-jwt-secret" \
  --set config.encryptionKey="your-encryption-key"
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
  master-key: "your-32-byte-base64-encoded-key"
  db-username: "n3n"
  db-password: "your-db-password"
  redis-password: "your-redis-password"
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
            - name: N3N_MASTER_KEY
              valueFrom:
                secretKeyRef:
                  name: n3n-secrets
                  key: master-key
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
| `N3N_MASTER_KEY` | ✅ | 資料加密主金鑰（32 bytes, base64 編碼） | - |
| `SPRING_DATASOURCE_URL` | ✅ | PostgreSQL 連線字串 | jdbc:postgresql://localhost:5432/n3n |
| `SPRING_DATASOURCE_USERNAME` | ✅ | 資料庫帳號 | n3n |
| `SPRING_DATASOURCE_PASSWORD` | ✅ | 資料庫密碼 | n3n |
| `SPRING_DATA_REDIS_HOST` | ✅ | Redis 主機 | localhost |
| `SPRING_DATA_REDIS_PORT` |  | Redis 連接埠 | 6379 |
| `SPRING_DATA_REDIS_PASSWORD` |  | Redis 密碼 | - |
| `JAVA_OPTS` |  | JVM 參數 | -XX:MaxRAMPercentage=75.0 |

---

## 安全配置

### 生成 Master Key

```bash
# 使用 OpenSSL 生成 32 bytes 隨機金鑰
openssl rand -base64 32
# 輸出範例: nJsaWEwTysEqcg/pAbCD32u8emt/KkJSeBZWdh7NGos=
```

### 安全建議

1. **永遠不要將 Master Key 寫入程式碼或 Git**
2. 使用 Kubernetes Secrets 或 HashiCorp Vault 管理機敏資訊
3. 啟用 TLS 加密所有通訊
4. 定期輪換密碼和金鑰
5. 限制資料庫和 Redis 的網路存取

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
