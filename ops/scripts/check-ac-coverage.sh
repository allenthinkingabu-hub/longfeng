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
    # 非豁免 Phase · 扫 arch.md 每个 ## AC: SC-XX-ACY 节 + 五行齐全
    if [[ -z "$ARCH_DOC" ]]; then
      echo "FAIL: phase=$PHASE arch doc missing (expected design/arch/${PHASE}.md or ${PHASE}-*.md)"
      exit 1
    fi
    # 注意：不要用 `|| echo 0` · grep -c 在无匹配时返回 0 且 exit 1 · 与 echo 叠成 "0\n0" 破坏数字比较
    AC_COUNT=$(grep -cE '^## AC: SC-[0-9]+\.AC-[0-9]+' "$ARCH_DOC" 2>/dev/null) || AC_COUNT=0
    AC_COUNT="${AC_COUNT:-0}"
    if [[ "$AC_COUNT" -eq 0 ]]; then
      echo "FAIL: phase=$PHASE arch doc $ARCH_DOC 未按 ## AC: SC-XX-ACY 分节（§1.5 约束 #13）"
      exit 1
    fi
    # 五行齐全检查（每个 AC 节必含 API: / Domain: / Event: / Error: / NFR:）
    MISS_LINES=0
    while IFS= read -r AC_HEADER; do
      [[ -z "$AC_HEADER" ]] && continue
      # 抽取该 AC 节到下一个 ## 或 EOF 之间的 body
      AC_BODY=$(awk -v hdr="$AC_HEADER" '
        $0 == hdr { in_ac=1; next }
        in_ac && /^## / { exit }
        in_ac { print }
      ' "$ARCH_DOC")
      for line_key in "API:" "Domain:" "Event:" "Error:" "NFR:"; do
        if ! echo "$AC_BODY" | grep -qE "^-[[:space:]]+\*?\*?${line_key}" 2>/dev/null \
          && ! echo "$AC_BODY" | grep -qE "^[[:space:]]*${line_key}" 2>/dev/null; then
          echo "MISS: $AC_HEADER 缺 '${line_key}' 行（§1.5 约束 #13 要求 API/Domain/Event/Error/NFR 五行齐全）"
          MISS_LINES=$((MISS_LINES+1))
        fi
      done
    done < <(grep -E '^## AC: SC-[0-9]+\.AC-[0-9]+' "$ARCH_DOC" 2>/dev/null)
    if [[ "$MISS_LINES" -gt 0 ]]; then
      echo "FAIL: phase=$PHASE arch $ARCH_DOC 有 $MISS_LINES 处五行不齐（AC sections=$AC_COUNT）"
      exit 1
    fi
    echo "[ac-coverage] phase=$PHASE --arch OK · AC sections=$AC_COUNT · 五行齐全"
    exit 0
    ;;

  --commits)
    # Retrofit Phase 传 --since <retrofit-start-commit> 只看增量（§26.2 豁免条款 2）
    SINCE="${3:-}"
    # S0 骨架：commit 前缀扫描的完整逻辑（SC-XX-ACY 前缀判定 + retrofit/hotfix 白名单）留各 Phase V-SX-20 首次调用时补
    echo "WARN: [ac-coverage] phase=$PHASE --commits · 本子命令仍是 SKELETON · exit 0 仅表示脚手架可执行 · 实际 commit 前缀扫描未做"
    echo "  · 非豁免 Phase 的 V-SX-20 首次调用时必须补完整逻辑：git log ${SINCE:+--since=$SINCE} · 每业务 commit 必含 [SC-XX-ACY] 前缀"
    echo "  · 请调用者不要把本次 exit 0 解读为真实合规"
    exit 0
    ;;

  --tests)
    # S0 骨架：@CoversAC 扫描 + matrix 行数等式留各 Phase V-SX-20 首次调用时补
    echo "WARN: [ac-coverage] phase=$PHASE --tests · 本子命令仍是 SKELETON · exit 0 仅表示脚手架可执行 · 实际 @CoversAC 扫描未做"
    echo "  · 非豁免 Phase 的 V-SX-20 首次调用时必须补完整逻辑：扫测试方法 @CoversAC 注解 · matrix 行数 == 方法数"
    echo "  · 请调用者不要把本次 exit 0 解读为真实合规"
    exit 0
    ;;

  --visual)
    # S0 骨架：Playwright diff vs baseline 留 Sd.10 + 前端 Phase V-SX-20 首次调用时补
    echo "WARN: [ac-coverage] phase=$PHASE --visual · 本子命令仍是 SKELETON · exit 0 仅表示脚手架可执行 · 实际视觉 diff 未做"
    echo "  · Sd.10 + 前端 Phase（S7/S8/S11 前端 AC）V-SX-20 首次调用时必须补：Playwright headless 1440×900 · diff ≤ 3%/8%"
    echo "  · 请调用者不要把本次 exit 0 解读为真实合规"
    exit 0
    ;;

  *) echo "unknown mode: $MODE"; exit 2 ;;
esac
