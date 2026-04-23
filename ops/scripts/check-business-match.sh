#!/usr/bin/env bash
# 落地计划 §1.5 通用约束 #11/#12 · SC↔Phase 双向映射 + 业务匹配报告
# 用法：check-business-match.sh [--ownership | --slots <phase> | --match <phase> | --aggregate]
# 退出码：0=通过，1=违规，2=用法错误
#
# 四子命令：
#   --ownership     校验 sc-phase-mapping 15 SC 全覆盖 + 每条含 owner_phases
#   --slots <phase> 校验本 Phase business-analysis.yml 的 sc_covered = mapping owner_phases 含本 phase 的 SC 集合
#   --match <phase> 产 reports/phase-<phase>-business-match.md（Phase 收尾时 Verifier Agent 调用）
#   --aggregate     S9 全项目汇总：15 SC × 所有 AC 的 owner_phases 并集 == 全集
set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
MAPPING="$REPO_ROOT/design/sc-phase-mapping.yml"

usage() {
  echo "usage: check-business-match.sh [--ownership | --slots <phase> | --match <phase> | --aggregate]"
  exit 2
}

CMD="${1:---aggregate}"

case "$CMD" in
  --ownership)
    # 校验 mapping 覆盖 SC-01..SC-15 + 每条含 owner_phases（§4.8 V-S0-15 硬约束）
    [[ -f "$MAPPING" ]] || { echo "FAIL: $MAPPING missing"; exit 1; }
    python3 - "$MAPPING" <<'PY'
import yaml, sys
m = yaml.safe_load(open(sys.argv[1]))
expected = {f'SC-{i:02d}' for i in range(1,16)}
actual = {k for k in m.keys() if k.startswith('SC-')}
missing = expected - actual
if missing:
    sys.stderr.write(f'FAIL: sc-phase-mapping 缺 SC {sorted(missing)}\n'); sys.exit(1)
for sc in expected:
    if 'owner_phases' not in m[sc]:
        sys.stderr.write(f'FAIL: {sc} 缺 owner_phases 键\n'); sys.exit(1)
print(f'OK: sc-phase-mapping 15 SC 完整 · 每条含 owner_phases')
PY
    exit 0
    ;;

  --slots)
    PHASE="${2:?--slots 需要 <phase> 参数}"
    ANALYSIS="$REPO_ROOT/design/analysis/${PHASE}-business-analysis.yml"
    # 豁免 Phase（无 business-analysis.yml · 例如 S0/S1/S2/S6/Sd）直接放行
    if [[ ! -f "$ANALYSIS" ]]; then
      ARCH_DOC=""
      for f in "$REPO_ROOT/design/arch/${PHASE}.md" "$REPO_ROOT/design/arch/${PHASE}"-*.md; do
        [[ -f "$f" ]] && { ARCH_DOC="$f"; break; }
      done
      if [[ -n "$ARCH_DOC" ]] && grep -qE '^exempt:[[:space:]]*true' "$ARCH_DOC" 2>/dev/null; then
        echo "[business-match] phase=$PHASE exempted · skipping slots check"
        exit 0
      fi
      echo "FAIL: $ANALYSIS missing 且 Phase 非豁免"
      exit 1
    fi

    [[ -f "$MAPPING" ]] || { echo "FAIL: $MAPPING missing"; exit 1; }

    python3 - "$ANALYSIS" "$MAPPING" "$PHASE" <<'PY'
import yaml, sys
analysis = yaml.safe_load(open(sys.argv[1]))
mapping  = yaml.safe_load(open(sys.argv[2]))
phase    = sys.argv[3]
declared = set(analysis.get('sc_covered') or [])
expected = {sc for sc, meta in mapping.items()
            if sc.startswith('SC-') and phase in (meta.get('owner_phases') or [])}
if declared != expected:
    sys.stderr.write(f'FAIL: phase={phase} sc_covered={sorted(declared)} != mapping expected={sorted(expected)}\n')
    sys.stderr.write(f'  越界: {sorted(declared - expected)}\n')
    sys.stderr.write(f'  漏题: {sorted(expected - declared)}\n')
    sys.exit(1)
print(f'OK: phase={phase} sc_covered == mapping owner_phases ({sorted(declared)})')
PY
    exit 0
    ;;

  --match)
    PHASE="${2:?--match 需要 <phase> 参数}"
    # S0 骨架：实际 Builder/Verifier/QA/UX 四角色锚点扫描留给各 Phase 首次调用时补
    # TODO: parse design/analysis/<phase>-business-analysis.yml 的 ac_coverage[]
    # TODO: 每个 AC 产一行 · 列 architect/dev/qa/ux(or observable_behavior) 锚点 + 状态 ✅/⚠️/❌
    # TODO: 输出 reports/phase-<phase>-business-match.md
    OUT="$REPO_ROOT/reports/phase-${PHASE}-business-match.md"
    mkdir -p "$(dirname "$OUT")"
    cat > "$OUT" <<EOF
# Phase ${PHASE} Business Match Report · SKELETON

> S0 骨架占位 · Phase 收尾时 Verifier Agent 调用补全四角色锚点
> 对应条款：落地计划 §1.5 通用约束 #12 · §4.8 V-S0-14

| AC | architect_anchor | dev_anchor | qa_anchor | ux/observable | 状态 |
|---|---|---|---|---|---|
| TBD | design/arch/${PHASE}.md | commit:TBD | test:TBD | mockup/observable:TBD | ⚠️ 待 Verifier 补 |

_生成时间：$(date -u +%Y-%m-%dT%H:%M:%SZ) · by check-business-match.sh --match_
EOF
    echo "OK: $OUT 已产（skeleton · Phase 首次调用时 Verifier 补全）"
    exit 0
    ;;

  --aggregate)
    # S9 全项目汇总：所有 phase 的 match 报告聚合 · 15 SC × 所有 AC owner_phases 并集 = 全集
    REPORTS_DIR="$REPO_ROOT/reports"
    if [[ ! -d "$REPORTS_DIR" ]]; then
      # S0 阶段 reports 目录可能尚未创建
      echo "[business-match] 无任何 phase 匹配报告（S9 aggregate 之前为空属正常 · §4.8 V-S0-16）"
      exit 0
    fi
    MATCH_COUNT=$(find "$REPORTS_DIR" -maxdepth 1 -type f -name 'phase-*-business-match.md' 2>/dev/null | wc -l | tr -d ' ')
    if [[ "${MATCH_COUNT:-0}" -eq 0 ]]; then
      echo "[business-match] 无任何 phase 匹配报告（S9 aggregate 之前为空属正常 · §4.8 V-S0-16）"
      exit 0
    fi
    # TODO: S9 节点补全量聚合 · 读 design/sc-phase-mapping.yml 所有 SC × AC owner_phases
    #        与 match 报告交叉验证全集覆盖 · 缺失 SC/AC 即 exit 1
    echo "OK: aggregate skeleton · 发现 $MATCH_COUNT 个 phase 匹配报告 · S9 首次调用时补完整聚合逻辑"
    exit 0
    ;;

  -h|--help) usage ;;
  *) echo "unknown command: $CMD"; usage ;;
esac
