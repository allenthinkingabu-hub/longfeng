#!/usr/bin/env bash
# Phase D4 · E 机制安装脚本
# 把 scripts/design-precommit.sh 装到 .git/hooks/pre-commit
# 一次性安装 · 之后 git commit 自动跑 design audit (warning-only)
#
# 用法: bash scripts/install-design-hooks.sh

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
HOOK_FILE="$REPO_ROOT/.git/hooks/pre-commit"
SOURCE="$REPO_ROOT/scripts/design-precommit.sh"

if [ ! -f "$SOURCE" ]; then
  echo "❌ $SOURCE 不存在"
  exit 1
fi

# 如果已有 pre-commit hook · 备份
if [ -f "$HOOK_FILE" ]; then
  if grep -q "design-precommit.sh" "$HOOK_FILE" 2>/dev/null; then
    echo "✓ pre-commit hook 已含 design-precommit · 跳过"
    exit 0
  fi
  cp "$HOOK_FILE" "$HOOK_FILE.backup-$(date +%s)"
  echo "📋 已备份现有 hook 到 $HOOK_FILE.backup-*"
fi

# 写 hook (调用 design-precommit.sh · 任何已有 hook 内容追加之前)
cat > "$HOOK_FILE" <<'HOOK_EOF'
#!/usr/bin/env bash
# Auto-installed by scripts/install-design-hooks.sh
# Phase D4 · E 机制 · design audit warning-only

REPO_ROOT="$(git rev-parse --show-toplevel)"
bash "$REPO_ROOT/scripts/design-precommit.sh"

# 调用其他 pre-commit 逻辑 (如果有) · 在此处加
exit 0
HOOK_EOF

chmod +x "$HOOK_FILE"
chmod +x "$SOURCE"
echo "✓ pre-commit hook 已安装到 $HOOK_FILE"
echo "✓ 触发条件: staged 改了 frontend/apps/h5/src/pages/[A-Z]*"
echo "✓ 模式: warning-only (不阻断 commit · PR-time 才硬阻断 GH workflow)"
echo ""
echo "卸载: rm $HOOK_FILE"
