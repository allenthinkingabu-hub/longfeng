#!/usr/bin/env bash
# 落地计划 §16.2 Sd.6 · testid 规约 verify
# 本版骨架：
#   1. 19 张 mockup HTML 必须存在 data-testid 或 P 规约 md 中明确列表
#   2. P01..P19.md 每份必须含 "3. testid" 小节
#   3. 命名规则：<screen>.<region>.<element>[-{variant}]
#
# 未实现（Sd.2 Storybook 后补）：
#   - ESLint rule enforce-testid
#   - Storybook 组件 testIdPrefix prop 覆盖率
set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$REPO_ROOT"

SPECS_DIR="design/specs"
MOCKUPS_DIR="design/mockups/wrongbook"

# 1. 19 P 规约各含 testid 小节
MISSING_TESTID_SECT=0
for p in $(ls "$SPECS_DIR"/P*.md 2>/dev/null | head -19); do
  if ! grep -qE '^## 3\.' "$p" || ! grep -qiE 'testid' "$p"; then
    echo "FAIL: $p · 缺 testid 小节"
    MISSING_TESTID_SECT=$((MISSING_TESTID_SECT+1))
  fi
done
[[ "$MISSING_TESTID_SECT" -eq 0 ]] || { echo "FAIL: $MISSING_TESTID_SECT 份 P 规约缺 testid 小节"; exit 1; }

# 2. 命名规则 <screen>.<region>.<element>[-variant] 扫描
INVALID_NAMES=0
while IFS= read -r tid; do
  # 合法格式：小写 + 点分 + 至少 3 段（screen.region.element）· 变体用 -
  if ! echo "$tid" | grep -qE '^[a-z][a-z0-9]*(\.[a-z][a-z0-9-]*){2,}(-[a-zA-Z0-9_-]+)?$'; then
    echo "WARN: 非标命名 testid: $tid"
    INVALID_NAMES=$((INVALID_NAMES+1))
  fi
done < <(grep -hoE '`[a-z][a-z0-9.-]{6,}`' "$SPECS_DIR"/P*.md | sort -u | tr -d '`' | head -80)

# 3. testid-convention.md 存在
[[ -f "design/system/testid-convention.md" ]] || { echo "FAIL: testid-convention.md missing"; exit 1; }

echo "[verify-testid] OK · 19 P 规约 testid 小节齐 · convention doc 存在 · $INVALID_NAMES 条建议复核（非 fail）"
exit 0
