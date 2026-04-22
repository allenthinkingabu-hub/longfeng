#!/usr/bin/env bash
# 落地计划 §1.8 规则 H · 从 Phase 章节生成初始 state/phase-<id>.yml
# 用法：state-init.sh <phase-id>
# S0 Phase 没有明确 task 列表（是脚手架），所以为 s0 生成一个最小 state 即可
set -euo pipefail

PHASE="${1:?usage: state-init.sh <phase-id>}"
REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
STATE_FILE="$REPO_ROOT/state/phase-${PHASE}.yml"
RUN_ID="$(date -u +%Y%m%d-%H%M%S)"
NOW_UTC="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

if [[ -f "$STATE_FILE" ]]; then
  echo "[state-init] $STATE_FILE already exists · skipping (use state-advance.sh)"
  exit 0
fi

mkdir -p "$REPO_ROOT/state"

cat > "$STATE_FILE" <<EOF
phase_id: ${PHASE}
run_id: ${RUN_ID}
updated_at: ${NOW_UTC}
upstream_tags_verified: []
design_gate:
  biz_gate: exempt
  arch_gate: exempt
tasks: []
EOF

echo "[state-init] created $STATE_FILE"
