#!/usr/bin/env bash
# Sd.7 · G3 硬门闸 · axe-core 0 violations over 80 stories
# Usage:
#   bash scripts/verify-a11y.sh           # 完整：build storybook + test-storybook
#   bash scripts/verify-a11y.sh --dry     # 骨架检查（计数 stories ≥ 80）
set -euo pipefail
REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$REPO_ROOT"

MODE="${1:-full}"

UIK_DIR="frontend/packages/ui-kit"
STORIES_DIR="$UIK_DIR/src/components"

# 骨架断言 · 80 stories 覆盖 20 组件
STORY_COUNT=$(grep -hE '^export const [A-Z]' "$STORIES_DIR"/*.stories.tsx 2>/dev/null | wc -l | tr -d ' ')
FILE_COUNT=$(ls "$STORIES_DIR"/*.stories.tsx 2>/dev/null | wc -l | tr -d ' ')

if [[ "$STORY_COUNT" -lt 80 ]]; then
  echo "FAIL: stories exports $STORY_COUNT < 80"
  exit 1
fi
if [[ "$FILE_COUNT" -ne 20 ]]; then
  echo "FAIL: stories files $FILE_COUNT ≠ 20"
  exit 1
fi
echo "[verify-a11y] stories skeleton: $FILE_COUNT files · $STORY_COUNT exports ✓"

if [[ "$MODE" == "--dry" ]]; then
  echo "[verify-a11y] dry mode · skip axe run"
  exit 0
fi

# 完整模式 · 需 Node 20 + pnpm + Storybook 依赖装好
cd "$UIK_DIR"
if [[ ! -d node_modules/@storybook ]]; then
  echo "FAIL: Storybook deps not installed · run 'pnpm install' first"
  exit 1
fi

# build static storybook
pnpm run storybook:build >/tmp/sb-build.log 2>&1 || { echo "FAIL: storybook build"; tail -40 /tmp/sb-build.log; exit 1; }
echo "[verify-a11y] storybook built ✓"

# serve + test-runner（用 npx -y 拉 http-server · curl 轮询代替 wait-on）
npx -y http-server storybook-static -p 6007 -s >/tmp/sb-serve.log 2>&1 &
SERVE_PID=$!
trap 'kill $SERVE_PID 2>/dev/null || true' EXIT

READY=0
for i in $(seq 1 30); do
  if curl -fsS -o /dev/null http://127.0.0.1:6007/iframe.html 2>/dev/null; then
    READY=1
    break
  fi
  sleep 1
done
if [[ "$READY" -ne 1 ]]; then
  echo "FAIL: static server not ready after 30s"
  tail -20 /tmp/sb-serve.log
  exit 1
fi
echo "[verify-a11y] static server ready on :6007 ✓"

# 运行 axe 扫描 · 0 violations 硬门闸
set +e
pnpm exec test-storybook --url http://127.0.0.1:6007 --maxWorkers=2 2>&1 | tee /tmp/sb-axe.log
RC=${PIPESTATUS[0]}
set -e

if [[ "$RC" -ne 0 ]]; then
  echo "FAIL: axe violations detected · 见 /tmp/sb-axe.log"
  exit 1
fi
echo "[verify-a11y] G3 axe 0 violations ✓ · 80 stories × WCAG 2A/2AA"
exit 0
