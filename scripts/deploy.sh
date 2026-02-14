#!/usr/bin/env bash
# =============================================================
# N3N Flow Platform - 首次部署（僅需執行一次）
# =============================================================
# 兩種模式：
#   1. 有 kubectl：自動安裝 ArgoCD + 設定 Application
#   2. 無 kubectl：輸出 Headlamp 操作指引（透過 Web UI apply YAML）
#
# 完成後 CI/CD 全自動：git push → build → ArgoCD auto-sync
# =============================================================
set -euo pipefail

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
RED='\033[0;31m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
K8S_DIR="${SCRIPT_DIR}/../k8s"

echo -e "${GREEN}╔═══════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║  N3N Flow Platform - 首次部署             ║${NC}"
echo -e "${GREEN}╚═══════════════════════════════════════════╝${NC}"
echo ""

# 產生 secrets
JWT_SECRET=$(openssl rand -base64 32)
DB_PASSWORD=$(openssl rand -base64 16 | tr -d '=+/')

# =============================================================
# 模式判斷
# =============================================================
HAS_KUBECTL=false
if command -v kubectl >/dev/null 2>&1 && kubectl cluster-info >/dev/null 2>&1; then
  HAS_KUBECTL=true
fi

if [ "$HAS_KUBECTL" = true ]; then
  echo -e "${GREEN}偵測到 kubectl（可連線叢集）→ 自動安裝模式${NC}"
  echo ""

  # ─── 1. Nexus Docker Push Ingress ───
  echo -e "${YELLOW}[1/5] Nexus Docker push 端點...${NC}"
  kubectl create namespace nexus --dry-run=client -o yaml | kubectl apply -f -
  kubectl apply -f "${K8S_DIR}/nexus-docker-push.yaml"

  # ─── 2. 安裝 ArgoCD ───
  echo -e "${YELLOW}[2/5] 安裝 ArgoCD...${NC}"
  kubectl create namespace argocd --dry-run=client -o yaml | kubectl apply -f -
  kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
  echo "  等待 ArgoCD server 就緒..."
  kubectl wait --for=condition=available deployment/argocd-server -n argocd --timeout=300s

  # ArgoCD server 預設為 HTTPS，改為 HTTP（TLS 由 Cloudflare 處理）
  kubectl -n argocd patch configmap argocd-cmd-params-cm \
    --type merge -p '{"data":{"server.insecure":"true"}}'
  kubectl -n argocd rollout restart deployment argocd-server
  kubectl wait --for=condition=available deployment/argocd-server -n argocd --timeout=120s

  # ─── 3. ArgoCD Ingress ───
  echo -e "${YELLOW}[3/5] 設定 ArgoCD Ingress...${NC}"
  kubectl apply -f "${K8S_DIR}/argocd/argocd-ingress.yaml"

  # ─── 4. 取得密碼 + 建立 Application ───
  echo -e "${YELLOW}[4/5] 建立 N3N Application...${NC}"
  ARGOCD_PASSWORD=$(kubectl get secret argocd-initial-admin-secret -n argocd \
    -o jsonpath='{.data.password}' | base64 -d)

  kubectl apply -f "${K8S_DIR}/argocd/n3n-application.yaml"

  # 設定 secrets 透過 argocd CLI（如果有的話）或 patch
  # 用 kubectl patch Application 設定 Helm parameters
  kubectl -n argocd patch application n3n --type merge -p "{
    \"spec\": {
      \"source\": {
        \"helm\": {
          \"parameters\": [
            {\"name\": \"config.jwtSecret\", \"value\": \"${JWT_SECRET}\"},
            {\"name\": \"postgresql.auth.password\", \"value\": \"${DB_PASSWORD}\"},
            {\"name\": \"database.password\", \"value\": \"${DB_PASSWORD}\"}
          ]
        }
      }
    }
  }"

  # ─── 5. 觸發同步 ───
  echo -e "${YELLOW}[5/5] 首次同步...${NC}"
  # 等待 ArgoCD 偵測到 application 然後自動同步
  echo "  ArgoCD 已設定自動同步，等待中..."
  sleep 10
  kubectl get application n3n -n argocd -o jsonpath='{.status.sync.status}' 2>/dev/null || true
  echo ""

  echo ""
  echo -e "${GREEN}╔═══════════════════════════════════════════════════════════╗${NC}"
  echo -e "${GREEN}║  部署完成！                                               ║${NC}"
  echo -e "${GREEN}╚═══════════════════════════════════════════════════════════╝${NC}"
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
  echo ""

else
  # =============================================================
  # 無 kubectl → Headlamp 操作指引
  # =============================================================
  echo -e "${YELLOW}未偵測到 kubectl → 輸出 Headlamp 操作指引${NC}"
  echo ""
  echo -e "${GREEN}請開啟 Headlamp：${CYAN}https://console.kubeinpocket.com${NC}"
  echo -e "${GREEN}用 Google 帳號登入後，依照以下步驟操作：${NC}"
  echo ""

  echo -e "${YELLOW}═══ 步驟 1/4：建立 Namespace ═══${NC}"
  echo "在 Headlamp 左側選 Namespaces → Create → 分別建立："
  echo "  - argocd"
  echo "  - nexus（如果不存在）"
  echo "  - n3n"
  echo ""

  echo -e "${YELLOW}═══ 步驟 2/4：安裝 ArgoCD ═══${NC}"
  echo "在 Headlamp 左上角選 namespace: argocd"
  echo "然後用「Create Resource」貼上以下 URL 的內容："
  echo ""
  echo -e "  ${CYAN}https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml${NC}"
  echo ""
  echo "或者，下載後分批貼上："
  echo "  curl -sL https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml > /tmp/argocd-install.yaml"
  echo ""
  echo "安裝完成後，patch configmap 讓 server 走 HTTP（TLS 由 Cloudflare 處理）："
  echo "  在 Headlamp 找到 ConfigMap: argocd-cmd-params-cm (namespace: argocd)"
  echo "  編輯，在 data 加入：server.insecure: \"true\""
  echo "  然後重啟 Deployment: argocd-server"
  echo ""

  echo -e "${YELLOW}═══ 步驟 3/4：Apply 以下 YAML ═══${NC}"
  echo "在 Headlamp 用「Create Resource」依序貼上："
  echo ""

  echo -e "${CYAN}--- [A] Nexus Docker Push Ingress (namespace: nexus) ---${NC}"
  cat "${K8S_DIR}/nexus-docker-push.yaml"
  echo ""

  echo -e "${CYAN}--- [B] ArgoCD Ingress (namespace: argocd) ---${NC}"
  cat "${K8S_DIR}/argocd/argocd-ingress.yaml"
  echo ""

  echo -e "${CYAN}--- [C] N3N Application (namespace: argocd) ---${NC}"
  cat "${K8S_DIR}/argocd/n3n-application.yaml"
  echo ""

  echo -e "${YELLOW}═══ 步驟 4/4：設定 N3N Secrets ═══${NC}"
  echo "在 Headlamp 找到 Application: n3n (namespace: argocd)"
  echo "編輯 YAML，在 spec.source.helm 加入 parameters："
  echo ""
  echo "  parameters:"
  echo "    - name: config.jwtSecret"
  echo "      value: \"${JWT_SECRET}\""
  echo "    - name: postgresql.auth.password"
  echo "      value: \"${DB_PASSWORD}\""
  echo "    - name: database.password"
  echo "      value: \"${DB_PASSWORD}\""
  echo ""

  echo -e "${GREEN}╔═══════════════════════════════════════════════════════════╗${NC}"
  echo -e "${GREEN}║  完成以上步驟後，設定 GitHub Secrets：                    ║${NC}"
  echo -e "${GREEN}╚═══════════════════════════════════════════════════════════╝${NC}"
  echo ""
  echo -e "  ${CYAN}https://github.com/aiinpocket/n3n/settings/secrets/actions${NC}"
  echo ""
  echo -e "  NEXUS_USERNAME  = admin"
  echo -e "  NEXUS_PASSWORD  = <Nexus admin 密碼>"
  echo ""
  echo -e "  ${YELLOW}ArgoCD 密碼取得方式：${NC}"
  echo "  在 Headlamp 找到 Secret: argocd-initial-admin-secret (namespace: argocd)"
  echo "  password 欄位即為 ArgoCD admin 密碼"
  echo ""
  echo -e "  之後每次 git push main → GitHub Actions build image → 更新 Git → ArgoCD 自動同步"
  echo ""
fi
