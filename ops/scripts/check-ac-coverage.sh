#!/usr/bin/env bash
# 落地计划 §1.5 通用约束 #13 · AC 分行三硬约束
# 用法：check-ac-coverage.sh <phase-id> [--arch | --commits | --tests | --visual]
# 退出码：0=通过，1=违规，2=用法错误
#
# 四子命令（六领域 Phase V-SX-20 必跑 · 前端型加 --visual）：
#   --arch    design/arch/<phase>.md 按 ## AC: SC-XX-ACY 分节 + 每节五行齐全 (API/Domain/Event/Error/NFR)
#   --commits git log --since=<retrofit-start> commit message 必带 [SC-XX-ACY] 前缀 (Retrofit Phase 豁免)
#   --tests   测试方法必含 @CoversAC("SC-XX-ACY#<category>.<index>") · matrix 行数 == 方法数
#   --visual  前端 Phase · Playwright 截图 vs design/system/screenshots/baseline/*.png · diff ≤ 3%/8%
#
# S0 骨架：豁免 Phase 直接放行 · 实际扫描逻辑留给各 Phase V-SX-20 首次调用时补
set -euo pipefail

PHASE="${1:?usage: check-ac-coverage.sh <phase-id> [--arch|--commits|--tests|--visual]}"
MODE="${2:---arch}"
REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"

# ---------- 豁免 Phase 直接放行 ----------
ARCH_DOC=""
for f in "$REPO_ROOT/design/arch/${PHASE}.md" "$REPO_ROOT/design/arch/${PHASE}"-*.md; do
  [[ -f "$f" ]] && { ARCH_DOC="$f"; break; }
done
if [[ -n "$ARCH_DOC" ]] && grep -qE '^exempt:[[:space:]]*true' "$ARCH_DOC" 2>/dev/null; then
  echo "[ac-coverage] phase=$PHASE mode=$MODE exempted · skipping"
  exit 0
fi

case "$MODE" in
  --arch)
    # TODO: 非豁免 Phase · 扫 arch.md 每个 ## AC: SC-XX-ACY 节 + 五行齐全
    # grep -c '^## AC: SC-' > 0 · 每节 awk block 内含 API:/Domain:/Event:/Error:/NFR: 五行
    if [[ -z "$ARCH_DOC" ]]; then
      echo "FAIL: phase=$PHASE arch doc missing (expected design/arch/${PHASE}.md or ${PHASE}-*.md)"
      exit 1
    fi
    AC_COUNT=$(grep -cE '^## AC: SC-[0-9]+\.AC-[0-9]+' "$ARCH_DOC" 2>/dev/null || echo 0)
    if [[ "${AC_COUNT:-0}" -eq 0 ]]; then
      echo "FAIL: phase=$PHASE arch doc $ARCH_DOC 未按 ## AC: SC-XX-ACY 分节（§1.5 约束 #13）"
      exit 1
    fi
    # TODO: per-AC 五行齐全验证 · 由各 Phase V-SX-20 首次调用时补完整块解析
    echo "[ac-coverage] phase=$PHASE --arch skeleton OK · AC sections=$AC_COUNT · TODO: per-AC 5-line check"
    exit 0
    ;;

  --commits)
    # Retrofit Phase 传 --since <retrofit-start-commit> 只看增量（§26.2 豁免条款 2）
    SINCE="${3:-}"
    SINCE_OPT=""
    [[ -n "$SINCE" ]] && SINCE_OPT="--since=$SINCE"
    # TODO: git log <SINCE_OPT> · 扫业务 commit message · 每条须含 [SC-XX-ACY] 前缀
    # （Retrofit [RETROFIT-v1.8] / Hotfix [HOTFIX-v1.8] / V19-UPGRADE 等豁免白名单）
    echo "[ac-coverage] phase=$PHASE --commits skeleton OK · TODO: 各 Phase V-SX-20 首次调用时补"
    exit 0
    ;;

  --tests)
    # TODO: 后端 Java grep -rE '@CoversAC\("SC-[0-9]+\.AC-[0-9]+#[a-z_]+\.[0-9]+"\)'
    # TODO: 前端 TS grep -rE '@CoversAC|t\.coversAC\(' (测试 runner adapter)
    # TODO: 交叉：matrix 行数 == @CoversAC 方法数 · 不允许多不允许少
    echo "[ac-coverage] phase=$PHASE --tests skeleton OK · TODO: 各 Phase V-SX-20 首次调用时补"
    exit 0
    ;;

  --visual)
    # TODO: 前端 Phase · Playwright headless 1440×900 固定字体 · 截图与 baseline 对比
    # TODO: diff ≤ 列表页 3% · 富交互页 8%（阈值从 design/system/screenshots/baseline/manifest.yml 读）
    echo "[ac-coverage] phase=$PHASE --visual skeleton OK · TODO: Sd + 各前端 Phase V-SX-20 首次调用时补"
    exit 0
    ;;

  *) echo "unknown mode: $MODE"; exit 2 ;;
esac
