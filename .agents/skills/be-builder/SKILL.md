---
name: be-builder
description: >
  AI 后端高保真开发的 Stage 2 Builder 工作流。读取 be-preflight 生成的三份施工图，
  按层（entity/repo/service/controller/openapi）逐层实现后端代码，
  每层精准注入对应的 Context 片段（不注入全文），每层完成后运行编译门禁。
  核心机制：每层作为独立 Sub-agent，从磁盘读文件，不共享对话历史，防止 Context 膨胀。
  触发场景：用户说 "/be-builder <phase>"、"/be-builder <phase> layer<N>"、
  "开始 builder"、"执行 layer1"，或 be-preflight 确认 has_blocking_gaps=false 后。
---

# be-builder · 分层构建 · Context 精准注入

## 目标

读取 be-preflight 产出的三份施工图，逐层实现后端服务，
确保：接口形状与前端 api-contracts 对齐、业务规则完整落地、架构约束严格遵守。

## 输入

| 参数 | 说明 | 示例 |
|---|---|---|
| `<phase>` | Phase ID | s3 / s4 / s5 |
| `[layer<N>]` | 可选，只执行指定层 | layer1 / layer2 / layer3 / layer4 / layer5 |

Phase → 服务目录默认映射：

| Phase | 服务目录 | Java 包根 |
|---|---|---|
| s3 | `backend/wrongbook-service` | `com.longfeng.wrongbook` |
| s4 | `backend/ai-analysis-service` | `com.longfeng.analysis` |
| s5 | `backend/review-plan-service` | `com.longfeng.review` |
| s6 | `backend/file-service` | `com.longfeng.file` |

施工图文件（be-preflight 产出，必须存在）：

```
design/tasks/preflight/<phase>-be-build-spec.json
design/tasks/preflight/<phase>-business-rule-cards.json
design/tasks/preflight/<phase>-arch-constraints.json
```

## ⚠️ Context 控制机制（必须执行，不得绕过）

### 核心原则：文件是管道，每层是独立 Agent

```
be-preflight → 写 3 个 JSON 到磁盘
be-builder layer N → 从磁盘读本层片段 → 写代码 → gate → commit
be-builder layer N+1 → 重新从磁盘读（无 layer N 的对话历史）
```

每层实现时必须遵守：
1. **Step 0 永远是读文件** — 不用对话历史里"记得的"约束，必须从磁盘重读
2. **只读本层需要的字段** — 使用下表指定的 JSON 路径，不读整个文件
3. **每层作为独立 Sub-agent** — 层与层之间没有共享的对话历史
4. **交接只通过 git commit + 磁盘文件** — 不通过对话传递信息

### 各层 Context 片段规格

#### Layer 1 · entity（约 2-3KB）

读取字段（使用 jq 提取）：

```bash
# 从 arch-constraints.json
jq '{orm,id_generation,soft_delete,optimistic_lock,persistence,subject_enum}' \
  design/tasks/preflight/<phase>-arch-constraints.json

# 从 be-build-spec.json（仅 entity 层）
jq '.layers[] | select(.id=="entity")' \
  design/tasks/preflight/<phase>-be-build-spec.json
```

**不读**：`_api_contracts`、`_business_rules`、`resolved_gaps`、其他 layers

#### Layer 2 · repository（约 1-2KB）

读取字段：

```bash
# 从 arch-constraints.json
jq '{orm,persistence}' \
  design/tasks/preflight/<phase>-arch-constraints.json

# 从 be-build-spec.json（仅 repository 层）
jq '.layers[] | select(.id=="repository")' \
  design/tasks/preflight/<phase>-be-build-spec.json
```

**不读**：业务规则、API 契约、其他 layers

#### Layer 3 · service（约 4-6KB，是最重的层）

读取字段：

```bash
# 主要：业务规则全部（若 > 5 张则按批读，见 Sub-layer 拆分）
cat design/tasks/preflight/<phase>-business-rule-cards.json

# 从 be-build-spec.json（业务规则部分）
jq '{_business_rules, "layer": (.layers[] | select(.id=="service"))}' \
  design/tasks/preflight/<phase>-be-build-spec.json

# 从 arch-constraints.json（辅助：基础设施约束）
jq '{messaging,transaction,infra}' \
  design/tasks/preflight/<phase>-arch-constraints.json
```

**不读**：`_api_contracts`（端点形状）、entity/repo layer 定义

#### Layer 4 · controller（约 3-4KB）

读取字段：

```bash
# 主要：API 契约（端点 + VO）
jq '{_api_contracts, "layer": (.layers[] | select(.id=="controller"))}' \
  design/tasks/preflight/<phase>-be-build-spec.json

# 从 arch-constraints.json（层次边界 + 路径规则）
jq '{api_path_prefix,subject_enum}' \
  design/tasks/preflight/<phase>-arch-constraints.json
```

**不读**：业务规则 cards、entity/repo/service layer 定义

#### Layer 5 · openapi（< 1KB）

读取字段：

```bash
# 只读端点列表（路径+方法+summary）和期望端点数
jq '{endpoints: [._api_contracts.endpoints[] | {method,path}], "layer": (.layers[] | select(.id=="openapi"))}' \
  design/tasks/preflight/<phase>-be-build-spec.json
```

**不读**：业务规则、架构约束、VO 字段详情

## 执行步骤（全量：无 layer 参数时依次执行 1→5）

### 前置检查

```bash
# 确认施工图已就绪
cat design/tasks/preflight/<phase>-be-build-spec.json | \
  python3 -c "import sys,json; d=json.load(sys.stdin); exit(0 if not d.get('has_blocking_gaps') else 1)" \
  || { echo "❌ has_blocking_gaps=true，请先确认 contract-gaps.md 后重试"; exit 1; }
```

---

### Layer 1 · entity 实体层

**Context 注入**：只读 arch-constraints 的 `{orm, id_generation, soft_delete, optimistic_lock, persistence, subject_enum}` + be-build-spec 的 entity layer 定义

#### Step 1a · 读取本层 Context（从磁盘，不靠记忆）

```bash
echo "=== Layer 1 Context: arch-constraints (entity slice) ==="
jq '{orm,id_generation,soft_delete,optimistic_lock,persistence,subject_enum}' \
  design/tasks/preflight/<phase>-arch-constraints.json

echo "=== Layer 1 Context: be-build-spec (entity layer) ==="
jq '.layers[] | select(.id=="entity")' \
  design/tasks/preflight/<phase>-be-build-spec.json
```

#### Step 1b · 实现内容（按 Context 片段，逐条落实）

按 entity layer 的 `key_rules` 和 `migrations_required` 实现：

1. **Flyway migration SQL**（`migrations_required` 中列出的每个文件）
   - 路径：`backend/<service>/src/main/resources/db/migration/`
   - 按 arch-constraints.json 中 `soft_delete.column`、`optimistic_lock.column` 写准确 DDL

2. **WrongItemStatus 枚举**（来自 `arch-constraints.json .subject_enum` + be-build-spec 状态机）
   - 内部域枚举：DRAFT(0) / ANALYZED(1) / SCHEDULED(2) / REVIEWED(3) / MASTERED(8) / ARCHIVED(9)
   - 不使用 String，使用 SMALLINT 映射（`@Enumerated(EnumType.ORDINAL)` 不适用，用 `AttributeConverter`）

3. **JPA Entity 类**（来自 entity layer `symbols` + `key_rules`）
   - `@SQLDelete` + `@Where` 来自 `arch-constraints.soft_delete.required_annotations`
   - `@Version` 来自 `arch-constraints.optimistic_lock.annotation`
   - 主键生成器来自 `arch-constraints.id_generation`（Snowflake，禁 `@GeneratedValue(IDENTITY)`）

#### Step 1c · 编译门禁（必须通过才进入 Layer 2）

```bash
mvn -f backend/pom.xml -pl <service-name> -am -q -DskipTests compile
echo "Layer 1 编译门禁：$?"
```

#### Step 1d · 架构合规扫描

```bash
SVC_DIR="backend/<service>/src/main/java"
# 软删注解必须存在
grep -r '@SQLDelete\|@Where' "$SVC_DIR" --include="*.java" | grep -q . \
  || { echo "❌ 缺少 @SQLDelete/@Where"; exit 1; }

# 乐观锁注解必须存在
grep -r '@Version' "$SVC_DIR" --include="*.java" | grep -q . \
  || { echo "❌ 缺少 @Version"; exit 1; }

# 禁止 BIGSERIAL 或 UUID 主键
! grep -r 'BIGSERIAL\|UUID\|GenerationType.IDENTITY' "$SVC_DIR" --include="*.java" | grep -q . \
  || { echo "❌ 违反 id_generation 约束"; exit 1; }

echo "✅ Layer 1 架构合规通过"
```

#### Step 1e · Commit

```bash
git add backend/<service>/src/main/java/**/*Entity*.java \
        backend/<service>/src/main/java/**/*Status*.java \
        backend/<service>/src/main/resources/db/migration/
git commit -m "feat(<phase>-L1): entity layer · JPA entity + Flyway migrations + WrongItemStatus"
```

---

### Layer 2 · repository 仓储层

**Context 注入**：只读 arch-constraints 的 `{orm, persistence}` + be-build-spec 的 repository layer 定义

#### Step 2a · 读取本层 Context

```bash
echo "=== Layer 2 Context ==="
jq '{orm,persistence}' design/tasks/preflight/<phase>-arch-constraints.json
jq '.layers[] | select(.id=="repository")' design/tasks/preflight/<phase>-be-build-spec.json
```

#### Step 2b · 检查前置条件

```bash
git log --oneline | grep -q "feat(<phase>-L1)" \
  || { echo "❌ Layer 1 commit 未找到，请先完成 Layer 1"; exit 1; }
```

#### Step 2c · 实现内容

1. **JpaRepository 接口**（来自 repository layer `symbols`）
   - 继承 `JpaRepository<Entity, Long>`
   - 复杂查询（多条件过滤列表）用 `QueryDSL JPAQueryFactory`，禁 `@Query` 大篇幅 JPQL

2. **QueryDSL Predicate 工具类**（来自 arch-constraints `orm.allowed` 的 QueryDSL 要求）
   - 为 `GET /items` 的 cursor/subject/status/tagCode 过滤组合构建 Predicate
   - cursor 分页：按 `id DESC`，cursor = `base64(lastId)`

3. **Q 类生成**（Maven APT 插件自动生成，verify 能找到 `Q*.java`）

#### Step 2d · 编译门禁

```bash
mvn -f backend/pom.xml -pl <service-name> -am -q -DskipTests compile
# 验证 Q 类已生成
find backend/<service>/target -name "QWrong*.java" | grep -q . \
  || { echo "❌ QueryDSL Q 类未生成"; exit 1; }
echo "✅ Layer 2 编译门禁通过"
```

#### Step 2e · 架构合规扫描

```bash
# 禁 MyBatis
! grep -r 'mybatis\|BaseMapper\|@MapperScan' \
    backend/<service>/src/main/java --include="*.java" | grep -q . \
  || { echo "❌ 发现 MyBatis 使用"; exit 1; }

# Repository 层无业务逻辑
! grep -r '@Transactional\|RocketMQ\|Redis' \
    backend/<service>/src/main/java/**/repo/ --include="*.java" | grep -q . \
  || { echo "❌ Repository 层含业务逻辑"; exit 1; }
echo "✅ Layer 2 架构合规通过"
```

#### Step 2f · Commit

```bash
git add backend/<service>/src/main/java/**/repo/
git commit -m "feat(<phase>-L2): repository layer · JpaRepository + QueryDSL predicates"
```

---

### Layer 3 · service 业务层

**这是最重的层。先检查 rule_cards 数量，决定单次执行还是 Sub-layer 拆分。**

**Context 注入**：business-rule-cards.json（全部或当批）+ be-build-spec._business_rules + arch-constraints.{messaging, transaction, infra}

#### Step 3a · 读取本层 Context + Sub-layer 决策

```bash
echo "=== Layer 3 Context ==="
# 业务规则卡
CARD_COUNT=$(python3 -c "import json; print(len(json.load(open('design/tasks/preflight/<phase>-business-rule-cards.json'))))")
echo "rule_cards 数量：$CARD_COUNT"

# 架构辅助约束
jq '{messaging: ._arch_constraints.mq, transaction: ._business_rules.idempotency, infra: ._arch_constraints}' \
  design/tasks/preflight/<phase>-be-build-spec.json
jq '{messaging,transaction,infra}' design/tasks/preflight/<phase>-arch-constraints.json
```

**Sub-layer 拆分规则**：

```
if CARD_COUNT <= 5:
    执行单次 Layer 3（读全部 rule_cards）
else:
    自动拆分为多个 Sub-layer，每批 2-3 张，按领域相关性分组：
      Layer 3a：写操作主流程（create/update 类 cards）
      Layer 3b：状态变更（softDelete/updateTags/updateStatus 类 cards）
      Layer 3c：读操作（list/get/getTags 类 cards）
    每个 Sub-layer 独立读对应批次 cards + 独立编译门禁 + 独立 commit
    全部 Sub-layer 通过后继续 Layer 4
```

**S3 拆分示例**（8 张 rule_cards）：

```
Layer 3a context：BR-01(状态守卫) + BR-02(幂等) + BR-05(MQ Outbox)
Layer 3b context：BR-03(乐观锁) + BR-04(软删审计) + BR-06(标签幂等)
Layer 3c context：BR-07(mastered回退) + BR-08(Attempt append-only)
```

#### Step 3b · 实现内容（对每张 Rule Card 逐一实现）

对 business-rule-cards.json 中每张 card（或当批）：

1. **读 `rule`（必须实现的核心逻辑）**
   - 状态迁移守卫：在 Service 中实现 `transitionStatus(target)` 方法，校验合法边
   - 幂等键：`RedisTemplate.opsForValue().setIfAbsent(key, value, ttl)` 原子命令
   - 乐观锁：捕获 `ObjectOptimisticLockingFailureException` → 重试/409

2. **读 `violation_behavior`（异常路径必须实现）**
   - 每种 violation 对应的 HTTP 状态码 + error message

3. **读 test_scenarios（可选，用于 Layer 4 的 IT 参考）**
   - 不在 Layer 3 写测试，但记录为注释/TODO 供 Layer 4 IT 用

同时从 arch-constraints `{messaging, transaction, infra}` 读取：
- `messaging.mq_outbox`：RocketMQ syncSend 失败 → catch → INSERT outbox
- `transaction.rule`：@Transactional 作用域内同步 INSERT audit_log
- `infra.redis.timeout_ms`：RedisTemplate 超时配置
- `infra.rocketmq.timeout_ms / retry`：Producer 超时 + 重试次数

#### Step 3c · 编译门禁（每个 Sub-layer 完成后必跑）

```bash
mvn -f backend/pom.xml -pl <service-name> -am -q -DskipTests compile
echo "Layer 3[a/b/c] 编译门禁：$?"
```

#### Step 3d · 架构合规扫描

```bash
SVC_DIR="backend/<service>/src/main/java"

# 幂等原子性：必须用 setIfAbsent，禁先 get 后 set
grep -r 'setIfAbsent' "$SVC_DIR" --include="*.java" | grep -q . \
  || { echo "❌ 幂等未用 setIfAbsent 原子命令"; exit 1; }

# Service 层无 HTTP 细节
! grep -r 'HttpServletRequest\|@RequestMapping\|@GetMapping' \
    "$SVC_DIR/**/service/" --include="*.java" | grep -q . \
  || { echo "❌ Service 层含 HTTP 细节"; exit 1; }

# MQ 在事务内（audit_log + MQ 在同一 @Transactional）
grep -r '@Transactional' "$SVC_DIR/**/service/" --include="*.java" | grep -q . \
  || { echo "❌ Service 层缺少 @Transactional"; exit 1; }

# 禁 Spring Statemachine
! grep -r 'spring-statemachine\|StateMachine<\|StateMachineFactory' \
    backend/<service>/pom.xml "$SVC_DIR" --include="*.java,*.xml" | grep -q . \
  || { echo "❌ 违反 ADR-0010：禁用 Spring Statemachine"; exit 1; }

echo "✅ Layer 3 架构合规通过"
```

#### Step 3e · Commit（每个 Sub-layer 各一个 commit）

```bash
git add backend/<service>/src/main/java/**/service/
git commit -m "feat(<phase>-L3[a/b/c]): service layer · [BR-XX BR-YY] 业务规则实现"
```

---

### Layer 4 · controller 控制层

**Context 注入**：be-build-spec._api_contracts（端点+VO+status_type）+ arch-constraints.{api_path_prefix, subject_enum}

#### Step 4a · 读取本层 Context

```bash
echo "=== Layer 4 Context: API 契约 ==="
jq '{_api_contracts}' design/tasks/preflight/<phase>-be-build-spec.json

echo "=== Layer 4 Context: 路径规则 ==="
jq '{api_path_prefix,subject_enum}' design/tasks/preflight/<phase>-arch-constraints.json
```

#### Step 4b · 检查前置条件

```bash
git log --oneline | grep -q "feat(<phase>-L3" \
  || { echo "❌ Layer 3 commit 未找到，请先完成 Layer 3"; exit 1; }
```

#### Step 4c · 实现内容（按 `_api_contracts.endpoints` 逐个端点实现）

对 `_api_contracts.endpoints` 中每个端点：

1. **Controller 方法**
   - 路径前缀来自 `arch-constraints.api_path_prefix.rule`（不加 `/api/v1/`，gateway 统一加）
   - `X-Request-Id` header 在 POST /items 强制校验（`@RequestHeader("X-Request-Id")`）

2. **DTO 类**（对照 `_api_contracts.vo_fields`）
   - `WrongItemVO` 字段全部用 snake_case（`@JsonProperty("stem_text")`）
   - `status` 字段类型 `String`，在 Service → VO 转换时映射（来自 `_api_contracts.status_type.suggested_mapping`）
   - `id` 字段序列化为 String（Long → String，避免 JS 精度丢失）

3. **ExceptionHandler**
   - `ObjectOptimisticLockingFailureException` → 409
   - `NotFoundException` → 404
   - `IllegalArgumentException` → 400（非法状态迁移）
   - Bean Validation 失败 → 400

4. **Integration Tests**（来自 business-rule-cards 的 `test_scenarios`）
   - `WrongItemIT`：Scene 1-9 对应的真实 HTTP 测试
   - `WrongItemApiContractIT`：对照 `_api_contracts.endpoints` 验证每个端点的 response 结构

#### Step 4d · 验证门禁（verify 包含 IT）

```bash
mvn -f backend/pom.xml -pl <service-name> -am -q verify
echo "Layer 4 verify 门禁：$?"
```

#### Step 4e · 架构合规扫描

```bash
CTL_DIR="backend/<service>/src/main/java/**/controller"

# Controller 无 repository 直接调用
! grep -r '\.findById\|\.save\|Repository\.' \
    "$CTL_DIR" --include="*.java" | grep -q . \
  || { echo "❌ Controller 层直接调用 Repository"; exit 1; }

# Controller 无 @Transactional
! grep -r '@Transactional' "$CTL_DIR" --include="*.java" | grep -q . \
  || { echo "❌ Controller 层含 @Transactional"; exit 1; }

echo "✅ Layer 4 架构合规通过"
```

#### Step 4f · Commit

```bash
git add backend/<service>/src/main/java/**/controller/ \
        backend/<service>/src/main/java/**/dto/ \
        backend/<service>/src/test/
git commit -m "feat(<phase>-L4): controller layer · REST endpoints + DTO + IT green"
```

---

### Layer 5 · openapi 规格层

**Context 注入**：be-build-spec._api_contracts.endpoints（仅 method+path，< 1KB）

#### Step 5a · 读取本层 Context

```bash
echo "=== Layer 5 Context: 端点清单 ==="
jq '._api_contracts.endpoints | map({method,path})' \
  design/tasks/preflight/<phase>-be-build-spec.json
EXPECTED=$(jq '._api_contracts.endpoints | length' \
  design/tasks/preflight/<phase>-be-build-spec.json)
echo "预期端点数：$EXPECTED"
```

#### Step 5b · 实现内容

1. 确认所有 Controller 方法有 `@Operation(summary=...)` 注解
2. 确认所有 DTO 类有 `@Schema` 注解
3. 生成/更新 `OpenApiConfig`，配置 springdoc 导出路径

#### Step 5c · OpenAPI 门禁

```bash
# 启动服务生成 openapi.yaml（或 Maven 插件离线生成）
mvn -f backend/pom.xml -pl <service-name> -am -q -DskipTests \
  springdoc-openapi:generate 2>/dev/null \
  || mvn -f backend/pom.xml -pl <service-name> -am -q -DskipTests verify

# 统计 paths 数量
ACTUAL=$(grep -c "^\s\+/wrongbook" backend/<service>/src/main/resources/openapi.yaml 2>/dev/null || echo "0")
echo "实际端点数：$ACTUAL / 期望：$EXPECTED"
[ "$ACTUAL" -ge "$EXPECTED" ] || { echo "❌ OpenAPI 端点数不足"; exit 1; }
echo "✅ Layer 5 OpenAPI 门禁通过"
```

#### Step 5d · Commit

```bash
git add backend/<service>/src/main/java/**/config/OpenApiConfig.java \
        backend/<service>/src/main/resources/openapi.yaml
git commit -m "feat(<phase>-L5): openapi layer · springdoc 规格导出 · paths=$ACTUAL"
```

---

## 汇总

全部 5 层完成后打印：

```
✅ be-builder 完成 · <phase>

   Layer 1 · entity     ✅ (commit: feat(<phase>-L1))
   Layer 2 · repo       ✅ (commit: feat(<phase>-L2))
   Layer 3 · service    ✅ (commit: feat(<phase>-L3a/L3b/L3c))
   Layer 4 · controller ✅ (commit: feat(<phase>-L4) · IT 全绿)
   Layer 5 · openapi    ✅ (commit: feat(<phase>-L5) · paths=N)

   架构合规：全部通过
   业务规则 cards 落地：M 张
   IT 覆盖 Scenes：1-9

建议：运行 /be-accept <phase> 进行三维验收
```

## 硬约束

1. **Step 0 必须读文件** — 每层启动时第一步必须执行 Step Xa 的 jq 命令，不允许跳过
2. **不允许跨层合并** — 每层有独立 commit，不允许"Layer 1+2 一起提交"
3. **门禁必须机器通过** — 不允许 AI 自我声明"编译应该可以通过"，必须运行命令
4. **门禁失败最多重试 3 次** — 第 4 次失败停机，输出完整错误，等 User 介入
5. **Sub-layer 必须独立 commit** — Layer 3 拆分时每个 Sub-layer 各自 commit，不合并
6. **Context 片段不得扩展** — Layer 1 不得读取 `_business_rules`；Layer 4 不得读取 `business-rule-cards.json`
7. **禁 MyBatis / Spring Statemachine / Seata** — arch-constraints.json 的 forbidden 列表是硬红线，架构合规扫描必跑

## 参考文档

- `design/tasks/preflight/<phase>-be-build-spec.json` — API 契约施工图
- `design/tasks/preflight/<phase>-business-rule-cards.json` — 业务规则卡
- `design/tasks/preflight/<phase>-arch-constraints.json` — 架构约束
- `design/arch/<phase>-*.md` — Phase 架构文档（仅供疑义时查证，不作主要 Context）
- `design/AI后端自动开发三段式方案.md` — 方案全文（Context 管理原理说明）
