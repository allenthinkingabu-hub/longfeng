#!/usr/bin/env bash
# 落地计划 §16.2 Sd.1 · Style Dictionary 三端一致性断言
# 用法：verify-tokens.sh
# 退出码：0=通过，1=违规
#
# 核心：tokens JSON 改动 → 必须同步 3 个输出 · diff 非空即违规
set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$REPO_ROOT"

UIKIT_DIR="frontend/packages/ui-kit"
TOKENS_DIR="design/system/tokens"

# 1. 检查 JSON 源 6 组齐全
for f in color typography spacing radius shadow motion; do
  [[ -f "$TOKENS_DIR/$f.json" ]] || { echo "FAIL: tokens source missing: $TOKENS_DIR/$f.json"; exit 1; }
done

# 2. 检查输出 3 端齐全
for f in tokens.css tokens.wxss tokens.ts; do
  [[ -f "$UIKIT_DIR/src/$f" ]] || { echo "FAIL: tokens output missing: $UIKIT_DIR/src/$f"; exit 1; }
done

# 3. 重新跑 style-dictionary · 对比 git diff
ORIG_CSS_HASH=$(shasum -a 256 "$UIKIT_DIR/src/tokens.css" | cut -d' ' -f1)
ORIG_WXSS_HASH=$(shasum -a 256 "$UIKIT_DIR/src/tokens.wxss" | cut -d' ' -f1)
ORIG_TS_HASH=$(shasum -a 256 "$UIKIT_DIR/src/tokens.ts" | cut -d' ' -f1)

(cd "$UIKIT_DIR" && npx style-dictionary build --config sd.config.js >/dev/null 2>&1)

NEW_CSS_HASH=$(shasum -a 256 "$UIKIT_DIR/src/tokens.css" | cut -d' ' -f1)
NEW_WXSS_HASH=$(shasum -a 256 "$UIKIT_DIR/src/tokens.wxss" | cut -d' ' -f1)
NEW_TS_HASH=$(shasum -a 256 "$UIKIT_DIR/src/tokens.ts" | cut -d' ' -f1)

if [[ "$ORIG_CSS_HASH" != "$NEW_CSS_HASH" || "$ORIG_WXSS_HASH" != "$NEW_WXSS_HASH" || "$ORIG_TS_HASH" != "$NEW_TS_HASH" ]]; then
  echo "FAIL: tokens JSON 与输出 diff · 重跑 style-dictionary 后 hash 变化 · 提交 style-dictionary 产物"
  echo "  css  old=$ORIG_CSS_HASH new=$NEW_CSS_HASH"
  echo "  wxss old=$ORIG_WXSS_HASH new=$NEW_WXSS_HASH"
  echo "  ts   old=$ORIG_TS_HASH new=$NEW_TS_HASH"
  exit 1
fi

# 4. 检查 --tkn-* 命名空间 · 每端至少 10 条
CSS_COUNT=$(grep -c -- '--tkn-' "$UIKIT_DIR/src/tokens.css")
WXSS_COUNT=$(grep -c -- '--tkn-' "$UIKIT_DIR/src/tokens.wxss")
TS_COUNT=$(grep -c 'Tkn' "$UIKIT_DIR/src/tokens.ts")

[[ "$CSS_COUNT" -ge 10 ]] || { echo "FAIL: tokens.css 少于 10 条 --tkn-* 变量"; exit 1; }
[[ "$WXSS_COUNT" -ge 10 ]] || { echo "FAIL: tokens.wxss 少于 10 条 --tkn-* 变量"; exit 1; }
[[ "$TS_COUNT" -ge 10 ]] || { echo "FAIL: tokens.ts 少于 10 条 Tkn* 常量"; exit 1; }

echo "[verify-tokens] OK · CSS/WXSS/TS 三端一致 · CSS $CSS_COUNT 变量 / WXSS $WXSS_COUNT 变量 / TS $TS_COUNT 常量"
exit 0
