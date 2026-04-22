#!/usr/bin/env bash
# 落地计划 §1.8 规则 H · 原子更新 task 状态
# 用法：state-advance.sh <phase-id> <task-id> <status> [--hash <sha>]
set -euo pipefail

PHASE="${1:?usage: state-advance.sh <phase-id> <task-id> <status>}"
TASK_ID="${2:?missing task-id}"
STATUS="${3:?missing status (pending|in_progress|blocked|done|failed)}"
HASH=""
if [[ "${4:-}" == "--hash" ]]; then
  HASH="${5:-}"
fi

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
STATE_FILE="$REPO_ROOT/state/phase-${PHASE}.yml"
[[ -f "$STATE_FILE" ]] || { echo "FAIL: $STATE_FILE missing · run state-init.sh first"; exit 1; }

command -v yq >/dev/null 2>&1 || { echo "FAIL: yq not installed"; exit 1; }

# State machine gate
case "$STATUS" in
  pending|in_progress|blocked|done|failed) ;;
  *) echo "FAIL: invalid status '$STATUS'"; exit 1 ;;
esac

NOW_UTC="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
TMP="$(mktemp)"

# Atomic update via yq eval
yq eval "
  .updated_at = \"${NOW_UTC}\" |
  (.tasks[] | select(.id == \"${TASK_ID}\") | .status) = \"${STATUS}\"
" "$STATE_FILE" > "$TMP"

if [[ "$STATUS" == "done" && -n "$HASH" ]]; then
  yq eval -i "
    (.tasks[] | select(.id == \"${TASK_ID}\") | .outputs_hash) = \"${HASH}\" |
    (.tasks[] | select(.id == \"${TASK_ID}\") | .finished_at) = \"${NOW_UTC}\"
  " "$TMP"
fi

mv "$TMP" "$STATE_FILE"
echo "[state-advance] $STATE_FILE :: $TASK_ID → $STATUS"
