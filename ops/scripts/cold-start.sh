#!/usr/bin/env bash
# 落地计划 §1.8 规则 D · 冷启动 5 步读入序
# 用法：cold-start.sh <phase-id>
# 输出一个 Read-清单，Agent 按顺序读；其余一律不读
set -euo pipefail

PHASE="${1:?usage: cold-start.sh <phase-id>}"
REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"

echo "=== Cold-start read sequence for phase=$PHASE ==="
echo ""
echo "Step 1 · Read Phase chapter (11 fields self-contained recipe):"
echo "  → design/落地实施计划_v1.0_AI自动执行.md (search: '^## .* Phase ${PHASE^^}')"
echo ""
echo "Step 2 · Read Phase arch doc (symbol truth-source for this Phase):"
echo "  → design/arch/${PHASE}.md"
if [[ -f "$REPO_ROOT/design/arch/${PHASE}.md" ]]; then
  echo "    (exists · $(wc -l < "$REPO_ROOT/design/arch/${PHASE}.md") lines)"
else
  echo "    (MISSING · Phase not started or Planner must create)"
fi
echo ""
echo "Step 3 · Read Phase state (progress / DAG):"
echo "  → state/phase-${PHASE}.yml"
if [[ -f "$REPO_ROOT/state/phase-${PHASE}.yml" ]]; then
  echo "    (exists · $(wc -l < "$REPO_ROOT/state/phase-${PHASE}.yml") lines)"
else
  echo "    (MISSING · run ops/scripts/state-init.sh ${PHASE})"
fi
echo ""
echo "Step 4 · Read upstream interfaces (contracts only — not other Phases' arch docs):"
echo "  → state/interfaces.yml (filter to this Phase's dor_depends_on)"
if [[ -f "$REPO_ROOT/state/interfaces.yml" ]]; then
  echo "    (exists · $(wc -l < "$REPO_ROOT/state/interfaces.yml") lines)"
else
  echo "    (MISSING · no upstream interfaces yet)"
fi
echo ""
echo "Step 5 · If scratch from prior reset exists, pick up next_step:"
SCRATCH_GLOB="$REPO_ROOT/state/scratch_summary_${PHASE}_*.md"
SCRATCHES=$(ls $SCRATCH_GLOB 2>/dev/null || true)
if [[ -n "$SCRATCHES" ]]; then
  echo "  → latest scratch:"
  ls -t $SCRATCH_GLOB | head -1 | sed 's/^/    /'
else
  echo "  (no scratch · start fresh from Step 1 of Phase)"
fi
echo ""
echo "=== End of cold-start · all other files are L0 read-only anchors · do not preload ==="
