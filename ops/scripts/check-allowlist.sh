#!/usr/bin/env bash
# 落地计划 §1.6 规则 E · 工具白名单硬门禁
# 用法：check-allowlist.sh <phase-id>|all-phases
# 退出码：0=通过，1=违规
set -euo pipefail

PHASE="${1:-all-phases}"
REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
DIR="$REPO_ROOT/docs/allowlist"
GLOBAL="$DIR/global.yml"
DENY="$DIR/global-deny.yml"

[[ -f "$GLOBAL" ]] || { echo "FAIL: $GLOBAL missing"; exit 1; }
[[ -f "$DENY"   ]] || { echo "FAIL: $DENY missing"; exit 1; }

command -v yq >/dev/null 2>&1 || { echo "FAIL: yq not installed"; exit 1; }

# Phase-specific yml required when PHASE != all-phases
if [[ "$PHASE" != "all-phases" ]]; then
  [[ -f "$DIR/$PHASE.yml" ]] || { echo "FAIL: $DIR/$PHASE.yml missing"; exit 1; }
fi

FAIL=0

# ---------- 1. Deny patterns ----------
# Scan tracked files only, skip docs/allowlist (self-referential) + .md
# (patterns themselves appear inside allowlist files)
DENY_PATTERNS=$(yq '.deny_patterns[].pattern' "$DENY" 2>/dev/null || true)
while IFS= read -r pattern; do
  [[ -z "$pattern" || "$pattern" == "null" ]] && continue
  HITS=$(git -C "$REPO_ROOT" grep -nE "$pattern" -- \
    ':!docs/allowlist/*' \
    ':!docs/adr/*.md' \
    ':!design/*.md' \
    ':!design/**/*.md' \
    ':!README.md' \
    ':!.github/pull_request_template.md' \
    ':!ops/scripts/check-allowlist.sh' \
    2>/dev/null || true)
  if [[ -n "$HITS" ]]; then
    echo "FAIL: deny pattern '$pattern' hit:"
    echo "$HITS" | head -10
    FAIL=$((FAIL+1))
  fi
done <<< "$DENY_PATTERNS"

# ---------- 2. Deny tool names ----------
# Scan infra/config-ish files where deny tool mentions matter
DENY_TOOLS=$(yq '.deny_tools[].name' "$DENY" 2>/dev/null || true)
while IFS= read -r name; do
  [[ -z "$name" || "$name" == "null" ]] && continue
  HITS=$(git -C "$REPO_ROOT" grep -niE "\\b${name}\\b" -- \
    '.github/workflows/*' \
    'helm/' \
    'ops/scripts/*.sh' \
    'infra/' \
    'backend/**/pom.xml' \
    'frontend/**/package.json' \
    2>/dev/null || true)
  # Exclude matches that are only inside comments (# ...) in yaml/sh
  HITS=$(echo "$HITS" | grep -vE '^[^:]+:[0-9]+:[[:space:]]*(#|//)' || true)
  if [[ -n "$HITS" ]]; then
    echo "FAIL: deny tool '$name' referenced outside comments:"
    echo "$HITS" | head -10
    FAIL=$((FAIL+1))
  fi
done <<< "$DENY_TOOLS"

# ---------- 3. Phase allowlist integrity ----------
if [[ "$PHASE" != "all-phases" ]]; then
  EXTENDS=$(yq '.extends' "$DIR/$PHASE.yml" 2>/dev/null || echo "null")
  if [[ "$EXTENDS" != "global" && "$EXTENDS" != "null" ]]; then
    echo "FAIL: $PHASE.yml.extends must be 'global' (got: $EXTENDS)"
    FAIL=$((FAIL+1))
  fi
fi

if [[ "$FAIL" -gt 0 ]]; then
  echo ""
  echo "FAIL: $FAIL allowlist violation(s) for phase=$PHASE"
  exit 1
fi

echo "OK: allowlist check passed for phase=$PHASE"
exit 0
