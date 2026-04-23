#!/usr/bin/env bash
# 落地计划 §1.9 规则 C · Test Oracle Contract 硬门禁
# 用法：check-oracle-source.sh <phase-id> [--phase-ac | --assertions]
# 退出码：0=通过，1=违规，2=用法错误
#
# 核心职责（S0 骨架 · 各 Phase 首次调用时补全实际扫描逻辑）：
# 1. 若 arch 标 exempt:true 或 oracle_exempt:true → 直接放行（§1.9 规则 D · §1.7 规则 F）
# 2. 非豁免：校验 design/acceptance-criteria-signed.yml 存在 + signed_by 合法
# 3. --phase-ac（DoR 模式）：统计本 Phase 相关 AC 数 + 确认已签字
# 4. --assertions（DoD 模式）：扫 Phase 章节验证步骤里的阈值数字 · 每个都能 grep 到 yml
set -euo pipefail

PHASE="${1:?usage: check-oracle-source.sh <phase-id> [--phase-ac|--assertions]}"
MODE="${2:---assertions}"
REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"

# ---------- 1. 豁免 Phase 直接放行 ----------
# §1.9 规则 D 明确 S0/S2/Sd 不产生业务 AC
# arch front matter 同时标 exempt:true 或 oracle_exempt:true 即豁免
ARCH_DOC=""
for f in "$REPO_ROOT/design/arch/${PHASE}.md" "$REPO_ROOT/design/arch/${PHASE}"-*.md; do
  [[ -f "$f" ]] && { ARCH_DOC="$f"; break; }
done
if [[ -n "$ARCH_DOC" ]]; then
  if grep -qE '^(exempt|oracle_exempt):[[:space:]]*true' "$ARCH_DOC" 2>/dev/null; then
    echo "[oracle-source] phase=$PHASE exempted · skipping (arch front matter 标 exempt/oracle_exempt)"
    exit 0
  fi
fi

# ---------- 2. 非豁免：校验 signed yml ----------
YML="$REPO_ROOT/design/acceptance-criteria-signed.yml"
[[ -f "$YML" ]] || { echo "ERR: $YML missing (did S0 落 template?)"; exit 1; }

command -v yq >/dev/null 2>&1 || { echo "FAIL: yq not installed"; exit 1; }

SIGNED_BY=$(yq '.signed_by' "$YML" | tr -d '"')
if [[ -z "$SIGNED_BY" || "$SIGNED_BY" == "null" ]]; then
  echo "ERR: $YML 未签字 · User 必须在本 Phase DoR 前独立签字（§1.9 规则 B）"
  exit 1
fi
if [[ "$SIGNED_BY" == "builder_agent" || "$SIGNED_BY" == "@builder-agent" ]]; then
  echo "ERR: $YML 由 Builder 自签 · 必须 User 独立签字（§1.9 规则 B）"
  exit 1
fi

if [[ "$MODE" == "--phase-ac" ]]; then
  # DoR 模式：校验本 Phase 相关 AC 签字齐全
  COUNT=$(yq ".criteria[] | select(.phase_owner == \"$PHASE\") | .acceptance // [] | length" "$YML" 2>/dev/null | awk '{s+=$1} END{print s+0}')
  if [[ "${COUNT:-0}" -ge 1 ]]; then
    echo "[oracle-source] phase=$PHASE DoR OK · AC count=$COUNT · signed_by=$SIGNED_BY"
    exit 0
  fi
  echo "ERR: phase=$PHASE 非豁免但在 $YML 中无关联 AC · 请 User 补 signed yml"
  exit 1
fi

# --assertions DoD 模式 · S0 骨架：实际阈值扫描逻辑留给各 Phase DoD 首次调用时补
# TODO: parse 落地计划 <phase> 章节 §X.8 验证步骤 numbered list · 抽取所有阈值
# TODO: 每个阈值必须在 $YML 的 .criteria[].acceptance[].threshold 或 .cross_cutting[].threshold 中 grep 到
echo "[oracle-source] phase=$PHASE DoD skeleton OK · TODO: full threshold scan (各 Phase 首次调用时补)"
exit 0
