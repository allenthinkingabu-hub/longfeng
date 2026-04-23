#!/usr/bin/env bash
# 落地计划 §1.5 通用约束 #9 · Mutation Kill Rate 硬阈值 ≥ 60%
# 用法：check-test-effectiveness.sh <phase-id>
# 退出码：0=通过，1=违规，2=用法错误
#
# 核心职责（S0 骨架 · 各 Phase 首次调用时补全 Stryker/PITest 实际执行）：
# 1. 识别 Phase 的测试技术栈（后端 JVM → PITest · 前端 TS → Stryker）
# 2. 运行 mutation 并解析 kill rate
# 3. kill rate < 60% → 产 `reports/phase-<phase>-mutation.md` 含剪枝清单（弱测试方法 list）
# 4. kill rate ≥ 60% → exit 0
set -euo pipefail

PHASE="${1:?usage: check-test-effectiveness.sh <phase-id>}"
REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"

# 豁免 Phase（S0/S1/S2/S6/Sd · arch 标 test_effectiveness_exempt: true 或 exempt: true）
ARCH_DOC=""
for f in "$REPO_ROOT/design/arch/${PHASE}.md" "$REPO_ROOT/design/arch/${PHASE}"-*.md; do
  [[ -f "$f" ]] && { ARCH_DOC="$f"; break; }
done
if [[ -n "$ARCH_DOC" ]]; then
  if grep -qE '^(exempt|test_effectiveness_exempt):[[:space:]]*true' "$ARCH_DOC" 2>/dev/null; then
    echo "[test-effectiveness] phase=$PHASE exempted · skipping mutation"
    exit 0
  fi
fi

# S0 骨架：识别技术栈 · 实际 mutation 执行留给各 Phase 首次调用时补
BACKEND_POM="$REPO_ROOT/backend/${PHASE}-service/pom.xml"
FRONTEND_PKG="$REPO_ROOT/frontend/apps/${PHASE}/package.json"
STACK=""
[[ -f "$BACKEND_POM" ]] && STACK="backend-java"
[[ -f "$FRONTEND_PKG" ]] && STACK="${STACK:-frontend-ts}"

if [[ -z "$STACK" ]]; then
  echo "[test-effectiveness] phase=$PHASE no pom/package found · skeleton exit 0 · 各 Phase 首次调用时补"
  exit 0
fi

# TODO: backend-java → mvn -Ppitest · 解析 target/pit-reports/mutations.xml · kill_rate ≥ 60
# TODO: frontend-ts → pnpm stryker run · 解析 reports/mutation/mutation.json · kill_rate ≥ 60
# TODO: kill_rate < 60 → 产 reports/phase-${PHASE}-mutation.md 含弱测试方法剪枝清单
echo "[test-effectiveness] phase=$PHASE stack=$STACK skeleton OK · TODO: 各 Phase 首次调用时补 mutation 实际执行"
exit 0
