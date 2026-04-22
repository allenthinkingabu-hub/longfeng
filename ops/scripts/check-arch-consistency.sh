#!/usr/bin/env bash
# 落地计划 §1.7 规则 C · 架构一致性硬门禁
# 用法：check-arch-consistency.sh <phase-id> [--trace-biz]
# 退出码：0=通过，1=违规
#
# 核心职责：
# 1. 豁免 Phase（front matter exempt: true）直接放行
# 2. 非豁免 Phase：gate_status 必须 approved
# 3. 代码改动里的符号（class/interface/@RequestMapping/topic/table）必须在 arch doc 中 grep 到
# 4. --trace-biz（v1.5）：0.2 领域模型实体名必须在 0.1 业务架构图节点 label 可见
set -euo pipefail

PHASE="${1:?usage: check-arch-consistency.sh <phase-id> [--trace-biz]}"
TRACE_BIZ=false
[[ "${2:-}" == "--trace-biz" ]] && TRACE_BIZ=true

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"

# 落地计划 §1.7 Rule F 示例：design/arch/s3-wrongbook.md · s4-ai-analysis.md · s0-bootstrap.md
# 既允许精确 <phase-id>.md 也允许 <phase-id>-<slug>.md
ARCH_DIR="$REPO_ROOT/design/arch"
ARCH_DOC=""
if [[ -f "$ARCH_DIR/${PHASE}.md" ]]; then
  ARCH_DOC="$ARCH_DIR/${PHASE}.md"
else
  # Glob by prefix; pick first match
  MATCH=$(ls "$ARCH_DIR/${PHASE}"-*.md 2>/dev/null | head -1 || true)
  if [[ -n "$MATCH" ]]; then
    ARCH_DOC="$MATCH"
  fi
fi

if [[ -z "$ARCH_DOC" || ! -f "$ARCH_DOC" ]]; then
  echo "FAIL: design/arch/${PHASE}.md or ${PHASE}-*.md missing"
  exit 1
fi

command -v yq >/dev/null 2>&1 || { echo "FAIL: yq not installed"; exit 1; }

# 1. Exempt phases: front matter `exempt: true` → pass
EXEMPT=$(yq '.exempt' "$ARCH_DOC" 2>/dev/null || echo "null")
if [[ "$EXEMPT" == "true" ]]; then
  echo "[arch-consistency] phase=$PHASE exempted · skipping symbol scan"
  exit 0
fi

# 2. Non-exempt: gate_status must be approved
GATE=$(yq '.gate_status' "$ARCH_DOC" 2>/dev/null || echo "draft")
if [[ "$GATE" != "approved" ]]; then
  echo "FAIL: $ARCH_DOC gate_status=$GATE (need 'approved')"
  exit 1
fi

# 3. Scan new/changed code symbols in the diff window
DIFF_BASE="${DIFF_BASE:-main}"
DIFF_RANGE="${DIFF_BASE}...HEAD"

CHANGED_FILES=$(git -C "$REPO_ROOT" diff "$DIFF_RANGE" --name-only 2>/dev/null | \
  grep -E '\.(java|ts|tsx|sql|yaml|yml)$' || true)

SYMBOLS=""
if [[ -n "$CHANGED_FILES" ]]; then
  while IFS= read -r f; do
    [[ -f "$REPO_ROOT/$f" ]] || continue
    # Extract: Java class/interface names, API mappings, topics, SQL tables
    grep -hE '(class |interface |@RequestMapping|@PostMapping|@GetMapping|@PutMapping|@DeleteMapping|topic:|CREATE TABLE)' "$REPO_ROOT/$f" 2>/dev/null | \
      sed -E '
        s/.*(class|interface) ([A-Z][A-Za-z0-9]+).*/\2/
        s/.*"(\/[a-z0-9\-\/_{}]+)".*/\1/
        s/.*topic:[[:space:]]*([a-z0-9\.\-]+).*/\1/
        s/.*CREATE TABLE[[:space:]]+([a-z_][a-z0-9_]*).*/\1/I
      '
  done <<< "$CHANGED_FILES" | sort -u > /tmp/arch-symbols-$$.txt
  SYMBOLS=$(cat /tmp/arch-symbols-$$.txt)
  rm -f /tmp/arch-symbols-$$.txt
fi

MISSING=0
if [[ -n "$SYMBOLS" ]]; then
  while IFS= read -r sym; do
    [[ -z "$sym" ]] && continue
    if ! grep -qF "$sym" "$ARCH_DOC"; then
      echo "MISS: symbol '$sym' not in $ARCH_DOC"
      MISSING=$((MISSING+1))
    fi
  done <<< "$SYMBOLS"
fi

# 4. --trace-biz (v1.5): 0.2 entity names must appear in 0.1 business arch diagram nodes
if $TRACE_BIZ; then
  # Extract entity names from section "## 1. 领域模型"
  # (heuristic: look for "聚合根 / 实体 / 值对象" bullet names ≈ CamelCase)
  ENTITIES=$(awk '
    /^## 1\. 领域模型/,/^## [02-9]/ { print }
  ' "$ARCH_DOC" | grep -oE '\b[A-Z][A-Za-z0-9]+\b' | sort -u || true)

  # Extract node labels from section "## 0. 业务架构图"
  BIZ_NODES=$(awk '
    /^## 0\. 业务架构图/,/^## 1\./ { print }
  ' "$ARCH_DOC" || true)

  TRACE_MISS=0
  while IFS= read -r entity; do
    [[ -z "$entity" ]] && continue
    # Skip common words (e.g., Mermaid, Entity header boilerplate)
    if [[ "$entity" =~ ^(Mermaid|Entity|Class|Value|Object|TRUE|FALSE|Diagram|Why|How)$ ]]; then continue; fi
    if ! echo "$BIZ_NODES" | grep -qF "$entity"; then
      echo "TRACE-MISS: domain entity '$entity' not in 0.1 business arch diagram"
      TRACE_MISS=$((TRACE_MISS+1))
    fi
  done <<< "$ENTITIES"

  if [[ "$TRACE_MISS" -gt 0 ]]; then
    echo "FAIL: $TRACE_MISS domain entity(s) not traceable to business arch"
    exit 1
  fi
fi

if [[ "$MISSING" -gt 0 ]]; then
  echo "FAIL: $MISSING symbol(s) missing in $ARCH_DOC"
  exit 1
fi

echo "[arch-consistency] phase=$PHASE OK"
exit 0
