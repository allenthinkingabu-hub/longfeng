#!/usr/bin/env bash
# 落地计划 §1.8 规则 E · Agent 自觉 reset
# 用法：handoff.sh <phase-id> <task-id> <reason>
# reason ∈ {token_budget_exceeded, tool_call_quota_exceeded, manual_handoff}
set -euo pipefail

PHASE="${1:?usage: handoff.sh <phase-id> <task-id> <reason>}"
TASK_ID="${2:?missing task-id}"
REASON="${3:?missing reason}"

case "$REASON" in
  token_budget_exceeded|tool_call_quota_exceeded|manual_handoff) ;;
  *) echo "FAIL: invalid reason '$REASON'"; exit 1 ;;
esac

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
SCRATCH="$REPO_ROOT/state/scratch_summary_${PHASE}_${TASK_ID}.md"
NOW_UTC="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

mkdir -p "$REPO_ROOT/state"

cat > "$SCRATCH" <<EOF
---
phase_id: ${PHASE}
task_id: ${TASK_ID}
run_id: $(date -u +%Y%m%d-%H%M%S)
written_at: ${NOW_UTC}
reason: ${REASON}
next_step: TODO-FILL-BEFORE-AGENT-EXITS
---

## 我做到哪了
- TODO: 列出已完成的 Step

## 关键临时发现（需下一位 Agent 感知）
- TODO: 列出不在代码里、但下一位 Agent 需要知道的发现

## 下一步从这里继续
- TODO: 描述具体的下一 Step + 验证命令
EOF

echo "[handoff] scratch written → $SCRATCH"
echo "[handoff] IMPORTANT: fill in TODO lines before Agent exits, else next Agent cold-start will be blind"
