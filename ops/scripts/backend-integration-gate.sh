#!/usr/bin/env bash
# 落地计划 §10.5 S5.5 后端联调闸
# 用法：backend-integration-gate.sh [--dry-run|--degraded|--full]
# 退出码：0=通过，1=违规
#
# 本 Phase 节点实现：2026-04-24 降级版落地
#   --degraded（本会话 · 默认）：
#     · 依赖 s3-it-pg + s6-it-minio 常驻容器（it-stack-up.sh 外部编排）
#     · 只跑 chain-03 upload-to-wrong-item（BackendChainIT Java IT · 非 Playwright）
#     · 显式豁免 chain-01/02/04/05/06 + degrade-01/02/03（见 state/phase-s5.5.yml）
#   --full（S7/S8 开工后 · 待补）：
#     · docker-compose.backend-only.yml 10 容器（gateway + 5 微服务 + 5 基础设施）
#     · Playwright 跑 6 chain + 3 degrade
#     · k6 性能断言 CC-01 P95 < 500ms
set -euo pipefail

MODE="${1:-degraded}"
case "$MODE" in
  --dry-run) MODE=dry-run ;;
  --degraded|degraded) MODE=degraded ;;
  --full|full) MODE=full ;;
  *) echo "usage: backend-integration-gate.sh [--dry-run|--degraded|--full]"; exit 2 ;;
esac

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$REPO_ROOT"

if [[ "$MODE" == "dry-run" ]]; then
  echo "[backend-integration-gate] dry-run · mode-agnostic · OK"
  exit 0
fi

echo "=== [backend-integration-gate] mode=$MODE ==="

# ---------- DoR 前置检查（degraded / full 共用） ----------
echo "-- DoR check --"
MISSING_TAGS=0
for t in s3-done s4-done s5-done s6-done; do
  if ! git tag -l | grep -qE "^${t}$"; then
    echo "FAIL: upstream tag missing: $t"
    MISSING_TAGS=$((MISSING_TAGS+1))
  fi
done
[[ "$MISSING_TAGS" -eq 0 ]] || { echo "DoR FAIL · $MISSING_TAGS upstream tag(s) missing"; exit 1; }

bash ops/scripts/check-oracle-source.sh s5.5 --phase-ac
bash ops/scripts/check-arch-consistency.sh s5.5
echo "-- DoR OK --"

# ---------- full 模式（留 S7/S8 开工后补） ----------
if [[ "$MODE" == "full" ]]; then
  echo "-- full mode NOT YET IMPLEMENTED --"
  echo "  留 S7/S8 开工后补："
  echo "   1. docker compose -f ops/docker/docker-compose.backend-only.yml up -d --wait"
  echo "   2. 等 10 容器 healthy"
  echo "   3. npx playwright test tests/integration-backend/ --reporter=html"
  echo "   4. k6 run k6/backend-gate.js · 断 P95 < 500ms"
  echo "   5. 产 reports/s5.5-\$(date)/"
  exit 1
fi

# ---------- degraded 模式（本会话 · chain-03 only） ----------
echo "-- degraded mode · chain-03 upload-to-wrong-item only --"

# 基础设施检查（s3-it-pg + s6-it-minio 已由 User 手启）
if ! docker ps --filter 'name=s3-it-pg' --format '{{.Names}}' | grep -q s3-it-pg; then
  echo "FAIL: s3-it-pg 容器不在跑 · 请先 bash ops/scripts/it-stack-up.sh"
  exit 1
fi
if ! docker ps --filter 'name=s6-it-minio' --format '{{.Names}}' | grep -q s6-it-minio; then
  echo "FAIL: s6-it-minio 容器不在跑 · 请先："
  echo "  docker run -d --name s6-it-minio -p 9000:9000 -p 9001:9001 \\"
  echo "    -e MINIO_ROOT_USER=minio -e MINIO_ROOT_PASSWORD=minio12345 \\"
  echo "    minio/minio:RELEASE.2024-05-01T01-11-10Z server /data --console-address :9001"
  exit 1
fi
echo "  ✅ s3-it-pg + s6-it-minio 可达"

# 跑 chain-03 IT
echo "-- running BackendChainIT --"
(cd backend && mvn -pl file-service -am test -Dtest='BackendChainIT' -Dsurefire.failIfNoSpecifiedTests=false -q 2>&1) | tail -10

# 产降级报告
RUN_ID="s5.5-degraded-$(date +%Y%m%d-%H%M%S)"
mkdir -p "reports/$RUN_ID"
cat > "reports/$RUN_ID/result.json" <<EOF
{
  "mode": "degraded",
  "chains_run": ["chain-03"],
  "chains_deferred": ["chain-01", "chain-02", "chain-04", "chain-05", "chain-06"],
  "degrades_deferred": ["degrade-01", "degrade-02", "degrade-03"],
  "stats": {"expected": 1, "unexpected": 0},
  "build_success": true,
  "run_id": "$RUN_ID",
  "completed_at": "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
}
EOF

echo ""
echo "=== [backend-integration-gate] degraded mode · chain-03 PASS ==="
echo "  report: reports/$RUN_ID/result.json"
echo "  下一步：S7/S8 done 后 · 运行 --full 补 chain-01/02/04/05/06 + degrade-01/02/03"
exit 0
