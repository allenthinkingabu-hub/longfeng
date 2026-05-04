#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════
# Phase 1 收尾 · 顺序 merge 5 个 BE sub-agent worktree branch 到 feature/s7-frontend-core
# 顺序: WT5 (gateway · 1 yml) → WT2 (file path) → WT1 (anonymous 大头) → WT3 (wb) → WT4 (ai)
# 每个 merge 前跑该 service mvn test 验绿 · 失败立即 abort
# ═══════════════════════════════════════════════════════════════════
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

# 当前分支必须是 feature/s7-frontend-core
current_branch=$(git rev-parse --abbrev-ref HEAD)
if [ "$current_branch" != "feature/s7-frontend-core" ]; then
  echo "❌ 当前分支 = $current_branch · 必须切到 feature/s7-frontend-core"
  exit 1
fi

# 检查工作目录干净
if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "❌ 工作目录有 uncommitted 变更 · 先 stash 或 commit"
  git status -sb
  exit 1
fi

# Git config zhe.wang (确保 merge commit 也是 zhe.wang)
git config user.name "zhe.wang"
git config user.email "zhe.wang@longfeng.com"

# 每条记录: branch_name|service_to_test|description
WORKTREES=(
  "worktree-agent-a750837b7a2b0aeb6|gateway|WT5 gateway routes realign"
  "worktree-agent-a925f38b003d0041e|file-service|WT2 file-service presign path fix"
  "worktree-agent-a86f55b6240851353|anonymous-service|WT1 anonymous-service Landing/Guest/Analytics"
  "worktree-agent-a1e7263ead9f9895c|wrongbook-service|WT3 wrongbook QuestionDetail (待完成)"
  "worktree-agent-ac6f9d55c040591c3|ai-analysis-service|WT4 ai-analysis SSE+cancel+ai_usage_log (待完成)"
)

echo "═══════════════════════════════════════════════════════════════════"
echo "Phase 1 收尾 · 顺序 merge 5 BE worktree"
echo "═══════════════════════════════════════════════════════════════════"

for entry in "${WORKTREES[@]}"; do
  branch=${entry%%|*}; rest=${entry#*|}
  service=${rest%%|*}; desc=${rest#*|}

  echo ""
  echo "── Merge: $desc ($branch → $current_branch) ──"

  # branch 存在?
  if ! git rev-parse --verify "$branch" > /dev/null 2>&1; then
    echo "  ⚠️  branch 不存在 · 跳过 (sub-agent 未完成)"
    continue
  fi

  # 看 branch HEAD 是否含 zhe.wang commit
  zhe_commits=$(git log "$branch" --author=zhe.wang --not "$current_branch" --oneline | wc -l)
  if [ "$zhe_commits" -eq 0 ]; then
    echo "  ⚠️  branch 无 zhe.wang commit · 跳过 (sub-agent 未提交)"
    continue
  fi
  echo "  待 merge $zhe_commits zhe.wang commit(s):"
  git log "$branch" --author=zhe.wang --not "$current_branch" --oneline | sed 's/^/    /'

  # 尝试 merge (no-ff 保留 merge commit)
  if git merge --no-ff "$branch" -m "merge: $desc

Sub-agent worktree: $branch
Service: $service

Co-Authored-By: zhe.wang <zhe.wang@longfeng.com>"; then
    echo "  ✅ merge OK"
  else
    echo "  ❌ merge conflict · 手动解决后 git merge --continue"
    git merge --abort
    exit 1
  fi

  # 跑 service IT 验
  echo "  跑 mvn test -pl backend/$service ..."
  if (cd backend && mvn -q -pl "$service" test); then
    echo "  ✅ $service tests PASS"
  else
    echo "  ❌ $service tests FAIL · merge 已合 · 你需手动 fix 或 git reset --hard HEAD~1 回滚"
    exit 1
  fi
done

echo ""
echo "═══════════════════════════════════════════════════════════════════"
echo "✅ Phase 1 收尾完成。已 merge 的 worktree 全 IT 通过。"
echo "下一步: 跑 ./qa/scripts/start-fullstack.sh 进 Phase 2"
echo "═══════════════════════════════════════════════════════════════════"
git log --oneline -10
