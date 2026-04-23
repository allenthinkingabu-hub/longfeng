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

# 识别技术栈：项目 backend module 名是业务域名（wrongbook/ai-analysis/review-plan/file/anonymous/gateway/common）
# 不是 <phase>-service 命名 · 不能按 phase_id 硬编码路径
# 改为：检测 backend/ 和 frontend/ 是否含有非空的业务代码 · 识别项目级技术栈
BACKEND_HAS_POM=false
FRONTEND_HAS_PKG=false
[[ -n "$(find "$REPO_ROOT/backend" -maxdepth 2 -name 'pom.xml' -not -path "*/common/*" 2>/dev/null | head -1)" ]] && BACKEND_HAS_POM=true
[[ -f "$REPO_ROOT/frontend/package.json" ]] && FRONTEND_HAS_PKG=true

STACK=""
$BACKEND_HAS_POM  && STACK="${STACK}backend-java "
$FRONTEND_HAS_PKG && STACK="${STACK}frontend-ts"
STACK="${STACK:-unknown}"

# S0 骨架：实际 mutation 执行留给各 Phase 首次调用时补
# 每 Phase 的测试文件实际路径由 Phase 自己的 pom/package 声明 · 本脚本不猜
echo "WARN: [test-effectiveness] phase=$PHASE stack=$STACK · 本脚本仍是 SKELETON · exit 0 仅表示脚手架可执行 · 实际 mutation 未跑"
echo "  · backend-java 技术栈：各 Phase V-SX-13 首次调用时补 mvn -Ppitest · 解析 target/pit-reports/mutations.xml · kill_rate ≥ 60"
echo "  · frontend-ts 技术栈：各前端 Phase 首次调用时补 pnpm stryker run · 解析 reports/mutation/mutation.json · kill_rate ≥ 60"
echo "  · 请调用者不要把本次 exit 0 解读为真实合规"
exit 0
