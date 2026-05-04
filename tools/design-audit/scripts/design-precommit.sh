#!/usr/bin/env bash
# Phase D4 · E 机制 · pre-commit design audit (warning-only · 不阻断 commit)
#
# 触发: git commit 时 · 自动检测 staged 的 frontend/apps/h5/src/pages/* 改动
# 行为: 跑 mockup-vs-impl 对应 page · 输出 diff% summary · exit 0 (不阻断)
# 设计: 不阻断开发 · PR-time 才硬阻断 (用 GH workflow + design-pr-screenshots.yml)
#
# 安装: 跑 scripts/install-design-hooks.sh 一次性 copy 到 .git/hooks/pre-commit
# Plan: docs/DESIGN-AUDIT-SYSTEM-PLAN.md §Layer3 E

set -uo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

# 检测 staged 改的 page src 文件
CHANGED_PAGES=$(git diff --cached --name-only --diff-filter=ACMR \
  | grep -E "^frontend/apps/h5/src/pages/[A-Z][^/]+/" \
  | awk -F/ '{print $5}' \
  | sort -u)

if [ -z "$CHANGED_PAGES" ]; then
  exit 0  # 没改 page · 跳过
fi

echo ""
echo "🎨 Design audit on staged page changes (warning-only · 不阻断 commit):"
echo ""

# Page dir name → page_id mapping (跟 mockup-vs-impl.spec.ts PAGES 对齐)
declare -A PAGE_MAP=(
  ["Landing"]="P-LANDING"
  ["Home"]="P-HOME"
  ["Capture"]="P02"
  ["Analyzing"]="P03"
  ["Result"]="P04"
  ["List"]="P05"
  ["Detail"]="P06"
  ["ReviewToday"]="P07"
  ["ReviewExec"]="P08"
  ["ReviewDone"]="P09"
  ["CalendarMonth"]="P10"
  ["EventDetail"]="P11"
  ["Notifications"]="P12"
  ["Settings"]="P13"
  ["GuestCapture"]="P-GUEST-CAPTURE"
  ["Shared"]="P-SHARED"
  ["Auth"]="P00"
)

for dir in $CHANGED_PAGES; do
  page_id="${PAGE_MAP[$dir]:-}"
  if [ -z "$page_id" ]; then
    echo "  ? $dir → 未知 page_id · 跳过 (建议补 PAGE_MAP 映射)"
    continue
  fi

  # 检查 vite 是否在跑 (mockup-diff 需要 :5173)
  if ! curl -sf -o /dev/null --max-time 1 http://localhost:5173/ 2>/dev/null; then
    echo "  ⚠ $dir ($page_id) → vite :5173 未启 · 跳过自动 audit · 提交后请手动:"
    echo "      cd e2e && pnpm e2e:mockup-diff -- --grep $page_id"
    continue
  fi

  echo "  → $dir ($page_id) · 跑 mockup-vs-impl..."
  result=$(cd e2e && pnpm e2e:mockup-diff 2>&1 | grep -E "📊 ${page_id}:" | head -1)
  if [ -n "$result" ]; then
    echo "      $result"
    diff_pct=$(echo "$result" | grep -oE 'diff=[0-9]+\.[0-9]+%' | head -1)
    if [ -n "$diff_pct" ]; then
      pct=$(echo "$diff_pct" | grep -oE '[0-9]+\.[0-9]+')
      if (( $(echo "$pct > 5.0" | bc -l 2>/dev/null) )); then
        echo "      ⚠ $diff_pct > 5% · commit 后建议跑 design-reviewer agent + 修复"
      else
        echo "      ✓ within 5% threshold"
      fi
    fi
  else
    echo "      (no diff data · check vite/baseline 状态)"
  fi
done

echo ""
echo "💡 Tip: 详细 audit 用 'Agent design-reviewer' · 多轮闭环用 'design-audit-orchestrator'"
echo ""

exit 0  # 永远不阻断 commit
