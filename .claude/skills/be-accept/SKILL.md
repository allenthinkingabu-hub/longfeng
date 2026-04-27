---
name: be-accept
description: >
  AI 后端高保真开发的 Stage 3 三维验收工作流。
  从三个独立维度验证后端实现：维度 A（接口契约形状）/ 维度 B（业务行为正确性）/ 维度 C（架构合规）。
  读取 be-preflight 产出的三份施工图（be-build-spec.json / business-rule-cards.json /
  arch-constraints.json），运行 IT 测试和静态扫描，输出 reports/phase-<phase>-be-acceptance.md。
  触发场景：用户说 "/be-accept <phase>"、"验收后端"、"运行三维验收"、"be-accept"，
  或 be-builder 所有层 commit 完成后。
---

# be-accept · 三维验收

## 目标

从三个独立维度验证后端服务，确保接口形状、业务行为、架构实现同时达标：

| 维度 | 来源 | 执行方式 | 判定依据 |
|---|---|---|---|
| **A · 接口契约** | `be-build-spec.json` `_api_contracts` | mvn verify + OpenAPI 路径/字段检查 | 端点数 ≥ 预期、字段名/类型/HTTP 状态码符合契约 |
| **B · 业务行为** | `business-rule-cards.json` | IT 测试（`*IT.java`）+ 场景断言 | IT 全绿 · 每条 Rule Card 有对应测试覆盖 |
| **C · 架构合规** | `arch-constraints.json` | 静态 grep 扫描 | 全部 verification 命令返回 0，无违规 |

## 输入

| 参数 | 说明 | 示例 |
|---|---|---|
| `<phase>` | Phase ID | s3 / s4 / s5 / s6 |
| `[--dry-run]` | 只打印扫描计划，不实际执行（可选） | `--dry-run` |

施工图文件（be-preflight 产出，必须存在）：

```
design/tasks/preflight/<phase>-be-build-spec.json
design/tasks/preflight/<phase>-business-rule-cards.json
design/tasks/preflight/<phase>-arch-constraints.json
```

## Phase → 服务映射

| Phase | 服务目录 | Java 包根 | IT 测试类模式 |
|---|---|---|---|
| s3 | `backend/wrongbook-service` | `com.longfeng.wrongbook` | `*IT.java` |
| s4 | `backend/ai-analysis-service` | `com.longfeng.analysis` | `*IT.java` |
| s5 | `backend/review-plan-service` | `com.longfeng.review` | `*IT.java` |
| s6 | `backend/file-service` | `com.longfeng.file` | `*IT.java` |

## ⚠️ Context 控制（每次必须从磁盘读，不靠记忆）

Step 0 是强制首步骤——所有约束必须从 JSON 文件重新读取，不使用对话历史里"记得的"信息。

---

## 执行步骤

### Step 0 · 读取施工图（从磁盘）

```bash
echo "=== be-accept Context: 施工图摘要 ==="

# 1. 端点清单 + 预期数
EXPECTED_ENDPOINTS=$(jq '._api_contracts.endpoints | length' \
  design/tasks/preflight/<phase>-be-build-spec.json)
echo "预期端点数：$EXPECTED_ENDPOINTS"
jq '._api_contracts.endpoints | map({method,path})' \
  design/tasks/preflight/<phase>-be-build-spec.json

# 2. 业务规则卡数量
RULE_COUNT=$(python3 -c \
  "import json; print(len(json.load(open('design/tasks/preflight/<phase>-business-rule-cards.json'))))")
echo "业务规则卡数：$RULE_COUNT"

# 3. 架构约束（verification 命令列表）
echo ""
echo "=== 架构约束 verification 列表 ==="
jq '{orm,messaging,id_strategy,state_machine,transaction,soft_delete,optimistic_lock}' \
  design/tasks/preflight/<phase>-arch-constraints.json
```

---

### Step 1 · 前置检查

```bash
# be-builder Layer 4 必须已 commit（含 IT 全绿）
git log --oneline | grep -q "feat(<phase>-L[45])" \
  || { echo "❌ be-builder L4/L5 commit 未找到，请先完成 be-builder <phase>"; exit 1; }

# 施工图文件必须存在
for f in be-build-spec.json business-rule-cards.json arch-constraints.json; do
  [ -f "design/tasks/preflight/<phase>-$f" ] \
    || { echo "❌ 缺少 design/tasks/preflight/<phase>-$f"; exit 1; }
done

echo "✅ 前置检查通过"
```

---

### Step 2 · 维度 A · 接口契约验收

#### Step 2a · 运行 IT（契约 + 冒烟）

```bash
# 运行契约 IT 和冒烟 IT（不运行全量，定向快速）
mvn -f backend/pom.xml -pl <service-name> -am -pl '!common' verify 2>&1 \
  | tee /tmp/be-accept-<phase>-it.log

IT_EXIT=${PIPESTATUS[0]}
echo "IT 退出码：$IT_EXIT"
```

从 log 提取结果：

```bash
grep "Tests run:" /tmp/be-accept-<phase>-it.log | tail -5
TOTAL_TESTS=$(grep "Tests run:" /tmp/be-accept-<phase>-it.log | tail -1 | grep -oE "Tests run: [0-9]+" | grep -oE "[0-9]+")
TOTAL_ERRORS=$(grep "Tests run:" /tmp/be-accept-<phase>-it.log | tail -1 | grep -oE "Errors: [0-9]+" | grep -oE "[0-9]+")
TOTAL_FAILURES=$(grep "Tests run:" /tmp/be-accept-<phase>-it.log | tail -1 | grep -oE "Failures: [0-9]+" | grep -oE "[0-9]+")
echo "总测试数：$TOTAL_TESTS · 错误：$TOTAL_ERRORS · 失败：$TOTAL_FAILURES"
```

#### Step 2b · OpenAPI 路径验证

```bash
# 从 /v3/api-docs 获取已暴露的路径（需 IT 上下文中或运行中服务）
# 方式一：解析 WrongItemApiContractIT 的断言（直接从 IT 日志判断）
if grep -q "openApiExposesDocumentedPaths.*PASS\|Tests run:.*Failures: 0.*WrongItemApiContractIT" \
    /tmp/be-accept-<phase>-it.log 2>/dev/null; then
  echo "✅ 维度 A · OpenAPI 契约 IT 通过"
  DIM_A_STATUS="PASS"
else
  # 方式二：直接统计 find surefire-reports 中的 WrongItemApiContractIT 结果
  CONTRACT_RESULT=$(find backend/<service>/target/failsafe-reports \
    -name "*ContractIT*" -exec grep -l "Tests run:.*Failures: 0.*Errors: 0" {} \; | wc -l)
  if [ "$CONTRACT_RESULT" -ge 1 ]; then
    echo "✅ 维度 A · 契约 IT 通过"
    DIM_A_STATUS="PASS"
  else
    echo "❌ 维度 A · 契约 IT 失败"
    DIM_A_STATUS="FAIL"
  fi
fi
```

#### Step 2c · VO 字段级检查（from be-build-spec.json）

读取 `_api_contracts.status_type.suggested_mapping`，验证代码中确有对应 mapping：

```bash
# 检查 WrongItemVO.status 序列化映射是否落地
SVC_JAVA="backend/<service>/src/main/java"

# 状态映射必须存在于 service 层
grep -r '"pending"\|"analyzing"\|"completed"' "$SVC_JAVA" --include="*.java" | grep -q . \
  && echo "✅ status → String 映射已落地" || echo "❌ status 映射缺失"

# id 序列化为 String（ToStringSerializer 或 JsonProperty）
grep -r 'ToStringSerializer\|"id".*String' "$SVC_JAVA" --include="*.java" | grep -q . \
  && echo "✅ id 序列化 String 已落地" || echo "⚠️  id → String 序列化未检测到（需人工确认）"

# snake_case VO 字段（@JsonProperty 或 @JsonNaming）
grep -r '@JsonProperty\|@JsonNaming\|SnakeCaseStrategy' "$SVC_JAVA" --include="*.java" | grep -q . \
  && echo "✅ snake_case VO 注解已落地" || echo "⚠️  snake_case 注解未检测到"
```

#### Step 2d · 汇总维度 A

```bash
if [ "$IT_EXIT" -eq 0 ] && [ "$DIM_A_STATUS" = "PASS" ]; then
  echo "✅✅ 维度 A · 接口契约 — 全部通过"
  DIM_A_OVERALL="✅ PASS"
else
  echo "❌ 维度 A · 接口契约 — 有失败（IT_EXIT=$IT_EXIT, STATUS=$DIM_A_STATUS）"
  DIM_A_OVERALL="❌ FAIL"
fi
```

---

### Step 3 · 维度 B · 业务行为验收

#### Step 3a · IT 测试与 Rule Card 对应关系检查

读取 `business-rule-cards.json`，逐张 card 检查是否有对应测试：

```bash
echo "=== Rule Card → IT 覆盖检查 ==="
RULE_IDS=$(python3 -c "
import json
cards = json.load(open('design/tasks/preflight/<phase>-business-rule-cards.json'))
for c in cards:
    print(c['id'], c['title'])
")
echo "$RULE_IDS"

# 统计 IT 测试数（来自 failsafe-reports）
ACTUAL_IT=$(find backend/<service>/target/failsafe-reports -name "*.xml" \
  -exec grep -oP 'tests="\K[0-9]+' {} \; 2>/dev/null | awk '{s+=$1} END{print s}')
echo "IT 总测试数：$ACTUAL_IT"
echo "Rule Card 数：$RULE_COUNT"

# 判断覆盖率：IT 测试数 >= Rule Card 数（每张 card 至少一个场景）
if [ "${ACTUAL_IT:-0}" -ge "$RULE_COUNT" ]; then
  echo "✅ IT 覆盖数（$ACTUAL_IT）≥ Rule Card 数（$RULE_COUNT）"
  DIM_B_COVERAGE="PASS"
else
  echo "⚠️  IT 覆盖数（$ACTUAL_IT）< Rule Card 数（$RULE_COUNT）— 建议补充测试"
  DIM_B_COVERAGE="WARN"
fi
```

#### Step 3b · 关键业务场景断言（S3 专用）

对 phase=s3，从 failsafe-reports 中检查关键场景是否通过：

```bash
if [ "<phase>" = "s3" ]; then
  REPORT_DIR="backend/<service>/target/failsafe-reports"
  
  # Scene 1/3 · 创建+读取
  grep -r "createAndGet\|V-S3-01a" "$REPORT_DIR" --include="*.xml" | grep -q 'failure\|error' \
    && echo "❌ Scene 1/3 (create→get) FAIL" || echo "✅ Scene 1/3 (create→get) PASS"

  # Scene 5 · 软删
  grep -r "softDelete\|V-S3-05" "$REPORT_DIR" --include="*.xml" | grep -q 'failure\|error' \
    && echo "❌ Scene 5 (soft delete) FAIL" || echo "✅ Scene 5 (soft delete) PASS"

  # Scene 6 · 幂等
  grep -r "idempotency\|V-S3-03" "$REPORT_DIR" --include="*.xml" | grep -q 'failure\|error' \
    && echo "❌ Scene 6 (idempotency) FAIL" || echo "✅ Scene 6 (idempotency) PASS"

  # Scene 7 · 乐观锁
  grep -r "optimisticLock\|V-S3-04" "$REPORT_DIR" --include="*.xml" | grep -q 'failure\|error' \
    && echo "❌ Scene 7 (optimistic lock) FAIL" || echo "✅ Scene 7 (optimistic lock) PASS"

  # Scene 4 · 标签替换
  grep -r "tagLifecycle" "$REPORT_DIR" --include="*.xml" | grep -q 'failure\|error' \
    && echo "❌ Scene 4 (tag replace) FAIL" || echo "✅ Scene 4 (tag replace) PASS"
fi
```

#### Step 3c · 汇总维度 B

```bash
if [ "$IT_EXIT" -eq 0 ] && [ "$DIM_B_COVERAGE" != "FAIL" ]; then
  echo "✅✅ 维度 B · 业务行为 — 全部通过"
  DIM_B_OVERALL="✅ PASS"
else
  echo "❌ 维度 B · 业务行为 — 有失败（请查看 /tmp/be-accept-<phase>-it.log）"
  DIM_B_OVERALL="❌ FAIL"
fi
```

---

### Step 4 · 维度 C · 架构合规验收

从 `arch-constraints.json` 读取各类约束，逐项静态扫描：

```bash
SVC_DIR="backend/<service>/src/main/java"
CTL_DIR="$SVC_DIR/$(echo <pkg-root> | tr . /)/controller"
SVC_SVC_DIR="$SVC_DIR/$(echo <pkg-root> | tr . /)/service"
REPO_DIR="$SVC_DIR/$(echo <pkg-root> | tr . /)/repo"
echo ""
echo "=== 维度 C · 架构合规扫描 ==="

DIM_C_FAIL=0

# C-01 · 禁 MyBatis（来自 arch-constraints.orm.forbidden）
! grep -r 'mybatis\|BaseMapper\|@MapperScan\|MybatisPlus' \
    "$SVC_DIR" --include="*.java" | grep -q . \
  && echo "✅ C-01 零 MyBatis" \
  || { echo "❌ C-01 检测到 MyBatis 使用"; DIM_C_FAIL=$((DIM_C_FAIL+1)); }

# C-02 · ORM 允许 JPA + QueryDSL（Q 类必须存在）
find backend/<service>/target -name "Q*.java" 2>/dev/null | grep -q . \
  && echo "✅ C-02 QueryDSL Q 类已生成" \
  || { echo "❌ C-02 QueryDSL Q 类未找到"; DIM_C_FAIL=$((DIM_C_FAIL+1)); }

# C-03 · 软删注解（来自 arch-constraints.soft_delete）
grep -r '@SQLRestriction\|@SQLDelete\|@Where' "$SVC_DIR" --include="*.java" | grep -q . \
  && echo "✅ C-03 @SQLRestriction/@SQLDelete 存在" \
  || { echo "❌ C-03 缺少软删注解"; DIM_C_FAIL=$((DIM_C_FAIL+1)); }

# C-04 · 乐观锁（来自 arch-constraints.optimistic_lock）
grep -r '@Version' "$SVC_DIR" --include="*.java" | grep -q . \
  && echo "✅ C-04 @Version 存在" \
  || { echo "❌ C-04 缺少 @Version"; DIM_C_FAIL=$((DIM_C_FAIL+1)); }

# C-05 · 幂等原子性（来自 arch-constraints messaging/transaction）
grep -r 'setIfAbsent' "$SVC_DIR" --include="*.java" | grep -q . \
  && echo "✅ C-05 setIfAbsent 原子幂等" \
  || { echo "❌ C-05 幂等未使用 setIfAbsent"; DIM_C_FAIL=$((DIM_C_FAIL+1)); }

# C-06 · Service 层有 @Transactional（audit_log 同事务）
grep -r '@Transactional' "$SVC_SVC_DIR" --include="*.java" 2>/dev/null | grep -q . \
  && echo "✅ C-06 Service 层 @Transactional 存在" \
  || { echo "❌ C-06 Service 层缺少 @Transactional"; DIM_C_FAIL=$((DIM_C_FAIL+1)); }

# C-07 · Controller 不直接调用 Repository（层次边界）
! grep -r 'Repository\.findBy\|Repository\.save\|Repository\.delete' \
    "${CTL_DIR}" --include="*.java" 2>/dev/null | grep -q . \
  && echo "✅ C-07 Controller 无 Repository 直调" \
  || { echo "❌ C-07 Controller 层直调 Repository"; DIM_C_FAIL=$((DIM_C_FAIL+1)); }

# C-08 · Controller 无 @Transactional
! grep -r '@Transactional' "${CTL_DIR}" --include="*.java" 2>/dev/null | grep -q . \
  && echo "✅ C-08 Controller 无 @Transactional" \
  || { echo "❌ C-08 Controller 含 @Transactional"; DIM_C_FAIL=$((DIM_C_FAIL+1)); }

# C-09 · Service 层无 HTTP 细节
! grep -r 'HttpServletRequest\|@RequestMapping\|@GetMapping\|@PostMapping' \
    "${SVC_SVC_DIR}" --include="*.java" 2>/dev/null | grep -q . \
  && echo "✅ C-09 Service 层无 HTTP 细节" \
  || { echo "❌ C-09 Service 层含 HTTP 细节"; DIM_C_FAIL=$((DIM_C_FAIL+1)); }

# C-10 · 禁 Spring Statemachine（来自 arch-constraints.state_machine.forbidden）
! grep -r 'spring-statemachine\|StateMachine<\|StateMachineFactory' \
    backend/<service>/pom.xml "$SVC_DIR" --include="*.java,*.xml" 2>/dev/null | grep -q . \
  && echo "✅ C-10 无 Spring Statemachine" \
  || { echo "❌ C-10 违反 ADR：禁用 Spring Statemachine"; DIM_C_FAIL=$((DIM_C_FAIL+1)); }

# C-11 · 禁 Seata
! grep -r 'seata\|@GlobalTransactional' \
    backend/<service>/pom.xml "$SVC_DIR" --include="*.java,*.xml" 2>/dev/null | grep -q . \
  && echo "✅ C-11 无 Seata" \
  || { echo "❌ C-11 违反 ADR：禁用 Seata"; DIM_C_FAIL=$((DIM_C_FAIL+1)); }

# C-12 · 禁止 IDENTITY/UUID 主键（业务实体，outbox 除外）
IDENTITY_VIOLATIONS=$(grep -r 'GenerationType\.IDENTITY\|@GeneratedValue.*UUID' \
    "$SVC_DIR" --include="*.java" 2>/dev/null \
    | grep -v 'Outbox\|outbox' || true)
[ -z "$IDENTITY_VIOLATIONS" ] \
  && echo "✅ C-12 无 IDENTITY/UUID 业务主键" \
  || { echo "❌ C-12 非法主键策略：$IDENTITY_VIOLATIONS"; DIM_C_FAIL=$((DIM_C_FAIL+1)); }

echo ""
echo "架构合规扫描完成：$DIM_C_FAIL 项失败"
if [ "$DIM_C_FAIL" -eq 0 ]; then
  echo "✅✅ 维度 C · 架构合规 — 全部通过"
  DIM_C_OVERALL="✅ PASS (12/12)"
else
  echo "❌ 维度 C · 架构合规 — $DIM_C_FAIL 项失败"
  DIM_C_OVERALL="❌ FAIL ($((12-DIM_C_FAIL))/12)"
fi
```

---

### Step 5 · 生成验收报告

收集所有维度结果，写入 `reports/phase-<phase>-be-acceptance.md`：

```bash
mkdir -p reports

# 从 failsafe-reports 汇总 IT 结果
IT_SUMMARY=$(grep "Tests run:" /tmp/be-accept-<phase>-it.log \
  | grep -v "^$" | tail -5 || echo "IT 未运行")

REPORT_FILE="reports/phase-<phase>-be-acceptance.md"

cat > "$REPORT_FILE" << 'REPORT'
# Phase <PHASE> 后端验收报告

> 生成时间：$(date -u +%Y-%m-%dT%H:%M:%SZ)
> 服务：<SERVICE_NAME>
> 执行人：be-accept Skill (automated)

## 维度 A · 接口契约

| 检查项 | 结果 |
|---|---|
| IT 全量通过 | <DIM_A_IT_RESULT> |
| OpenAPI 契约 IT | <DIM_A_CONTRACT_IT_RESULT> |
| status → String 映射 | <DIM_A_STATUS_MAPPING> |
| id 序列化为 String | <DIM_A_ID_SERIALIZE> |
| snake_case VO | <DIM_A_SNAKE_CASE> |

**维度 A 结论：<DIM_A_OVERALL>**

## 维度 B · 业务行为

| IT 测试 | 总数 | 通过 | 失败 | 错误 |
|---|---|---|---|---|
| 全量 IT | <TOTAL_TESTS> | <PASS_TESTS> | <TOTAL_FAILURES> | <TOTAL_ERRORS> |

| Rule Card 覆盖 | 结果 |
|---|---|
| Rule Card 数 | <RULE_COUNT> 张 |
| IT 测试数 | <ACTUAL_IT> 个 |
| 覆盖状态 | <DIM_B_COVERAGE> |

**维度 B 结论：<DIM_B_OVERALL>**

## 维度 C · 架构合规

| 检查项 | 结果 |
|---|---|
| C-01 · 零 MyBatis | — |
| C-02 · QueryDSL Q 类 | — |
| C-03 · @SQLRestriction/@SQLDelete | — |
| C-04 · @Version 乐观锁 | — |
| C-05 · setIfAbsent 幂等原子 | — |
| C-06 · Service @Transactional | — |
| C-07 · Controller 无 Repository 直调 | — |
| C-08 · Controller 无 @Transactional | — |
| C-09 · Service 无 HTTP 细节 | — |
| C-10 · 无 Spring Statemachine | — |
| C-11 · 无 Seata | — |
| C-12 · 无 IDENTITY/UUID 业务主键 | — |

**维度 C 结论：<DIM_C_OVERALL>**

## 总结论

| 维度 | 结论 |
|---|---|
| A · 接口契约 | <DIM_A_OVERALL> |
| B · 业务行为 | <DIM_B_OVERALL> |
| C · 架构合规 | <DIM_C_OVERALL> |

<FINAL_VERDICT>

---

> 报告由 be-accept 自动生成 · 施工图来源：design/tasks/preflight/<phase>-*.json
REPORT

echo "验收报告已写入：$REPORT_FILE"
```

**注意**：上面是 shell 模板示意。实际执行时，Claude 应将各变量的实际值内联填入报告，而非依赖 shell 变量展开。具体做法：

1. 运行每条扫描命令，记录每个检查项的 ✅/❌ 结果
2. 将结果填入报告模板（Write 工具写入文件）
3. 报告中的每一行都是真实执行结果，不是占位符

---

### Step 6 · 结论判定

```bash
echo ""
echo "================================================================"
echo "  be-accept <phase> · 验收结论"
echo "================================================================"
echo "  维度 A · 接口契约  →  $DIM_A_OVERALL"
echo "  维度 B · 业务行为  →  $DIM_B_OVERALL"
echo "  维度 C · 架构合规  →  $DIM_C_OVERALL"
echo "================================================================"
```

**全部通过**（三个维度全绿）：

```
✅ Phase <phase> 后端验收通过

   建议执行：
     git tag <phase>-done
     git push --tags
   
   然后进入下一 Phase：/be-preflight <next-phase>
```

**有失败项**（任一维度有 ❌）：

```
❌ Phase <phase> 后端验收未通过，需要修复：

   维度 A 失败 → 检查 Layer 4 (controller/DTO)，重新执行 /be-builder <phase> layer4
   维度 B 失败 → 检查 IT 测试和 Layer 3 (service)，查看 /tmp/be-accept-<phase>-it.log
   维度 C 失败 → 按 C-XX 编号定位违规代码，修复后重新执行 /be-accept <phase>
```

---

## 各 Phase 特殊说明

### S3 · wrongbook-service

业务行为 9 个场景与 IT 测试对应：

| Scene | 描述 | IT 测试方法 |
|---|---|---|
| 1 · 录入主流程 | POST /items → 201, status=pending | `WrongItemIT.createAndGet` |
| 2 · 列表加载 | GET /items?subject= → 有数据 | `WrongItemIT.pageBySubject` |
| 3 · 详情查看 | GET /items/{id} → 字段齐全 | `WrongItemIT.createAndGet` |
| 4 · 标签批量替换 | PATCH /items/{id}/tags → DB 恰好 N 行 | `WrongItemIT.tagLifecycle` |
| 5 · 软删后不可见 | DELETE → GET 404 + audit_log | `WrongItemIT.softDelete` |
| 6 · 幂等性 | 同 rid 两次 POST → 同 id, count=1 | `WrongItemIT.idempotencySingleRequestId` |
| 7 · 乐观锁冲突 | 并发 PATCH → 一 200 一 409 | `WrongItemIT.optimisticLockConflict` |
| 8 · 非法状态转换 | reviewed→draft → 400 | 需补充 IT（目前无） |
| 9 · Redis 降级 | Redis 停后仍 201 | 手动验证（需 Docker 操作）|

Scene 8/9 若 IT 尚未覆盖，在维度 B 报告中标注 `⚠️ 待补充`，不作为阻断项（升级为 WARNING）。

### S4 · ai-analysis-service

- 维度 A 需额外检查 SSE 端点的 `Content-Type: text/event-stream`
- 维度 B 需验证 AI 调用失败降级（fallback 返回，非 500）
- 维度 C 额外检查：无硬编码 API Key（`grep -r 'api-key\|sk-' --include="*.java"` 期望 0 命中）

### S5 · review-plan-service

- 维度 B 需验证 SM-2 算法计算结果（ease_factor/interval 公式正确）
- 维度 C 额外检查：mastery 回写 WrongItem 使用跨服务调用（非直接 DB 写入）

### S6 · file-service

- 维度 A 需验证 presign URL 格式（非空字符串，必须是合法 URL）
- 维度 B 需验证文件完成回调状态变更（READY / QUARANTINED）

---

## 报告文件存放

```
reports/phase-<phase>-be-acceptance.md
```

报告必须用 Write 工具写入磁盘，不能只打印到终端。报告中的每一行检查结果必须是真实命令执行结果（✅/❌/⚠️），不允许 AI 自行声明"应该通过"。

---

## 硬约束

1. **Step 0 必须读文件** — 所有约束来源必须是 JSON 文件，不靠对话历史
2. **门禁必须机器判定** — 不允许 AI 声明"架构应该合规"，必须执行 grep 命令
3. **IT 必须实际运行** — 不允许跳过 mvn verify，不允许声明"IT 应该都绿"
4. **报告必须写入文件** — 不能只打印验收结论，必须写入 `reports/phase-<phase>-be-acceptance.md`
5. **维度独立判定** — A/B/C 三个维度各自独立给出 PASS/FAIL/WARN，不合并判断
6. **失败必须指明修复层** — 有 ❌ 时必须指出对应哪个 be-builder Layer 需要修复
7. **占位符禁止** — 报告中不允许出现 `<DIM_A_IT_RESULT>` 等未替换的占位符

## 参考文档

- `design/tasks/preflight/<phase>-be-build-spec.json` — API 契约（维度 A 真源）
- `design/tasks/preflight/<phase>-business-rule-cards.json` — 业务规则（维度 B 真源）
- `design/tasks/preflight/<phase>-arch-constraints.json` — 架构约束（维度 C 真源）
- `design/AI后端自动开发三段式方案.md` — 方案全文（验收维度原始定义）
