#!/usr/bin/env bash
# =============================================================
# N3N Flow Platform - 首次部署（僅需執行一次）
# =============================================================
# 在有 kubectl 存取權的機器上執行
# 安裝 ArgoCD + 設定 N3N Application
# 完成後 CI/CD 全自動：git push → build → deploy
#
# 前置條件：kubectl 和 helm 已安裝且可連線叢集
# =============================================================
set -euo pipefail

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
K8S_DIR="${SCRIPT_DIR}/../k8s"

echo -e "${GREEN}╔═══════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║  N3N Flow Platform - 首次部署             ║${NC}"
echo -e "${GREEN}╚═══════════════════════════════════════════╝${NC}"
echo ""

command -v kubectl >/dev/null 2>&1 || { echo "kubectl 未安裝"; exit 1; }
command -v helm >/dev/null 2>&1 || { echo "helm 未安裝"; exit 1; }
kubectl cluster-info >/dev/null 2>&1 || { echo "無法連接叢集"; exit 1; }
echo -e "${GREEN}叢集連線正常${NC}"
echo ""

# ─── 1. Nexus Docker Push 端點 ───
echo -e "${YELLOW}[1/5] Nexus Docker push 端點...${NC}"
kubectl create namespace nexus --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -f "${K8S_DIR}/nexus-docker-push.yaml"

# ─── 2. 安裝 ArgoCD ───
echo -e "${YELLOW}[2/5] 安裝 ArgoCD...${NC}"
helm repo add argo https://argoproj.github.io/argo-helm 2>/dev/null || true
helm repo update argo

helm upgrade --install argocd argo/argo-cd \
  --namespace argocd --create-namespace \
  -f "${K8S_DIR}/argocd/install-values.yaml" \
  --wait --timeout 5m

echo -e "${GREEN}  ArgoCD 安裝完成${NC}"

# 取得 admin 密碼
ARGOCD_PASSWORD=$(kubectl get secret argocd-initial-admin-secret -n argocd \
  -o jsonpath='{.data.password}' | base64 -d)
echo -e "${GREEN}  Web UI: https://argocd.kubeinpocket.com${NC}"

# ─── 3. 建立 N3N namespace + secrets ───
echo -e "${YELLOW}[3/5] 建立 N3N secrets...${NC}"
kubectl create namespace n3n --dry-run=client -o yaml | kubectl apply -f -

JWT_SECRET=$(openssl rand -base64 32)
DB_PASSWORD=$(openssl rand -base64 16 | tr -d '=+/')

# 預建 Helm 需要的 secret values（ArgoCD 會在 app set 時使用）
echo -e "${GREEN}  Secrets 已產生${NC}"

# ─── 4. 建立 ArgoCD Application ───
echo -e "${YELLOW}[4/5] 建立 ArgoCD Application...${NC}"
kubectl apply -f "${K8S_DIR}/argocd/n3n-application.yaml"

# 安裝 ArgoCD CLI
if ! command -v argocd >/dev/null 2>&1; then
  echo "  安裝 argocd CLI..."
  if [[ "$(uname -m)" == "arm64" ]] || [[ "$(uname -m)" == "aarch64" ]]; then
    curl -sSL -o /tmp/argocd "https://github.com/argoproj/argo-cd/releases/latest/download/argocd-linux-arm64"
  else
    curl -sSL -o /tmp/argocd "https://github.com/argoproj/argo-cd/releases/latest/download/argocd-linux-amd64"
  fi
  chmod +x /tmp/argocd
  sudo mv /tmp/argocd /usr/local/bin/argocd 2>/dev/null || mv /tmp/argocd ~/argocd
fi

# 用 port-forward 連線（首次安裝 Ingress 可能還沒 ready）
echo "  等待 ArgoCD server 就緒..."
kubectl wait --for=condition=available deployment/argocd-server -n argocd --timeout=120s

# 設定密鑰參數
kubectl port-forward svc/argocd-server -n argocd 8443:443 &
PF_PID=$!
sleep 3

argocd login localhost:8443 \
  --insecure --grpc-web \
  --username admin --password "${ARGOCD_PASSWORD}" 2>/dev/null

argocd app set n3n \
  -p config.jwtSecret="${JWT_SECRET}" \
  -p postgresql.auth.password="${DB_PASSWORD}" \
  -p database.password="${DB_PASSWORD}" \
  -p image.tag=latest

kill $PF_PID 2>/dev/null || true

echo -e "${GREEN}  Application 已建立${NC}"

# ─── 5. 觸發首次同步 ───
echo -e "${YELLOW}[5/5] 首次同步...${NC}"
kubectl port-forward svc/argocd-server -n argocd 8443:443 &
PF_PID=$!
sleep 3

argocd login localhost:8443 \
  --insecure --grpc-web \
  --username admin --password "${ARGOCD_PASSWORD}" 2>/dev/null

argocd app sync n3n --prune --timeout 600

kill $PF_PID 2>/dev/null || true

echo ""
echo -e "${GREEN}╔═══════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║  部署完成！                                               ║${NC}"
echo -e "${GREEN}╚═══════════════════════════════════════════════════════════╝${NC}"
echo ""
kubectl get pods -n n3n
echo ""
echo -e "  ${CYAN}應用程式:${NC}  https://n3n.kubeinpocket.com"
echo -e "  ${CYAN}ArgoCD UI:${NC} https://argocd.kubeinpocket.com"
echo -e "  ${CYAN}ArgoCD 帳密:${NC} admin / ${ARGOCD_PASSWORD}"
echo ""
echo -e "  ${YELLOW}GitHub Secrets（設定後 CI/CD 全自動）:${NC}"
echo -e "  https://github.com/aiinpocket/n3n/settings/secrets/actions"
echo ""
echo -e "  NEXUS_USERNAME  = admin"
echo -e "  NEXUS_PASSWORD  = <Nexus admin 密碼>"
echo -e "  ARGOCD_PASSWORD = ${ARGOCD_PASSWORD}"
echo ""
echo -e "  Cloudflare DNS（若無萬用字元）:"
echo -e "    n3n.kubeinpocket.com"
echo -e "    argocd.kubeinpocket.com"
echo -e "    docker-push.kubeinpocket.com"
