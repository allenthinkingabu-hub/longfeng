#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════
# Phase 2 · 启 6 BE service + FE dev (关 MSW)
# 复用现有 infra (s3-it-pg / s3-it-redis / s5.5-it-rmq-* / lf-dev-minio/nacos)
# BE 端口 98xx · FE 端口 9873
#
# 用法:
#   export LONGFENG_QIANWEN_KEY=sk-...   # user 提供
#   ./qa/scripts/start-fullstack.sh
# ═══════════════════════════════════════════════════════════════════
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

if [ -z "${LONGFENG_QIANWEN_KEY:-}" ]; then
  echo "❌ ERROR: LONGFENG_QIANWEN_KEY 未设置 · 必须 export 真实 DashScope key 才能跑 L4"
  exit 1
fi

# ── 1. infra 健康检查 ─────────────────────────────────────────
echo "=== [1/5] Infra health check ==="
redis-cli -p 16379 ping || { echo "❌ Redis :16379 down"; exit 1; }
PGPASSWORD=wb psql -h localhost -p 15432 -U postgres -d longfeng_dev -c "SELECT 1" > /dev/null || { echo "❌ PG :15432/longfeng_dev down"; exit 1; }
curl -sf http://localhost:19000/minio/health/live > /dev/null || { echo "❌ MinIO :19000 down"; exit 1; }
nc -z localhost 19876 || { echo "❌ RocketMQ ns :19876 down"; exit 1; }
echo "✅ Infra OK"

# ── 2. mvn install 一次 (skip tests · sub-agent 已各自跑过) ─────
echo "=== [2/5] mvn install (skip tests · 5 BE 已各自验) ==="
cd backend
mvn install -DskipTests -T 1C -q

# ── 3. 启 6 BE service · 后台 · 各自 .log ─────────────────────
mkdir -p "$ROOT/logs"
cd "$ROOT"

# 各 service 配置 (用环境变量覆盖 application.yml)
common_env=(
  --spring.datasource.url=jdbc:postgresql://localhost:15432/longfeng_dev
  --spring.datasource.username=postgres
  --spring.datasource.password=wb
  --spring.data.redis.host=localhost
  --spring.data.redis.port=16379
  --rocketmq.name-server=localhost:19876
)

storage_env=(
  --app.storage.minio.endpoint=http://localhost:19000
  --app.storage.minio.access-key=minio
  --app.storage.minio.secret-key=minio12345
  --app.storage.minio.bucket=wrongbook-dev
)

llm_env=(
  --longfeng.ai.provider=qianwen
  --longfeng.ai.qianwen.api-key=$LONGFENG_QIANWEN_KEY
)

declare -A SVC_PORT
SVC_PORT[gateway]=9880
SVC_PORT[wrongbook-service]=9881
SVC_PORT[ai-analysis-service]=9882
SVC_PORT[review-plan-service]=9883
SVC_PORT[file-service]=9884
SVC_PORT[anonymous-service]=9885

echo "=== [3/5] Start 6 BE service (background · check logs/) ==="
for svc in gateway wrongbook-service ai-analysis-service review-plan-service file-service anonymous-service; do
  port=${SVC_PORT[$svc]}
  args="--server.port=$port ${common_env[*]}"
  case $svc in
    file-service) args="$args ${storage_env[*]}";;
    ai-analysis-service) args="$args ${llm_env[*]}";;
  esac
  echo "  → $svc on :$port"
  nohup mvn -pl "backend/$svc" spring-boot:run \
    -Dspring-boot.run.arguments="$args" \
    > "logs/$svc.log" 2>&1 &
  echo "    pid=$!"
done

# ── 4. 等所有 health UP (180s 超时) ─────────────────────────
echo "=== [4/5] Wait for 6 health UP (180s timeout) ==="
deadline=$(($(date +%s) + 180))
for port in 9880 9881 9882 9883 9884 9885; do
  echo -n "  :$port "
  while true; do
    if curl -sf "http://localhost:$port/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
      echo "✅"
      break
    fi
    if [ $(date +%s) -gt $deadline ]; then
      echo "❌ TIMEOUT (see logs/)"
      exit 1
    fi
    sleep 2
  done
done

# ── 5. 启 FE dev · MSW 默认 DEV 模式启 · 我们要关 ──────────────
# 关 MSW 通过 vite production build · 但 dev mode 也可以用 MODE=production 关
# 简单做法: 直接改 main.tsx 的判断条件 · 但侵入性强
# 推荐: 临时跳过 MSW · 用 VITE_DISABLE_MSW=1 · 但要看 main.tsx 是否支持
# 当前办法: 把 vite proxy 指 :9880 · MSW 仍跑但被 BE 真返覆盖 (MSW onUnhandledRequest=bypass)
# 不 · MSW 会拦截匹配的 handler · 必须真关
# 终极: Playwright init 时 page.evaluate('window.__lf_disable_msw__ = true') · 看 main.tsx
echo "=== [5/5] Start FE dev (port 9873 · MSW handling 见 logs/fe-dev.log) ==="
cd frontend/apps/h5
VITE_API_PROXY_TARGET=http://localhost:9880 \
VITE_DISABLE_MSW=1 \
  nohup pnpm dev --port 9873 --host > "$ROOT/logs/fe-dev.log" 2>&1 &
echo "  pid=$!"
sleep 6

if curl -sf http://localhost:9873/ > /dev/null; then
  echo "✅ FE dev :9873 UP"
else
  echo "⚠️  FE dev :9873 not yet ready · 看 logs/fe-dev.log"
fi

echo ""
echo "═══════════════════════════════════════════════════════════════════"
echo "✅ 全栈起完。下一步:"
echo "  - 验路由: curl http://localhost:9880/actuator/gateway/routes | jq"
echo "  - 验 endpoint 真返: curl -H 'Authorization: Bearer dev-anon' http://localhost:9880/api/landing/kpi"
echo "  - 跑 L3 hybrid (BASE_URL=FE · /api 走 vite proxy → gateway):"
echo "    cd e2e && BASE_URL=http://localhost:9873 pnpm exec playwright test specs/sc-11.spec.ts specs/sc-12.spec.ts --reporter=list --trace on"
echo "═══════════════════════════════════════════════════════════════════"
