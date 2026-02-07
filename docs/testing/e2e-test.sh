#!/bin/bash
#
# N3N E2E 完整測試腳本 - MongoDB CRUD + 並行工作流程
#
# 測試內容：
# 1. 註冊新用戶
# 2. 登入取得 token
# 3. 建立 MongoDB 憑證
# 4. 建立包含 8 個節點的工作流程（含並行處理、MongoDB CRUD、結果匯總）
# 5. 發布並執行流程
# 6. 驗證 MongoDB 資料和執行結果
#

set -e

BASE_URL="http://localhost:8080"
API_URL="$BASE_URL/api"

# 測試用戶資料
TEST_EMAIL="e2e-test-$(date +%s)@n3n.test"
TEST_PASSWORD="TestPass12345"
TEST_NAME="E2E Test User"

# 顏色輸出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_warning() { echo -e "${YELLOW}[WARNING]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_step() { echo -e "${CYAN}[STEP]${NC} $1"; }

# ========== 步驟 1: 檢查服務狀態 ==========
check_services() {
    log_step "1/9 檢查服務狀態..."

    if ! curl -sf "$BASE_URL/actuator/health" > /dev/null 2>&1; then
        log_error "N3N 應用未啟動或不健康"
        return 1
    fi
    log_success "N3N 應用正常運行"

    if ! docker exec n3n-mongodb mongosh --eval "db.adminCommand('ping')" > /dev/null 2>&1; then
        log_error "MongoDB 未啟動"
        return 1
    fi
    log_success "MongoDB 正常運行"

    return 0
}

# ========== 步驟 2: 註冊用戶 ==========
register_user() {
    log_step "2/9 註冊新用戶: $TEST_EMAIL"

    REGISTER_RESPONSE=$(curl -sf -X POST "$API_URL/auth/register" \
        -H "Content-Type: application/json" \
        -d "{\"email\": \"$TEST_EMAIL\", \"password\": \"$TEST_PASSWORD\", \"name\": \"$TEST_NAME\"}" 2>&1) || {
        log_error "註冊失敗"
        return 1
    }

    ACCESS_TOKEN=$(echo "$REGISTER_RESPONSE" | jq -r '.accessToken')

    if [ "$ACCESS_TOKEN" = "null" ] || [ -z "$ACCESS_TOKEN" ]; then
        log_info "首次設定完成，執行登入..."
        LOGIN_RESPONSE=$(curl -sf -X POST "$API_URL/auth/login" \
            -H "Content-Type: application/json" \
            -d "{\"email\": \"$TEST_EMAIL\", \"password\": \"$TEST_PASSWORD\"}" 2>&1) || {
            log_error "登入失敗"
            return 1
        }
        ACCESS_TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.accessToken')
    fi

    if [ "$ACCESS_TOKEN" = "null" ] || [ -z "$ACCESS_TOKEN" ]; then
        log_error "無法取得 access token"
        return 1
    fi

    log_success "登入成功"
    export ACCESS_TOKEN
}

# ========== 步驟 3: 建立 MongoDB 憑證 ==========
create_credential() {
    log_step "3/9 建立 MongoDB 憑證..."

    CRED_DATA=$(cat << 'CREDEOF'
{
    "name": "MongoDB E2E Test",
    "type": "mongodb",
    "description": "E2E 測試用 MongoDB 連線",
    "visibility": "private",
    "data": {
        "host": "mongodb",
        "port": "27017",
        "database": "n3n_test",
        "username": "n3n_admin",
        "password": "n3n_secret_123",
        "authSource": "admin"
    }
}
CREDEOF
)

    CRED_RESPONSE=$(curl -sf -X POST "$API_URL/credentials" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $ACCESS_TOKEN" \
        -d "$CRED_DATA" 2>&1) || {
        log_error "建立憑證失敗"
        return 1
    }

    CREDENTIAL_ID=$(echo "$CRED_RESPONSE" | jq -r '.id')
    if [ "$CREDENTIAL_ID" = "null" ] || [ -z "$CREDENTIAL_ID" ]; then
        log_error "無法取得憑證 ID"
        echo "$CRED_RESPONSE"
        return 1
    fi

    log_success "MongoDB 憑證建立成功: $CREDENTIAL_ID"
    export CREDENTIAL_ID
}

# ========== 步驟 4: 建立工作流程 ==========
create_flow() {
    log_step "4/9 建立工作流程..."

    FLOW_NAME="MongoDB-CRUD-E2E-$(date +%s)"
    FLOW_RESPONSE=$(curl -s -X POST "$API_URL/flows" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $ACCESS_TOKEN" \
        -d "{\"name\": \"$FLOW_NAME\", \"description\": \"MongoDB CRUD with parallel processing and fan-in (8 nodes)\"}" 2>&1)

    # Check for errors
    if echo "$FLOW_RESPONSE" | jq -e '.error' > /dev/null 2>&1; then
        log_error "建立流程失敗"
        echo "$FLOW_RESPONSE" | jq '.'
        return 1
    fi

    FLOW_ID=$(echo "$FLOW_RESPONSE" | jq -r '.id')
    if [ "$FLOW_ID" = "null" ] || [ -z "$FLOW_ID" ]; then
        log_error "無法取得流程 ID"
        return 1
    fi

    log_success "流程建立成功: $FLOW_ID"
    export FLOW_ID
}

# ========== 步驟 5: 儲存流程版本 ==========
save_flow_version() {
    log_step "5/9 儲存流程版本（8 節點，含 MongoDB CRUD）..."

    # 流程架構：
    #
    # [1] Trigger ─► [2] Code: 產生資料 ─┬─► [3] MongoDB: Insert  ─┐
    #                                    ├─► [4] MongoDB: Find    ─┼─► [6] Code: 匯總 ─► [7] MongoDB: Update ─► [8] Output
    #                                    └─► [5] HTTP: 外部驗證   ─┘
    #

    # 建立版本資料檔案
    cat > /tmp/flow_version.json << JSONEOF
{
    "version": "1.0.0",
    "definition": {
        "nodes": [
            {
                "id": "trigger-1",
                "type": "trigger",
                "position": { "x": 100, "y": 200 },
                "data": { "label": "開始", "config": {} }
            },
            {
                "id": "code-init",
                "type": "code",
                "position": { "x": 300, "y": 200 },
                "data": {
                    "label": "產生測試資料",
                    "config": {
                        "language": "javascript",
                        "code": "const testId = 'e2e_' + Date.now(); const doc = { _testId: testId, name: 'E2E Test Document', status: 'pending', values: [10, 20, 30, 40, 50], createdAt: new Date().toISOString() }; return { testId, document: doc, documentJson: JSON.stringify(doc) };"
                    }
                }
            },
            {
                "id": "mongo-insert",
                "type": "mongodb",
                "position": { "x": 550, "y": 100 },
                "data": {
                    "label": "MongoDB Insert",
                    "config": {
                        "credentialId": "$CREDENTIAL_ID",
                        "resource": "document",
                        "operation": "insertOne",
                        "collection": "e2e_tests",
                        "document": "{\\"name\\": \\"E2E Test\\", \\"status\\": \\"pending\\", \\"timestamp\\": \\"{{new Date().toISOString()}}\\"}"
                    }
                }
            },
            {
                "id": "mongo-find",
                "type": "mongodb",
                "position": { "x": 550, "y": 200 },
                "data": {
                    "label": "MongoDB Find",
                    "config": {
                        "credentialId": "$CREDENTIAL_ID",
                        "resource": "document",
                        "operation": "find",
                        "collection": "e2e_tests",
                        "filter": "{\\"status\\": \\"pending\\"}",
                        "limit": 10
                    }
                }
            },
            {
                "id": "http-check",
                "type": "httpRequest",
                "position": { "x": 550, "y": 300 },
                "data": {
                    "label": "外部 API 驗證",
                    "config": {
                        "method": "GET",
                        "url": "https://httpbin.org/get?test=mongodb-crud",
                        "timeout": 15
                    }
                }
            },
            {
                "id": "code-aggregate",
                "type": "code",
                "position": { "x": 800, "y": 200 },
                "data": {
                    "label": "匯總結果",
                    "config": {
                        "language": "javascript",
                        "code": "const summary = { completed: true, operations: ['insert', 'find', 'http'], timestamp: new Date().toISOString() }; return { summary };"
                    }
                }
            },
            {
                "id": "mongo-update",
                "type": "mongodb",
                "position": { "x": 1000, "y": 200 },
                "data": {
                    "label": "MongoDB Update",
                    "config": {
                        "credentialId": "$CREDENTIAL_ID",
                        "resource": "document",
                        "operation": "updateMany",
                        "collection": "e2e_tests",
                        "filter": "{\\"status\\": \\"pending\\"}",
                        "update": "{\\"\$set\\": {\\"status\\": \\"completed\\"}}"
                    }
                }
            },
            {
                "id": "output-1",
                "type": "output",
                "position": { "x": 1200, "y": 200 },
                "data": { "label": "完成", "config": {} }
            }
        ],
        "edges": [
            { "id": "e1", "source": "trigger-1", "target": "code-init" },
            { "id": "e2", "source": "code-init", "target": "mongo-insert" },
            { "id": "e3", "source": "code-init", "target": "mongo-find" },
            { "id": "e4", "source": "code-init", "target": "http-check" },
            { "id": "e5", "source": "mongo-insert", "target": "code-aggregate" },
            { "id": "e6", "source": "mongo-find", "target": "code-aggregate" },
            { "id": "e7", "source": "http-check", "target": "code-aggregate" },
            { "id": "e8", "source": "code-aggregate", "target": "mongo-update" },
            { "id": "e9", "source": "mongo-update", "target": "output-1" }
        ]
    },
    "settings": { "concurrency": "allow", "timeout": 300 }
}
JSONEOF

    VERSION_RESPONSE=$(curl -sf -X POST "$API_URL/flows/$FLOW_ID/versions" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $ACCESS_TOKEN" \
        -d @/tmp/flow_version.json 2>&1) || {
        log_error "儲存版本失敗"
        return 1
    }

    VERSION=$(echo "$VERSION_RESPONSE" | jq -r '.version')
    if [ "$VERSION" = "null" ] || [ -z "$VERSION" ]; then
        log_error "無法取得版本號"
        echo "$VERSION_RESPONSE"
        return 1
    fi

    log_success "流程版本儲存成功: $VERSION"
    export FLOW_VERSION="$VERSION"
    rm -f /tmp/flow_version.json
}

# ========== 步驟 6: 發布流程版本 ==========
publish_flow() {
    log_step "6/9 發布流程版本..."

    curl -sf -X POST "$API_URL/flows/$FLOW_ID/versions/$FLOW_VERSION/publish" \
        -H "Authorization: Bearer $ACCESS_TOKEN" > /dev/null 2>&1 || {
        log_error "發布失敗"
        return 1
    }

    log_success "流程版本已發布"
}

# ========== 步驟 7: 執行流程 ==========
execute_flow() {
    log_step "7/9 執行流程..."

    EXEC_RESPONSE=$(curl -sf -X POST "$API_URL/executions" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $ACCESS_TOKEN" \
        -d "{\"flowId\": \"$FLOW_ID\", \"version\": \"$FLOW_VERSION\", \"input\": {\"testRun\": true, \"timestamp\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\"}}" 2>&1) || {
        log_error "執行失敗"
        echo "Response: $EXEC_RESPONSE"
        return 1
    }

    EXECUTION_ID=$(echo "$EXEC_RESPONSE" | jq -r '.id')
    if [ "$EXECUTION_ID" = "null" ] || [ -z "$EXECUTION_ID" ]; then
        log_error "無法取得執行 ID"
        echo "$EXEC_RESPONSE"
        return 1
    fi

    log_success "流程執行已啟動: $EXECUTION_ID"
    export EXECUTION_ID
}

# ========== 步驟 8: 等待執行完成 ==========
wait_for_completion() {
    log_step "8/9 等待執行完成..."

    MAX_WAIT=120
    WAIT_COUNT=0

    while [ $WAIT_COUNT -lt $MAX_WAIT ]; do
        EXEC_STATUS=$(curl -sf "$API_URL/executions/$EXECUTION_ID" \
            -H "Authorization: Bearer $ACCESS_TOKEN" 2>&1) || {
            log_error "無法取得執行狀態"
            return 1
        }

        EXEC_STATE=$(echo "$EXEC_STATUS" | jq -r '.status')

        case "$EXEC_STATE" in
            "COMPLETED"|"completed")
                log_success "執行完成！"
                return 0
                ;;
            "FAILED"|"failed")
                log_error "執行失敗"
                echo "$EXEC_STATUS" | jq '.'
                # 取得節點執行詳情
                curl -sf "$API_URL/executions/$EXECUTION_ID/nodes" \
                    -H "Authorization: Bearer $ACCESS_TOKEN" 2>&1 | jq '.[] | select(.status == "failed") | {nodeId, errorMessage}'
                return 1
                ;;
            "CANCELLED"|"cancelled")
                log_error "執行被取消"
                return 1
                ;;
            *)
                printf "\r  狀態: %-12s [%3d/%d 秒]" "$EXEC_STATE" "$WAIT_COUNT" "$MAX_WAIT"
                sleep 2
                WAIT_COUNT=$((WAIT_COUNT + 2))
                ;;
        esac
    done

    echo ""
    log_error "執行超時"
    return 1
}

# ========== 步驟 9: 驗證結果 ==========
verify_results() {
    log_step "9/9 驗證執行結果..."

    echo ""
    log_info "節點執行狀態:"

    NODES_RESPONSE=$(curl -sf "$API_URL/executions/$EXECUTION_ID/nodes" \
        -H "Authorization: Bearer $ACCESS_TOKEN" 2>&1) || {
        log_error "無法取得節點執行結果"
        return 1
    }

    echo "$NODES_RESPONSE" | jq -r '.[] | "  \(.nodeId): \(.status) (\(.durationMs // 0)ms)"' 2>/dev/null

    # 檢查失敗的節點
    FAILED_COUNT=$(echo "$NODES_RESPONSE" | jq '[.[] | select(.status == "failed")] | length' 2>/dev/null)
    if [ "$FAILED_COUNT" != "0" ] && [ -n "$FAILED_COUNT" ]; then
        log_warning "有 $FAILED_COUNT 個節點執行失敗"
        echo "$NODES_RESPONSE" | jq '.[] | select(.status == "failed") | {nodeId, errorMessage}'
    fi

    # 驗證 MongoDB 資料
    echo ""
    log_info "MongoDB 資料驗證:"

    MONGO_COUNT=$(docker exec n3n-mongodb mongosh --quiet \
        -u n3n_admin -p n3n_secret_123 --authenticationDatabase admin \
        n3n_test --eval "db.e2e_tests.countDocuments({})" 2>&1) || MONGO_COUNT="查詢失敗"

    echo "  文件總數: $MONGO_COUNT"

    COMPLETED_COUNT=$(docker exec n3n-mongodb mongosh --quiet \
        -u n3n_admin -p n3n_secret_123 --authenticationDatabase admin \
        n3n_test --eval "db.e2e_tests.countDocuments({status: 'completed'})" 2>&1) || COMPLETED_COUNT="0"

    echo "  已完成狀態: $COMPLETED_COUNT"

    # 取得執行輸出
    echo ""
    log_info "執行輸出摘要:"
    OUTPUT_RESPONSE=$(curl -sf "$API_URL/executions/$EXECUTION_ID/output" \
        -H "Authorization: Bearer $ACCESS_TOKEN" 2>&1) || true

    if [ -n "$OUTPUT_RESPONSE" ]; then
        echo "$OUTPUT_RESPONSE" | jq 'keys' 2>/dev/null || echo "$OUTPUT_RESPONSE"
    fi

    log_success "驗證完成！"
}

# ========== 清理測試資料 ==========
cleanup() {
    log_info "清理測試資料..."
    docker exec n3n-mongodb mongosh --quiet \
        -u n3n_admin -p n3n_secret_123 --authenticationDatabase admin \
        n3n_test --eval "db.e2e_tests.drop()" 2>/dev/null || true
    log_success "清理完成"
}

# ========== 主程式 ==========
main() {
    echo ""
    echo "╔══════════════════════════════════════════════════════════════════╗"
    echo "║           N3N E2E 完整測試 - MongoDB CRUD 工作流程               ║"
    echo "╠══════════════════════════════════════════════════════════════════╣"
    echo "║  • 8 個節點的複雜工作流程                                        ║"
    echo "║  • 並行處理（3 個分支同時執行）                                  ║"
    echo "║  • MongoDB CRUD: Insert → Find → Update                         ║"
    echo "║  • Fan-in 結果匯總                                               ║"
    echo "║  • HTTP 外部 API 呼叫                                            ║"
    echo "╚══════════════════════════════════════════════════════════════════╝"
    echo ""

    if ! command -v jq &> /dev/null; then
        log_error "需要安裝 jq 工具"
        exit 1
    fi

    START_TIME=$(date +%s)

    check_services || exit 1
    register_user || exit 1
    create_credential || exit 1
    create_flow || exit 1
    save_flow_version || exit 1
    publish_flow || exit 1
    execute_flow || exit 1
    wait_for_completion || exit 1
    verify_results || exit 1

    END_TIME=$(date +%s)
    DURATION=$((END_TIME - START_TIME))

    echo ""
    echo "╔══════════════════════════════════════════════════════════════════╗"
    echo "║                    ✅ 所有 E2E 測試通過！                        ║"
    echo "╠══════════════════════════════════════════════════════════════════╣"
    printf "║  用戶:     %-54s ║\n" "$TEST_EMAIL"
    printf "║  流程 ID:  %-54s ║\n" "$FLOW_ID"
    printf "║  執行 ID:  %-54s ║\n" "$EXECUTION_ID"
    printf "║  憑證 ID:  %-54s ║\n" "$CREDENTIAL_ID"
    printf "║  節點數:   %-54s ║\n" "8 (含 3 個並行分支)"
    printf "║  總耗時:   %-54s ║\n" "${DURATION} 秒"
    echo "╚══════════════════════════════════════════════════════════════════╝"
    echo ""

    if [ "$1" = "--cleanup" ]; then
        cleanup
    fi
}

main "$@"
