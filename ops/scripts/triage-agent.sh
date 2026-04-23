#!/usr/bin/env bash
# 落地计划 §27.3 · v1.9 · Triage Agent wrapper
# 用法：triage-agent.sh <failure_id> [--run-id <e2e_run_id>]
# 退出码：0=分诊完成，1=失败，2=用法错误
#
# 六类分诊（§27.3）：
#   code_bug_backend      断言 stacktrace 指向 backend module → Fix Agent (related_files=Java files)
#   code_bug_frontend     断言 stacktrace 指向 frontend module → Fix Agent (related_files=TSX/CSS files)
#   test_flake            3 次重试 ≥ 2 次过 OR 明显时序 bug → needs-human (打 @flaky quarantine)
#   matrix_ambiguity      断言不明确对应 signed yml threshold → needs-human (User 裁决)
#   infra_issue           CI runner/docker/网络抖 → needs-human (ops)
#   visual_drift          --visual diff 超阈值但业务断言绿 → 人工二选一
#
# confidence 规则：
#   ≥ 0.8  → 自动走对应处置
#   0.6-0.8 → 走对应处置但标 low_confidence: true · User 事后 review
#   < 0.6  → 强制 needs-human
#
# 产物：reports/triage/<failure_id>.yml · schema 见 §27.7
#
# S0 骨架：Triage Agent prompt 编排 + stacktrace 语义匹配留 S9 节点补
set -euo pipefail

FAILURE_ID="${1:?usage: triage-agent.sh <failure_id> [--run-id <e2e_run_id>]}"
RUN_ID=""
if [[ "${2:-}" == "--run-id" ]]; then
  RUN_ID="${3:-}"
fi

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
TRIAGE_YML="$REPO_ROOT/reports/triage/${FAILURE_ID}.yml"
mkdir -p "$(dirname "$TRIAGE_YML")"

# 寻找 failure 源 yml
SRC_YML=""
if [[ -n "$RUN_ID" ]]; then
  SRC_YML="$REPO_ROOT/reports/e2e/${RUN_ID}/failures/${FAILURE_ID}.yml"
else
  SRC_YML=$(find "$REPO_ROOT/reports/e2e" -type f -name "${FAILURE_ID}.yml" 2>/dev/null | head -1)
fi

if [[ -z "$SRC_YML" || ! -f "$SRC_YML" ]]; then
  echo "[triage] WARN: failure source yml not found for $FAILURE_ID · 产 skeleton triage yml"
fi

# S0 骨架 · 产 triage yml 骨架（§27.7 schema）
cat > "$TRIAGE_YML" <<EOF
schema_version: 1.0
failure_id: "${FAILURE_ID}"
ac_id: "TBD"                                     # 从 failure yml 的 @CoversAC 注解读
phase: "TBD"
category: "TBD"                                  # 六类之一
confidence: 0.0                                  # 0.0 代表 Triage Agent 尚未判定
related_files: []                                # Fix Agent 写权限上限
proposed_fix_type: ""
fix_attempts: 0
state: needs-human                               # 骨架默认 needs-human · 等 Triage Agent 覆写
violations: []
user_override: false
accepted_by: ""
accepted_reason: ""
history:
  - at: "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    by: triage-agent
    action: skeleton_created
EOF

echo "[triage] phase=TBD failure=${FAILURE_ID} skeleton OK · $TRIAGE_YML 已产"
echo "[triage] TODO S9 节点补："
echo "  1. 读 $SRC_YML · 解析 stacktrace + @CoversAC 注解 + test name"
echo "  2. 启动独立 Claude/Opus 会话（prompt 含六类定义 + confidence 计算规则）"
echo "  3. 判定 category/confidence/related_files/proposed_fix_type"
echo "  4. 回写 $TRIAGE_YML · state 设为 in_progress/needs-human"
echo "  5. history 追加 action: classified · 供 fix-agent.sh 读"
exit 0
