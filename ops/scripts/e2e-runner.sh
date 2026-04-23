#!/usr/bin/env bash
# 落地计划 §27.2 · v1.9 · E2E Runner（按 SC/AC/matrix 策略跑 120-180 条）
# 用法：e2e-runner.sh [--strategy balanced|critical-only|smoke] [--since <tag>]
# 退出码：0=全绿，1=有失败但已 Triage，2=用法错误
#
# 策略窗口 [120, 180]（§27.2 决策 · 避免 4h 禁区）：
#   每 SC happy_path       1 条 × 15 SC   = 15
#   每 critical AC error   1 条 × 50-80   = 50-80
#   每 Phase boundary 抽样 2 条 × 6 级 A  = 12
#   visual 全跑                             = 24
#   合计约 101-131（策略允许 [120, 200] 窗）
#
# 产物：reports/e2e/<run_id>/junit.xml + failures/<failure_id>.yml
#
# S0 骨架：策略编排 + Playwright 调度留 S9 节点补
set -euo pipefail

STRATEGY="balanced"
SINCE=""
for ((i=1; i<=$#; i++)); do
  arg="${!i}"
  case "$arg" in
    --strategy)
      next=$((i+1)); STRATEGY="${!next:-balanced}" ;;
    --since)
      next=$((i+1)); SINCE="${!next:-}" ;;
  esac
done

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
RUN_ID="e2e-$(date -u +%Y%m%d-%H%M%S)"
OUT_DIR="$REPO_ROOT/reports/e2e/$RUN_ID"
mkdir -p "$OUT_DIR/failures"

echo "[e2e-runner] skeleton OK · strategy=$STRATEGY · since=${SINCE:-HEAD} · run_id=$RUN_ID"
echo "[e2e-runner] TODO S9 节点补："
echo "  1. 读 design/sc-phase-mapping.yml + design/analysis/*-business-analysis.yml"
echo "  2. 按 strategy 筛选测试清单（critical=true AC 必跑）"
echo "  3. npx playwright test --config=e2e/playwright.config.ts --reporter=junit"
echo "  4. 失败用例产 $OUT_DIR/failures/<failure_id>.yml（schema 见 §27.7）"
echo "  5. 输出 $OUT_DIR/junit.xml + summary.md"
echo "  6. 策略窗口校验 V-S9-21：总条数 ∈ [120, 180]"

# S0 骨架：产 placeholder junit.xml 让下游 triage-agent.sh 能连得上
cat > "$OUT_DIR/junit.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="e2e-skeleton" tests="0" failures="0" errors="0">
  <!-- S0 骨架 · S9 节点补真实执行 -->
</testsuite>
EOF
cat > "$OUT_DIR/summary.md" <<EOF
# E2E Run ${RUN_ID} · SKELETON
- strategy: ${STRATEGY}
- total: 0 · pass: 0 · fail: 0 · skipped: 0
- generated_at: $(date -u +%Y-%m-%dT%H:%M:%SZ)
_S0 骨架 · S9 节点补真实测试编排_
EOF
exit 0
