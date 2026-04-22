#!/usr/bin/env bash
# 落地计划 §1.7 规则 A' (v1.5 新增) · 业务架构图存在性 + special_requirements 字段校验
# 用法：check-biz-arch.sh <phase-id>
# 豁免 Phase 直接放行
set -euo pipefail

PHASE="${1:?usage: check-biz-arch.sh <phase-id>}"
REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
ARCH_DOC="$REPO_ROOT/design/arch/${PHASE}.md"

[[ -f "$ARCH_DOC" ]] || { echo "FAIL: $ARCH_DOC missing"; exit 1; }
command -v yq >/dev/null 2>&1 || { echo "FAIL: yq not installed"; exit 1; }

EXEMPT=$(yq '.exempt' "$ARCH_DOC" 2>/dev/null || echo "null")
if [[ "$EXEMPT" == "true" ]]; then
  echo "[biz-arch] phase=$PHASE exempted"
  exit 0
fi

FAIL=0

# 1. business_arch_diagram field must exist (points to anchor)
BIZ_ARCH=$(yq '.business_arch_diagram' "$ARCH_DOC" 2>/dev/null || echo "null")
if [[ -z "$BIZ_ARCH" || "$BIZ_ARCH" == "null" ]]; then
  echo "FAIL: $ARCH_DOC front matter missing 'business_arch_diagram'"
  FAIL=$((FAIL+1))
fi

# 2. special_requirements field must exist (list or explicit "none")
SPEC_REQ_TYPE=$(yq '.special_requirements | type' "$ARCH_DOC" 2>/dev/null || echo "!!null")
if [[ "$SPEC_REQ_TYPE" == "!!null" || "$SPEC_REQ_TYPE" == "null" ]]; then
  echo "FAIL: $ARCH_DOC front matter missing 'special_requirements' (use explicit 'none' if N/A)"
  FAIL=$((FAIL+1))
fi

# 3. Section "## 0. 业务架构图" must contain a Mermaid block
if ! awk '/^## 0\. 业务架构图/,/^## 1\./ { print }' "$ARCH_DOC" | grep -q '```mermaid'; then
  echo "FAIL: $ARCH_DOC section '## 0. 业务架构图' does not contain a mermaid code block"
  FAIL=$((FAIL+1))
fi

[[ "$FAIL" -eq 0 ]] || { echo "FAIL: $FAIL biz-arch violation(s) for phase=$PHASE"; exit 1; }
echo "[biz-arch] phase=$PHASE OK"
exit 0
