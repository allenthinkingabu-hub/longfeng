#!/usr/bin/env bash
# 落地计划 §27.4 · v1.9 · Fix Agent wrapper（related_files 写权限限定 · 循环上限 3）
# 用法：fix-agent.sh <failure_id>
# 退出码：0=修复完成（回跑绿），1=失败或需升级 needs-human，2=用法错误
#
# 权限硬约束（§27.4 · 防 R16）：
#   - 写权限限定 triage.related_files 清单 · 越界写 → git hooks 拒绝 + 记入 violations
#   - commit message 强制 `[triage/<failure_id>][SC-XX-ACY] fix: <concise>` 双前缀
#   - 循环防护：同一 AC fix_attempts > 3 → 强制升级 needs-human-accepted
#   - 回跑要求：修完必须本地回跑失败用例 + run-verifier.sh + check-ac-coverage.sh 三合一绿
#
# S0 骨架：Fix Agent prompt 编排 + pre-commit 越界拦截留 S9 节点补
set -euo pipefail

FAILURE_ID="${1:?usage: fix-agent.sh <failure_id>}"
REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
TRIAGE_YML="$REPO_ROOT/reports/triage/${FAILURE_ID}.yml"

[[ -f "$TRIAGE_YML" ]] || { echo "FAIL: $TRIAGE_YML missing · 先跑 triage-agent.sh $FAILURE_ID"; exit 1; }

command -v yq >/dev/null 2>&1 || { echo "FAIL: yq not installed"; exit 1; }

STATE=$(yq '.state' "$TRIAGE_YML" | tr -d '"')
CATEGORY=$(yq '.category' "$TRIAGE_YML" | tr -d '"')
FIX_ATTEMPTS=$(yq '.fix_attempts' "$TRIAGE_YML")
CONFIDENCE=$(yq '.confidence' "$TRIAGE_YML")

# 循环上限 3（§27.4）
if (( FIX_ATTEMPTS >= 3 )); then
  yq -i '.state = "needs-human-accepted"' "$TRIAGE_YML"
  yq -i '.history += [{"at": "'"$(date -u +%Y-%m-%dT%H:%M:%SZ)"'", "by": "fix-agent", "action": "escalated_fix_attempts_exceeded"}]' "$TRIAGE_YML"
  echo "[fix-agent] phase=TBD failure=${FAILURE_ID} fix_attempts=$FIX_ATTEMPTS ≥ 3 · 强制升级 needs-human-accepted"
  exit 1
fi

# 低置信度不自动修（§27.3）
if python3 -c "import sys; sys.exit(0 if float('$CONFIDENCE') < 0.6 else 1)"; then
  echo "[fix-agent] phase=TBD failure=${FAILURE_ID} confidence=$CONFIDENCE < 0.6 · 拒绝自动修 · 升级 needs-human"
  exit 1
fi

# 非 code_bug_* 类别不自动修
if [[ "$CATEGORY" != "code_bug_backend" && "$CATEGORY" != "code_bug_frontend" ]]; then
  echo "[fix-agent] phase=TBD failure=${FAILURE_ID} category=$CATEGORY · 非 code_bug · 不自动修"
  exit 0
fi

echo "[fix-agent] phase=TBD failure=${FAILURE_ID} skeleton OK · TODO S9 节点补："
echo "  1. 读 $TRIAGE_YML related_files · 构造写权限白名单"
echo "  2. 启动独立 Claude/Opus 会话（prompt 含 AC + 失败 stacktrace + related_files 约束）"
echo "  3. Fix Agent 提交 patch · commit msg 强制 [triage/${FAILURE_ID}][SC-XX-ACY] fix: <summary>"
echo "  4. pre-commit hook 校验越界写 · 超白名单即拒收"
echo "  5. fix_attempts += 1 · 回跑 e2e-runner.sh 原失败用例 + run-verifier.sh + check-ac-coverage.sh"
echo "  6. 三合一绿 → state=resolved · 回写 $TRIAGE_YML"

yq -i '.fix_attempts += 1' "$TRIAGE_YML"
yq -i '.history += [{"at": "'"$(date -u +%Y-%m-%dT%H:%M:%SZ)"'", "by": "fix-agent", "action": "skeleton_invoked"}]' "$TRIAGE_YML"
exit 0
