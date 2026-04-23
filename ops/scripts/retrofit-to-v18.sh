#!/usr/bin/env bash
# 落地计划 §26 Retrofit 六步流程启动脚本
# 用法：retrofit-to-v18.sh <phase-id> [--dry-run]
# 退出码：0=通过，1=违规，2=用法错误
#
# 六步流程（§26.1）：
#   Step 1 · 打 <phase>-retrofit-start tag · 扫 arch 是否按 AC 分节
#   Step 2 · 扫 analysis.yml schema_version · 判断是否需 bump 1.0 → 1.1
#   Step 3 · 产 reports/retrofit/<phase>-plan.md · User 审签
#   Step 4 · Builder 按 plan 打 patch
#   Step 5 · Verifier 独立复写 critical AC
#   Step 6 · 跑 V-SX-20 + 打 <phase>-v1.8-compliant tag
#
# S0 骨架（本脚本自身）：Step 1-2 扫描 + Step 3 plan 草稿 · Step 4-6 由 User + Builder 手动驱动
# 扫描产物：reports/retrofit/<phase>-analysis.md（Analyst draft）
#
# Batch 识别：
#   --dry-run 模式下扫全仓 · 列出所有已 s{n}-done 但不合规的 Phase
set -euo pipefail

PHASE="${1:?usage: retrofit-to-v18.sh <phase-id> [--dry-run]}"
DRY_RUN=false
for arg in "${@:2}"; do
  case "$arg" in
    --dry-run) DRY_RUN=true ;;
    *) echo "unknown flag: $arg"; exit 2 ;;
  esac
done

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$REPO_ROOT"

# ---------- 前置：<phase>-done tag 必须存在 ----------
if ! git tag -l | grep -qE "^${PHASE}-done$"; then
  echo "FAIL: phase=$PHASE 未打 ${PHASE}-done tag · 非 Retrofit 对象 · 走 v1.8 原生流程"
  exit 1
fi

# ---------- 豁免 Phase（S0/S2/Sd · C 级无业务 AC）特殊处理 ----------
ARCH_DOC=""
for f in "$REPO_ROOT/design/arch/${PHASE}.md" "$REPO_ROOT/design/arch/${PHASE}"-*.md; do
  [[ -f "$f" ]] && { ARCH_DOC="$f"; break; }
done
IS_EXEMPT=false
if [[ -n "$ARCH_DOC" ]] && grep -qE '^exempt:[[:space:]]*true' "$ARCH_DOC" 2>/dev/null; then
  IS_EXEMPT=true
fi

# ---------- Step 1 · 打 retrofit-start tag（dry-run 跳过）----------
TAG="${PHASE}-retrofit-start"
if ! $DRY_RUN; then
  if git tag -l | grep -qE "^${TAG}$"; then
    echo "[retrofit] ${TAG} 已存在 · 跳过重打"
  else
    git tag "$TAG"
    echo "[retrofit] 打 tag ${TAG} @ $(git rev-parse --short HEAD)"
  fi
fi

# ---------- Step 2 · 扫 arch + analysis schema 合规度 ----------
ARCH_COMPLIANT=false
if $IS_EXEMPT; then
  ARCH_COMPLIANT=true   # 豁免 Phase arch 无需按 AC 分节
elif [[ -n "$ARCH_DOC" ]]; then
  if grep -qE '^## AC: SC-[0-9]+\.AC-[0-9]+' "$ARCH_DOC" 2>/dev/null; then
    ARCH_COMPLIANT=true
  fi
fi

ANALYSIS="$REPO_ROOT/design/analysis/${PHASE}-business-analysis.yml"
ANALYSIS_COMPLIANT=false
if $IS_EXEMPT; then
  ANALYSIS_COMPLIANT=true   # 豁免 Phase 无 business-analysis.yml
elif [[ -f "$ANALYSIS" ]]; then
  SCHEMA=$(yq '.schema_version' "$ANALYSIS" 2>/dev/null | tr -d '"' || echo "")
  [[ "$SCHEMA" == "1.1" ]] && ANALYSIS_COMPLIANT=true
fi

# ---------- Step 3 · 产 analysis.md draft ----------
OUT_DIR="$REPO_ROOT/reports/retrofit"
mkdir -p "$OUT_DIR"
ANALYSIS_DRAFT="$OUT_DIR/${PHASE}-analysis.md"

cat > "$ANALYSIS_DRAFT" <<EOF
# Phase ${PHASE} Retrofit Analysis · Auto-Draft

> By \`retrofit-to-v18.sh ${PHASE}\` · 由本脚本扫出合规缺口 · 供 User 审时参考 · 下一步手工产 plan.md

- is_exempt: $IS_EXEMPT （§1.5 C 级豁免判定）
- arch_compliant: $ARCH_COMPLIANT （§1.5 约束 #13 · 按 AC 分节）
- analysis_compliant: $ANALYSIS_COMPLIANT （schema_version == 1.1 · 含 critical + verification_matrix 五类）
- retrofit_start_tag: $TAG
- base_commit: $(git rev-parse --short HEAD)

## 下一步

1. User 基于此 draft + 本 Phase §4.6/§4.8 产出物清单 · 产 \`reports/retrofit/${PHASE}-plan.md\`
2. Plan 含 Step 1-6 的 S0 特化映射 + Commit 分组 + signed_by/approved_at
3. User 签字后 Builder 按 plan 打 patch · commit 前缀 \`[RETROFIT-v1.8]\`
4. Verifier 独立复写 critical AC（豁免 Phase 为 ∅）
5. 跑 V-SX-20（豁免 Phase 跑 V-SX-11..19 替代）+ 打 \`${PHASE}-v1.8-compliant\` tag

_生成时间：$(date -u +%Y-%m-%dT%H:%M:%SZ) · by retrofit-to-v18.sh_
EOF

echo "[retrofit] phase=$PHASE analysis draft → $ANALYSIS_DRAFT"
echo "[retrofit] arch_compliant=$ARCH_COMPLIANT · analysis_compliant=$ANALYSIS_COMPLIANT · is_exempt=$IS_EXEMPT"

if $DRY_RUN; then
  echo "[retrofit] dry-run 完成 · 无 tag · 下一步：移除 --dry-run 正式启动 Step 1-6"
fi
exit 0
