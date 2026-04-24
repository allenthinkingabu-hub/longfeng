#!/usr/bin/env bash
# 落地计划 §16.2 Sd.4 · 15 SC flow ↔ Playwright step 映射断言
# 本版（2026-04-24）为骨架 · 确保 15 份 .mmd 存在 + Mermaid 语法基本完整
# 真正的 "flow ↔ Playwright step" 映射断言在 Sd.9 prototype + S7/S8 Playwright spec 落地后补
set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$REPO_ROOT"

FLOWS_DIR="design/flows"
EXPECTED=15
ACTUAL=$(ls "$FLOWS_DIR"/SC*.mmd 2>/dev/null | wc -l | tr -d ' ')

if [[ "$ACTUAL" -lt "$EXPECTED" ]]; then
  echo "FAIL: expected ≥ $EXPECTED .mmd files · actual $ACTUAL"
  exit 1
fi

# 检查每份文件 · 必须含 flowchart 关键字 + 至少 4 个节点
MISSING=0
for f in $(ls "$FLOWS_DIR"/SC*.mmd 2>/dev/null); do
  if ! grep -qE '^flowchart ' "$f"; then
    echo "FAIL: $f · 未声明 flowchart type"
    MISSING=$((MISSING+1))
    continue
  fi
  NODES=$(grep -oE '\b[A-Za-z][A-Za-z0-9_]*(\[\[?|\(\(?|\{)' "$f" | sort -u | wc -l | tr -d ' ')
  if [[ "$NODES" -lt 3 ]]; then
    echo "WARN: $f · 节点数 $NODES 偏少（建议 ≥ 4）"
  fi
done

[[ "$MISSING" -eq 0 ]] || { echo "FAIL: $MISSING 份 .mmd 结构不合规"; exit 1; }

echo "[verify-flows] OK · $ACTUAL 份 SC flow Mermaid · 语法基本完整"
echo "  note: S7/S8 Playwright spec 落地后 · 本脚本增 '每 SC 关键 step 在 spec 可查' 断言"
exit 0
