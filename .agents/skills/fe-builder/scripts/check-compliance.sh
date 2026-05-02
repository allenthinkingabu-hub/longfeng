#!/usr/bin/env bash
# fe-builder · compliance check
# Usage: bash check-compliance.sh <CSS_FILE> <TSX_FILE>

CSS_FILE="$1"
TSX_FILE="$2"

if [[ -z "$CSS_FILE" || -z "$TSX_FILE" ]]; then
  echo "Usage: check-compliance.sh <CSS_FILE> <TSX_FILE>"
  exit 1
fi

PASS=0
FAIL=0

check() {
  local label="$1"
  local result="$2"  # "pass" or "fail"
  local detail="$3"
  if [[ "$result" == "pass" ]]; then
    echo "  ✅ $label"
    PASS=$((PASS+1))
  else
    echo "  ❌ $label"
    [[ -n "$detail" ]] && echo "     $detail"
    FAIL=$((FAIL+1))
  fi
}

echo ""
echo "=== fe-builder compliance check ==="
echo "  CSS : $CSS_FILE"
echo "  TSX : $TSX_FILE"
echo ""

# Gate 1: No hardcoded hex colors in CSS (excluding local var definition lines and comments)
echo "[ Gate 1 ] 零硬编码色值 (CSS)"
# Find hex colors that are NOT inside a CSS variable definition (--var: #...) or a comment
HARD_COLORS=$(grep -n '#[0-9a-fA-F]\{3,6\}\b\|rgb(\|hsl(' "$CSS_FILE" 2>/dev/null \
  | grep -v -E '^[0-9]+:\s*[/*]' \
  | grep -v -E '^[0-9]+:\s*--[a-zA-Z]')

if [[ -z "$HARD_COLORS" ]]; then
  check "无品牌色硬编码" "pass"
else
  HARD_COUNT=$(echo "$HARD_COLORS" | wc -l | tr -d ' ')
  check "无品牌色硬编码" "fail" "$HARD_COUNT 处疑似硬编码色：$(echo "$HARD_COLORS" | head -3 | awk -F: '{print $1": "$2}' | tr '\n' ' ')"
fi

# Gate 2: No legacy iOS color variables
echo ""
echo "[ Gate 2 ] 无旧 iOS 色变量 (CSS)"
LEGACY_VARS=$(grep -n 'var(--blue)\|var(--red)\|var(--green)\|var(--orange)\|var(--indigo)\|var(--yellow)\b' "$CSS_FILE" 2>/dev/null)
if [[ -z "$LEGACY_VARS" ]]; then
  check "无 var(--blue/red/green) 等旧变量" "pass"
else
  LEGACY_COUNT=$(echo "$LEGACY_VARS" | wc -l | tr -d ' ')
  check "无 var(--blue/red/green) 等旧变量" "fail" "$LEGACY_COUNT 处：$(echo "$LEGACY_VARS" | head -3 | awk -F: '{print $1": "$2}' | tr '\n' ' ')"
fi

# Gate 3: No hardcoded iOS colors in TSX
echo ""
echo "[ Gate 3 ] 无内联 iOS 色 (TSX)"
INLINE_COLORS=$(grep -n "style={{.*color:\s*'#[0-9a-fA-F]\{3,6\}'\|stroke=\"#[0-9a-fA-F]\{3,6\}\"" "$TSX_FILE" 2>/dev/null \
  | grep -v "stroke=\"#fff\"\|stroke='#fff'" )
if [[ -z "$INLINE_COLORS" ]]; then
  check "无内联品牌色 (白色 SVG 图标除外)" "pass"
else
  INLINE_COUNT=$(echo "$INLINE_COLORS" | wc -l | tr -d ' ')
  check "无内联品牌色" "fail" "$INLINE_COUNT 处：$(echo "$INLINE_COLORS" | head -3 | awk -F: '{print $1": "$2}' | tr '\n' ' ')"
fi

# Gate 4: Testid presence
echo ""
echo "[ Gate 4 ] Testid 存在 (TSX)"
TESTID_COUNT=$(grep -c 'data-testid' "$TSX_FILE" 2>/dev/null || echo 0)
if [[ "$TESTID_COUNT" -gt 0 ]]; then
  check "data-testid 存在 ($TESTID_COUNT 处)" "pass"
else
  check "data-testid 存在" "fail" "TSX 中无任何 data-testid"
fi

# Summary
echo ""
echo "=== 结果 ==="
echo "  通过：$PASS / $((PASS+FAIL))"
if [[ "$FAIL" -gt 0 ]]; then
  echo "  ❌ 未通过：$FAIL 项，请修复后再 commit"
  exit 1
else
  echo "  ✅ 全部通过，可以 commit"
  exit 0
fi
