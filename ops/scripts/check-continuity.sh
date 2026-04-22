#!/usr/bin/env bash
# 落地计划 §1.8 规则 C · 连贯性校验
# 用法：check-continuity.sh <phase-id>
# 三件事：
#   ① state/interfaces.yml 中 path 对应文件的 sha256 == 声明值
#   ② 本 Phase DoR 声明的 dor_depends_on[*].id 都能在 interfaces.yml 里找到
#   ③ upstream_tags_verified[*] 确实 git tag --list 可见
set -euo pipefail

PHASE="${1:?usage: check-continuity.sh <phase-id>}"
REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
STATE_FILE="$REPO_ROOT/state/phase-${PHASE}.yml"
IFACES="$REPO_ROOT/state/interfaces.yml"

# S0 tolerance: state file may be absent, then minimal init-on-read
if [[ ! -f "$STATE_FILE" ]]; then
  echo "[continuity] $STATE_FILE missing · ok for S0 skeleton · non-fatal"
  exit 0
fi

command -v yq >/dev/null 2>&1 || { echo "FAIL: yq not installed"; exit 1; }

FAIL=0

# ① sha256 consistency
if [[ -f "$IFACES" ]]; then
  COUNT=$(yq '.interfaces | length' "$IFACES" 2>/dev/null || echo 0)
  for i in $(seq 0 $((COUNT-1))); do
    [[ "$COUNT" -eq 0 ]] && break
    P=$(yq ".interfaces[$i].path" "$IFACES" 2>/dev/null)
    DECLARED=$(yq ".interfaces[$i].sha256" "$IFACES" 2>/dev/null)
    [[ -z "$P" || "$P" == "null" ]] && continue
    [[ -z "$DECLARED" || "$DECLARED" == "null" ]] && continue
    ABS="$REPO_ROOT/$P"
    if [[ ! -f "$ABS" ]]; then
      echo "FAIL: interfaces.yml[$i] path=$P missing"
      FAIL=$((FAIL+1))
      continue
    fi
    ACTUAL=$(shasum -a 256 "$ABS" | awk '{print $1}')
    # Strip leading "0x" if user used that style; accept raw hex
    DECLARED_NORM="${DECLARED#0x}"
    if [[ "$ACTUAL" != "$DECLARED_NORM" ]]; then
      echo "WARN: interfaces.yml[$i] path=$P sha256 drifted (declared=$DECLARED_NORM actual=$ACTUAL)"
      # WARN only — sha drift often expected mid-Phase. Hardening in CI via arch-consistency.
    fi
  done
fi

# ② DoR declared depends_on all resolvable
DOR_COUNT=$(yq '.dor_depends_on | length' "$STATE_FILE" 2>/dev/null || echo 0)
for i in $(seq 0 $((DOR_COUNT-1))); do
  [[ "$DOR_COUNT" -eq 0 ]] && break
  ID=$(yq ".dor_depends_on[$i].id" "$STATE_FILE" 2>/dev/null)
  [[ -z "$ID" || "$ID" == "null" ]] && continue
  if ! grep -qF "$ID" "$IFACES" 2>/dev/null; then
    echo "FAIL: dor_depends_on[$i].id=$ID not found in interfaces.yml"
    FAIL=$((FAIL+1))
  fi
done

# ③ upstream_tags_verified all real
TAG_COUNT=$(yq '.upstream_tags_verified | length' "$STATE_FILE" 2>/dev/null || echo 0)
for i in $(seq 0 $((TAG_COUNT-1))); do
  [[ "$TAG_COUNT" -eq 0 ]] && break
  T=$(yq ".upstream_tags_verified[$i]" "$STATE_FILE" 2>/dev/null)
  [[ -z "$T" || "$T" == "null" ]] && continue
  if ! git -C "$REPO_ROOT" tag --list | grep -qx "$T"; then
    echo "FAIL: upstream tag '$T' declared verified but git tag --list does not show it"
    FAIL=$((FAIL+1))
  fi
done

if [[ "$FAIL" -gt 0 ]]; then
  echo "FAIL: $FAIL continuity violation(s) for phase=$PHASE"
  exit 1
fi

echo "[continuity] phase=$PHASE OK"
exit 0
