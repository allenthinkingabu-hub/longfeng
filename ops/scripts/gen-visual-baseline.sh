#!/usr/bin/env bash
# 落地计划 §4.6 · Sd.10 · 决策 C · 视觉回归 baseline 生成
# 用法：gen-visual-baseline.sh [<screen_id> | --all]
# 退出码：0=通过，1=违规，2=用法错误
#
# 核心职责（S0 骨架 · Sd.10 节点执行时补 Playwright 实际编排）：
# 1. Playwright headless Chrome 1440×900 · 固定字体 (Source Han Sans + Roboto fallback)
# 2. 对 design/mockups/**/*.html 或 apps/h5 真实路由截屏
# 3. 输出 design/system/screenshots/baseline/<screen_id>.png
# 4. 更新 design/system/screenshots/baseline/manifest.yml
#    schema: { screen_id / path / category: list|rich_interactive / threshold: 0.03|0.08 / sc_ref / sha256 }
# 5. rebaseline 流程：mockup 修改 → Sd Agent 重跑 → 提 PR（diff 可视化贴图）· User 签字才合
set -euo pipefail

TARGET="${1:---all}"
REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
BASELINE_DIR="$REPO_ROOT/design/system/screenshots/baseline"

mkdir -p "$BASELINE_DIR"

if [[ "$TARGET" == "--all" ]]; then
  echo "[visual-baseline] skeleton OK · TODO: Sd.10 节点补全量 baseline 生成"
  echo "  · Playwright headless 1440×900 · 固定 Source Han Sans + Roboto fallback"
  echo "  · 决策 C 阈值：列表页 3% · 富交互页 8%"
  echo "  · 输出 $BASELINE_DIR/<screen_id>.png + manifest.yml"
else
  echo "[visual-baseline] skeleton OK · target=$TARGET · TODO: 单屏 baseline 生成（Sd.10 节点补）"
fi

# TODO Sd.10 节点补：
# 1. npx playwright install --with-deps chromium
# 2. node scripts/baseline-runner.mjs --screens <list> --viewport 1440x900 --font-fixed
# 3. 每屏产 <screen_id>.png · sha256 入 manifest.yml
# 4. git add design/system/screenshots/baseline/ · commit "baseline: <screen_id> [Sd-10]"
exit 0
