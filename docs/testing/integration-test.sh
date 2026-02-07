#!/bin/bash
BASE_URL="http://localhost:8080/api"
PASS=0; FAIL=0; ERRORS=""

check() {
  local name="$1" expected="$2" actual="$3"
  if [ "$actual" = "$expected" ]; then
    PASS=$((PASS+1))
  else
    FAIL=$((FAIL+1))
    ERRORS="$ERRORS\n  FAIL: $name (expected=$expected actual=$actual)"
  fi
}

# 1. Auth required
SC=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/flows")
check "auth-required" "401" "$SC"

# 2. Register
REG=$(curl -s -X POST "$BASE_URL/auth/register" -H 'Content-Type: application/json' \
  -d '{"email":"admin@test.com","password":"TestPass12345Ab","name":"Admin User"}')
TOKEN=$(echo "$REG" | python3 -c "import sys,json; print(json.load(sys.stdin).get('accessToken',''))" 2>/dev/null)
check "register" "true" "$([ -n "$TOKEN" ] && [ ${#TOKEN} -gt 10 ] && echo true || echo false)"
AUTH="Authorization: Bearer $TOKEN"

# 3. Login
LR=$(curl -s -X POST "$BASE_URL/auth/login" -H 'Content-Type: application/json' \
  -d '{"email":"admin@test.com","password":"TestPass12345Ab"}')
AT=$(echo "$LR" | python3 -c "import sys,json; print(json.load(sys.stdin).get('accessToken',''))" 2>/dev/null)
check "login" "true" "$([ -n "$AT" ] && [ ${#AT} -gt 10 ] && echo true || echo false)"
TOKEN="$AT"; AUTH="Authorization: Bearer $TOKEN"

# 4. Refresh
RT=$(echo "$LR" | python3 -c "import sys,json; print(json.load(sys.stdin).get('refreshToken',''))" 2>/dev/null)
RR=$(curl -s -X POST "$BASE_URL/auth/refresh" -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$RT\"}")
NAT=$(echo "$RR" | python3 -c "import sys,json; print(json.load(sys.stdin).get('accessToken',''))" 2>/dev/null)
check "refresh" "true" "$([ -n "$NAT" ] && [ ${#NAT} -gt 10 ] && echo true || echo false)"
TOKEN="$NAT"; AUTH="Authorization: Bearer $TOKEN"

# 5-8. Flow CRUD
CF=$(curl -s -X POST "$BASE_URL/flows" -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"name":"Test Flow","description":"Integration test"}')
FID=$(echo "$CF" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null)
check "create-flow" "true" "$([ -n "$FID" ] && [ ${#FID} -gt 5 ] && echo true || echo false)"

SC=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/flows/$FID" -H "$AUTH")
check "get-flow" "200" "$SC"

SC=$(curl -s -o /dev/null -w '%{http_code}' -X PUT "$BASE_URL/flows/$FID" -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"name":"Updated Flow","description":"Updated"}')
check "update-flow" "200" "$SC"

SC=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/flows" -H "$AUTH")
check "list-flows" "200" "$SC"

# 9. Version (with version field)
VR=$(curl -s -w '\n%{http_code}' -X POST "$BASE_URL/flows/$FID/versions" -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"version":"1.0.0","definition":{"nodes":[{"id":"n1","type":"trigger","data":{"label":"Start"},"position":{"x":0,"y":0}}],"edges":[]}}')
VSC=$(echo "$VR" | tail -1)
check "create-version" "200" "$VSC"

# 10. Publish
SC=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/flows/$FID/versions/1.0.0/publish" -H "$AUTH")
check "publish" "200" "$SC"

# 11. Execute (POST /api/executions with flowId)
ER=$(curl -s -X POST "$BASE_URL/executions" -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"flowId\":\"$FID\"}")
EID=$(echo "$ER" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null)
check "execute" "true" "$([ -n "$EID" ] && [ ${#EID} -gt 5 ] && echo true || echo false)"

# 12. Retry
if [ -n "$EID" ] && [ ${#EID} -gt 5 ]; then
  sleep 1
  SC=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/executions/$EID/retry" -H "$AUTH")
  check "retry" "200" "$SC"
else
  check "retry" "200" "skip"
fi

# 13. Register second user for share
REG2=$(curl -s -X POST "$BASE_URL/auth/register" -H 'Content-Type: application/json' \
  -d '{"email":"user@test.com","password":"TestPass12345Ab","name":"Normal User"}')
T2=$(echo "$REG2" | python3 -c "import sys,json; print(json.load(sys.stdin).get('accessToken',''))" 2>/dev/null)

# 14. Share (with email)
SR=$(curl -s -X POST "$BASE_URL/flows/$FID/shares" -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"email":"user@test.com","permission":"view"}')
SROK=$(echo "$SR" | python3 -c "import sys,json; d=json.load(sys.stdin); print('ok' if 'id' in d else 'fail')" 2>/dev/null)
check "share-flow" "ok" "$SROK"

# 15. Webhook (with name)
WH=$(curl -s -X POST "$BASE_URL/webhooks" -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"flowId\":\"$FID\",\"name\":\"Test Hook\",\"path\":\"test-hook\",\"method\":\"POST\"}")
WHID=$(echo "$WH" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null)
check "webhook" "true" "$([ -n "$WHID" ] && [ ${#WHID} -gt 5 ] && echo true || echo false)"

# 16. Credential (lowercase type)
CR=$(curl -s -X POST "$BASE_URL/credentials" -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"name":"Test Cred","type":"api_key","data":{"apiKey":"test123"}}')
CRID=$(echo "$CR" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null)
check "credential" "true" "$([ -n "$CRID" ] && [ ${#CRID} -gt 5 ] && echo true || echo false)"

# 17. Dashboard
SC=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/dashboard/stats" -H "$AUTH")
check "dashboard" "200" "$SC"

# 18. Monitoring (ADMIN: /monitoring/system)
SC=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/monitoring/system" -H "$AUTH")
check "monitoring" "200" "$SC"

# 19. Activities (/activities/my)
SC=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/activities/my" -H "$AUTH")
check "activities" "200" "$SC"

# 20. RBAC
SC=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/admin/users" -H "Authorization: Bearer $T2")
check "rbac" "403" "$SC"

# 21. Export
SC=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/flows/$FID/export" -H "$AUTH")
check "export" "200" "$SC"

# 22. Skills
SC=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/skills" -H "$AUTH")
check "skills" "200" "$SC"

# 23. Rate limit
for i in $(seq 1 12); do
  curl -s -o /dev/null -X POST "$BASE_URL/auth/login" -H 'Content-Type: application/json' \
    -d '{"email":"ratelimit@test.com","password":"wrong"}' 2>/dev/null
done
RLR=$(curl -s -X POST "$BASE_URL/auth/login" -H 'Content-Type: application/json' \
  -d '{"email":"ratelimit@test.com","password":"wrong"}')
RLS=$(echo "$RLR" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('status',0))" 2>/dev/null)
check "rate-limit" "429" "$RLS"

# 24. Password change
SC=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/auth/change-password" -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"currentPassword":"TestPass12345Ab","newPassword":"NewPass456789Cd"}')
check "change-password" "200" "$SC"

# 25. Login new password
NLR=$(curl -s -X POST "$BASE_URL/auth/login" -H 'Content-Type: application/json' \
  -d '{"email":"admin@test.com","password":"NewPass456789Cd"}')
NLT=$(echo "$NLR" | python3 -c "import sys,json; print(json.load(sys.stdin).get('accessToken',''))" 2>/dev/null)
check "login-new-pwd" "true" "$([ -n "$NLT" ] && [ ${#NLT} -gt 10 ] && echo true || echo false)"
TOKEN="$NLT"; AUTH="Authorization: Bearer $TOKEN"

# 26. Pagination
SC=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/flows?page=0&size=1000" -H "$AUTH")
check "pagination" "200" "$SC"

# 27. Executions list
SC=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/executions" -H "$AUTH")
check "executions" "200" "$SC"

# 28a. Clone Flow (before delete)
CLONE_RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/flows/$FID/clone" -H "$AUTH")
CLONE_SC=$(echo "$CLONE_RESP" | tail -1)
CLONE_BODY=$(echo "$CLONE_RESP" | sed '$d')
CLONE_ID=$(echo "$CLONE_BODY" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null)
check "clone-flow" "201" "$CLONE_SC"
check "clone-flow-has-id" "true" "$([ -n "$CLONE_ID" ] && [ ${#CLONE_ID} -gt 10 ] && echo true || echo false)"

# 28b. Delete flow (204)
SC=$(curl -s -o /dev/null -w '%{http_code}' -X DELETE "$BASE_URL/flows/$FID" -H "$AUTH")
check "delete-flow" "204" "$SC"

# 29. Deleted = 404
SC=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/flows/$FID" -H "$AUTH")
check "deleted-404" "404" "$SC"

# 30. Constraint validation
LONG_REASON=$(python3 -c "print('x'*600)")
SC=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/executions/00000000-0000-0000-0000-000000000000/pause?reason=$LONG_REASON" -H "$AUTH")
check "validation" "400" "$SC"

# 31. Security status
SC=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/security/status" -H "$AUTH")
check "security" "200" "$SC"

# 32-33. Services/Components
SC=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/services" -H "$AUTH")
check "services" "200" "$SC"

SC=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/components" -H "$AUTH")
check "components" "200" "$SC"

# 34-36. i18n JSON
python3 -c "import json; json.load(open('src/main/frontend/src/i18n/locales/en.json'))" 2>/dev/null && check "en-json" "true" "true" || check "en-json" "true" "false"
python3 -c "import json; json.load(open('src/main/frontend/src/i18n/locales/zh-TW.json'))" 2>/dev/null && check "zh-json" "true" "true" || check "zh-json" "true" "false"
python3 -c "import json; json.load(open('src/main/frontend/src/i18n/locales/ja.json'))" 2>/dev/null && check "ja-json" "true" "true" || check "ja-json" "true" "false"

# 37-39. New i18n keys
python3 -c "
import json
d=json.load(open('src/main/frontend/src/i18n/locales/en.json'))
assert d['device']['editDevice']; assert d['device']['directConnection']
assert d['recovery']['migrateTitle']; assert d['recovery']['reEncryptInfo']
" 2>/dev/null && check "i18n-en" "true" "true" || check "i18n-en" "true" "false"

python3 -c "
import json
d=json.load(open('src/main/frontend/src/i18n/locales/zh-TW.json'))
assert d['device']['editDevice']; assert d['recovery']['migrateTitle']
" 2>/dev/null && check "i18n-zh" "true" "true" || check "i18n-zh" "true" "false"

python3 -c "
import json
d=json.load(open('src/main/frontend/src/i18n/locales/ja.json'))
assert d['device']['editDevice']; assert d['recovery']['migrateTitle']
" 2>/dev/null && check "i18n-ja" "true" "true" || check "i18n-ja" "true" "false"

# ========== Profile Update ==========
PROFILE_RESP=$(curl -s -w "\n%{http_code}" -X PUT "$BASE_URL/auth/profile" \
  -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"name":"Updated Name"}')
PROFILE_SC=$(echo "$PROFILE_RESP" | tail -1)
PROFILE_BODY=$(echo "$PROFILE_RESP" | sed '$d')
PROFILE_NAME=$(echo "$PROFILE_BODY" | python3 -c "import sys,json; print(json.load(sys.stdin).get('name',''))" 2>/dev/null)
check "profile-update" "200" "$PROFILE_SC"
check "profile-name-updated" "Updated Name" "$PROFILE_NAME"

# ========== Help i18n keys ==========
python3 -c "
import json
d=json.load(open('src/main/frontend/src/i18n/locales/en.json'))
assert d['help']['documentation']; assert d['help']['reportIssue']
assert d['flow']['clone']; assert d['flow']['cloneSuccess']
assert d['account']['editProfile']; assert d['account']['updateProfile']
" 2>/dev/null && check "i18n-new-keys" "true" "true" || check "i18n-new-keys" "true" "false"

echo ""
echo "=============================="
echo "Integration Tests: $PASS passed, $FAIL failed (total $((PASS+FAIL)))"
if [ $FAIL -gt 0 ]; then
  echo -e "Failures:$ERRORS"
fi
echo "=============================="
