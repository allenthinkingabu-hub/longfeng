#!/usr/bin/env bash
# 落地计划 §1.5 通用约束 #14 · §26 · Verifier Agent 独立复写 wrapper
# 用法：run-verifier.sh <phase-id> [--sample-strategy default|critical-only|full]
# 退出码：0=通过，1=违规，2=用法错误
#
# 核心职责（S0 骨架 · 各 Phase V-SX-20 首次调用时补 Verifier Agent prompt 编排）：
# - default        : critical=true AC 100% 复写 · critical=false AC 按 ≥30% 抽样
# - critical-only  : 只复写 critical=true AC（轻量回归用）
# - full           : 全部 AC 100% 复写（§26 Retrofit 专用 · 豁免条款 4）
#
# Verifier Agent 启动 prompt 硬约束（§1.9 规则 E · 禁止 oracle 污染）：
#   只可引用：① 本 Phase 章节 11 字段 · ② design/arch/<phase>.md · ③ acceptance-criteria-signed.yml
#   禁止引用 Builder Agent 的代码 / 测试文件
set -euo pipefail

PHASE="${1:?usage: run-verifier.sh <phase-id> [--sample-strategy default|critical-only|full]}"
STRATEGY_FLAG="${2:-}"
STRATEGY="default"
if [[ "$STRATEGY_FLAG" == "--sample-strategy" ]]; then
  STRATEGY="${3:-default}"
fi

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"

# ---------- 豁免 Phase 直接放行 ----------
ARCH_DOC=""
for f in "$REPO_ROOT/design/arch/${PHASE}.md" "$REPO_ROOT/design/arch/${PHASE}"-*.md; do
  [[ -f "$f" ]] && { ARCH_DOC="$f"; break; }
done
if [[ -n "$ARCH_DOC" ]] && grep -qE '^exempt:[[:space:]]*true' "$ARCH_DOC" 2>/dev/null; then
  echo "[verifier] phase=$PHASE exempted · no business AC · skipping"
  exit 0
fi

ANALYSIS="$REPO_ROOT/design/analysis/${PHASE}-business-analysis.yml"
if [[ ! -f "$ANALYSIS" ]]; then
  echo "FAIL: $ANALYSIS missing · 非豁免 Phase 必须先落 0.0.5 业务分析 YAML"
  exit 1
fi

# TODO: 各 Phase V-SX-20 首次调用时补：
# 1. 读 $ANALYSIS · 按 strategy 筛 critical AC 清单
# 2. 启动独立 Claude/Opus 会话 · 注入 prompt (arch.md + acceptance-criteria-signed.yml + 本 Phase 章节)
# 3. Verifier 生成独立测试集 · 跑（后端 mvn test / 前端 pnpm test）· 必须绿
# 4. Verifier 测试红但 Builder 测试绿 → R11/R15 联合 · 走 Hotfix（§26.2 豁免 4）
# 5. 产 reports/phase-${PHASE}-verifier.md 含 strategy/AC 清单/复写通过率/红灯清单

OUT="$REPO_ROOT/reports/phase-${PHASE}-verifier.md"
mkdir -p "$(dirname "$OUT")"
cat > "$OUT" <<EOF
# Phase ${PHASE} Verifier Report · SKELETON

> S0 骨架占位 · 各 Phase V-SX-20 首次调用时补 Verifier Agent 实际编排
> 对应条款：落地计划 §1.5 通用约束 #14 · §26 Retrofit Step 5 · §27 E2E 闭环

- strategy: $STRATEGY
- critical_ac_count: TBD
- rewritten_ac_count: TBD
- pass_count: TBD
- fail_count: TBD

_生成时间：$(date -u +%Y-%m-%dT%H:%M:%SZ) · by run-verifier.sh_
EOF
echo "[verifier] phase=$PHASE strategy=$STRATEGY skeleton OK · $OUT 已产 · TODO: 实际 Verifier 会话编排"
exit 0
