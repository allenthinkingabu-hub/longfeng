---
name: be-preflight
description: >
  AI 后端高保真开发的 Stage 1 Pre-flight 工作流。在 AI 开始写后端代码之前，
  从落地实施计划、api-contracts、business-analysis.yml、arch 文档中提取
  三类约束（接口契约 / 业务规则 / 架构限制），并检测当前代码与前端契约的差距，
  最终输出 be-build-spec.json（Builder 施工图）和 contract-gaps.md（阻断项供 User 确认）。
  触发场景：用户说 "/be-preflight <phase>"、"开始后端 pre-flight"、"分析后端约束"、
  "生成后端 build-spec"，或在准备开发某个后端 Phase 之前。
---

# be-preflight · 后端开发前信息对齐

## 目标

把落地实施计划（Phase 描述）+ api-contracts + business-analysis.yml + arch 文档
转成 **be-build-spec.json**（be-builder 的施工图），
填补"AI 不知道前端调什么接口、不知道业务规则边界、不知道架构禁止什么"的信息缺口。

## 输入

| 参数 | 说明 | 示例 |
|---|---|---|
| `<phase>` | Phase ID，如 s3 / s4 / s5 | s3 |
| `--plan` | 实施计划路径（可选，自动推断） | design/落地实施计划_v1.0_AI自动执行.md |
| `--arch` | arch 文档路径（可选，自动推断） | design/arch/<phase>-*.md |
| `--analysis` | business-analysis.yml 路径（可选，自动推断） | design/analysis/<phase>-business-analysis.yml |

Phase → 文件路径默认映射：

| Phase | arch 文档 | business-analysis |
|---|---|---|
| s3 | `design/arch/s3-wrongbook.md` | `design/analysis/s3-business-analysis.yml`（若存在）|
| s4 | `design/arch/s4-ai-analysis.md` | `design/analysis/s4-business-analysis.yml`（若存在）|
| s5 | `design/arch/s5-review-plan.md` | `design/analysis/s5-business-analysis.yml`（若存在）|

## 执行步骤（严格按序）

### Step 1 · 读取 Phase 对应 arch 文档

```bash
cat design/arch/<phase>-*.md
```

重点读取：
- `§4 事件与契约`（OpenAPI paths、DTO、RocketMQ topic）
- `§2 领域模型`（聚合根、实体、状态机、不变量）
- `§1 业务理解`（假设清单、漂移登记、special_requirements）
- `§8 符号清单`（聚合根/Service/Controller/DTO 符号列表）

提取并记录：
- **API 端点列表**：所有 REST paths + HTTP method + 路径参数
- **DTO 结构**：request/response body 字段 + 约束
- **状态机**：所有状态、所有合法迁移边
- **不变量**：INV-* 列表

### Step 2 · 读取 api-contracts（前端真源）

```bash
cat frontend/packages/api-contracts/src/clients/*.ts
cat frontend/packages/api-contracts/src/types.ts
```

重点读取：
- **调用路径**：`GET/POST/PATCH/DELETE` 实际 URL + query 参数
- **VO 字段**：前端期望的响应字段类型
- **Status 类型**：前端使用的枚举值（如 `WrongItemStatus = 'pending' | 'analyzing' | ...`）
- **请求结构**：`CreateXxxReq`、`UpdateXxxReq` 字段

### Step 3 · 读取 business-analysis.yml（若存在）

```bash
cat design/analysis/<phase>-business-analysis.yml 2>/dev/null || echo "NOT FOUND"
```

提取 `special_requirements`（已决议的 Q&A）和 `ac_coverage`（AC + testid 关联）。

### Step 4 · 对比检测 contract gaps

逐一比对 arch 文档定义的 API 端点 vs api-contracts 实际调用：

| 检查项 | 说明 |
|---|---|
| 路径缺失 | arch 定义了但 api-contracts 未调用，或反之 |
| 字段缺失 | VO 中前端需要但 arch DTO 中没有的字段 |
| 类型不匹配 | 前端 `status: string` vs 后端 `status: integer` |
| 方法不符 | 前端用 PATCH 但 arch 定义 PUT |

将所有 gap 分类为：
- **BLOCKER**：前端调用但后端无对应端点；或关键字段类型不兼容
- **WARNING**：命名风格差异；非必填字段缺失；端点顺序差异

### Step 5 · 生成 be-build-spec.json

输出路径：`design/tasks/preflight/<phase>-be-build-spec.json`

结构模板：

```json
{
  "phase": "s3",
  "arch_source": "design/arch/s3-wrongbook.md",
  "api_contracts_source": "frontend/packages/api-contracts/src/clients/wrongbook.ts",
  "business_analysis": "design/analysis/s3-business-analysis.yml",
  "generated_at": "<ISO8601>",
  "_api_contracts": {
    "endpoints": [
      {
        "method": "POST",
        "path": "/wrongbook/items",
        "request_dto": "CreateWrongItemReq",
        "response_dto": "WrongItemVO",
        "idempotency": "X-Request-Id header",
        "frontend_file": "frontend/packages/api-contracts/src/clients/wrongbook.ts:12"
      }
    ],
    "status_type": {
      "frontend_values": ["pending", "analyzing", "completed", "error"],
      "backend_values": [0, 1, 2, 3, 8, 9],
      "mapping_required": true,
      "mapping_note": "前端 string → 后端 SMALLINT，WrongItemVO 需序列化为 string"
    }
  },
  "_business_rules": {
    "state_machine": {
      "states": ["draft", "analyzed", "scheduled", "reviewed", "mastered", "archived"],
      "transitions": [
        { "from": "draft", "to": "analyzed", "trigger": "S4 embedding 回填" },
        { "from": "analyzed", "to": "scheduled", "trigger": "S5 review_plan 生成" }
      ],
      "invariants": ["INV-01: 仅允许预定义边迁移", "INV-02: version 单调递增"]
    },
    "idempotency": {
      "scope": "POST /wrongbook/items",
      "key_pattern": "idem:wb:{requestId}",
      "ttl_seconds": 600,
      "fallback": "DB unique index idem_key"
    },
    "soft_delete": {
      "column": "deleted_at TIMESTAMPTZ",
      "hibernate_annotation": "@SQLDelete + @Where(clause='deleted_at IS NULL')",
      "post_delete_get": "404"
    },
    "optimistic_lock": {
      "column": "version BIGINT",
      "conflict_http_status": 409
    }
  },
  "_arch_constraints": {
    "orm": "JPA + QueryDSL（禁 MyBatis）",
    "mq": "RocketMQ 5.x 事务消息（失败降级 wrong_item_outbox）",
    "id_generation": "Snowflake（禁 BIGSERIAL）",
    "forbidden_patterns": [
      "MyBatis / Mapper XML",
      "Seata 分布式事务",
      "Spring Statemachine",
      "UUID 主键"
    ],
    "required_patterns": [
      "HikariCP 连接池",
      "Nacos 服务发现",
      "Sentinel 限流",
      "@Transactional + audit_log 同事务"
    ]
  },
  "layers": [
    {
      "id": "entity",
      "label": "实体层（Entity / Domain Model）",
      "symbols": ["WrongItem", "WrongItemTag", "WrongItemImage", "TagTaxonomy", "WrongAttempt", "WrongItemOutbox"],
      "key_rules": [
        "WrongItem 唯一聚合根",
        "@SQLDelete + @Where 软删",
        "@Version Long version 乐观锁",
        "Snowflake @GeneratedValue(strategy=AUTO) 需替换为自定义 generator"
      ],
      "files_to_create": [
        "src/main/java/com/longfeng/wrongbook/domain/WrongItem.java",
        "src/main/java/com/longfeng/wrongbook/domain/WrongItemTag.java",
        "src/main/java/com/longfeng/wrongbook/domain/WrongItemImage.java",
        "src/main/java/com/longfeng/wrongbook/domain/TagTaxonomy.java",
        "src/main/java/com/longfeng/wrongbook/domain/WrongAttempt.java",
        "src/main/java/com/longfeng/wrongbook/domain/WrongItemOutbox.java"
      ]
    },
    {
      "id": "repository",
      "label": "仓储层（Repository）",
      "symbols": ["WrongItemRepository", "WrongItemTagRepository", "WrongItemImageRepository", "TagTaxonomyRepository", "WrongAttemptRepository", "WrongItemOutboxRepository"],
      "key_rules": [
        "继承 JpaRepository",
        "复杂查询用 QueryDSL Predicate（禁 @Query JPQL 大量使用）",
        "软删 @Where 已在 Entity 层处理，Repository 无需额外 filter"
      ]
    },
    {
      "id": "service",
      "label": "业务层（Service）",
      "symbols": ["WrongItemService", "WrongAttemptService", "IdempotencyService"],
      "key_rules": [
        "状态迁移守卫：transitionStatus 方法校验合法边，非法迁移抛 400",
        "幂等：POST /items 先查 Redis idem:wb:{requestId}，命中返回缓存 VO",
        "审计：@Transactional 内同步写 audit_log",
        "MQ：syncSend 失败 catch → INSERT wrong_item_outbox",
        "status 序列化：WrongItemVO.status 返回前端 string（'pending'/'analyzing'/'completed'/'error'）"
      ]
    },
    {
      "id": "controller",
      "label": "控制层（Controller + DTO）",
      "symbols": ["WrongItemController", "WrongAttemptController", "WrongbookExceptionHandler"],
      "key_rules": [
        "路径前缀：/wrongbook（无 /api/v1/ 前缀，由 gateway 统一加）",
        "409 CONFLICT 由 ObjectOptimisticLockingFailureException → ExceptionHandler 处理",
        "X-Request-Id header 在 POST /items 强制校验（400 if missing）"
      ]
    },
    {
      "id": "openapi",
      "label": "OpenAPI 规格（springdoc）",
      "symbols": ["OpenApiConfig"],
      "key_rules": [
        "所有 11 个端点必须有 @Operation 注解",
        "DTO 必须有 @Schema 注解",
        "路径与 §4.1 OpenAPI 片段 100% 匹配"
      ]
    }
  ],
  "has_blocking_gaps": false,
  "blocking_gaps_summary": ""
}
```

### Step 6 · 生成 business-rule-cards.json

输出路径：`design/tasks/preflight/<phase>-business-rule-cards.json`

从 arch 文档 `§1.2 假设清单`、`§2.3 不变量`、`§1.5 业务规则`、`special_requirements` 提炼，
每条规则一张 card：

```json
[
  {
    "id": "BR-01",
    "title": "状态迁移守卫",
    "layer": "service",
    "rule": "WrongItem.status 迁移仅允许预定义边：draft→analyzed→scheduled→reviewed→mastered(↔scheduled)→archived",
    "violation_behavior": "抛 400 BadRequest，message: 'invalid status transition'",
    "test_scenarios": [
      "draft → analyzed OK",
      "draft → mastered FAIL 400",
      "mastered → scheduled OK（SM-2 回退）"
    ]
  },
  {
    "id": "BR-02",
    "title": "POST 幂等键",
    "layer": "service",
    "rule": "POST /wrongbook/items 必须携带 X-Request-Id header；Redis key=idem:wb:{requestId} TTL=10min；命中时返回 200 + 原 VO（不重新插入）",
    "violation_behavior": "缺 header → 400；幂等命中 → 200（非 201）",
    "test_scenarios": [
      "首次请求 → 201",
      "相同 X-Request-Id 重试 → 200 + 原 VO",
      "缺 X-Request-Id header → 400"
    ]
  }
]
```

### Step 7 · 生成 arch-constraints.json

输出路径：`design/tasks/preflight/<phase>-arch-constraints.json`

从 arch 文档 `§7 ADR`、`frontend/packages/api-contracts` 和计划中的 ADR 决议提炼：

```json
{
  "phase": "s3",
  "orm": {
    "allowed": ["JPA", "QueryDSL"],
    "forbidden": ["MyBatis", "Mapper XML", "JDBC Template（除 migration 外）"]
  },
  "messaging": {
    "allowed": ["RocketMQ 5.x transactional message"],
    "forbidden": ["Seata", "DTM", "直接 INSERT 到 wrong_item_outbox 不经 syncSend 尝试"]
  },
  "id_strategy": {
    "allowed": ["Snowflake（应用层生成）"],
    "forbidden": ["BIGSERIAL", "UUID", "random Long"]
  },
  "state_machine": {
    "allowed": ["手写枚举 + Service 守卫"],
    "forbidden": ["Spring Statemachine 库"]
  },
  "transaction": {
    "rule": "audit_log 必须同事务写入（@Transactional 作用域内 INSERT audit_log）",
    "forbidden": ["@Async audit_log", "事后单独写审计"]
  },
  "soft_delete": {
    "column": "deleted_at TIMESTAMPTZ",
    "annotations": ["@SQLDelete(sql='UPDATE wrong_item SET deleted_at=now() WHERE id=?')", "@Where(clause='deleted_at IS NULL')"],
    "forbidden": ["deleted boolean 字段", "status=9 代替软删"]
  }
}
```

### Step 8 · 生成 contract-gaps.md

输出路径：`design/tasks/preflight/<phase>-contract-gaps.md`

**只包含 Step 4 检测到的 gap**（exact match 不出现在这里）：

```markdown
# Contract Gaps — <phase>

> 以下是前端 api-contracts 与 arch 文档之间的差距，需要 User 确认处理方式。

## BLOCKER · 必须修复才能开工

| gap ID | 前端调用 | arch 定义 | 类型 | 建议 |
|---|---|---|---|---|
| G-01 | `status: 'pending' \| 'analyzing' \| 'completed' \| 'error'` | `status: SMALLINT 0/1/2/3/8/9` | 类型不兼容 | WrongItemVO 序列化时映射为 string |

## WARNING · 建议修复但不阻断

| gap ID | 前端调用 | arch 定义 | 类型 | 建议 |
|---|---|---|---|---|
| G-02 | `mastery: number (0..100)` | `mastery: SMALLINT 0..2` | 取值范围差异 | VO 层确认返回语义（0..2 还是换算为 0..100）|

## User 确认

- [ ] 所有 BLOCKER 已明确处理方式
- [ ] 所有 WARNING 已确认可接受或已决议
- [ ] be-build-spec.json 中 `has_blocking_gaps` 已更新
- [ ] Builder 可开工
```

### Step 9 · 汇总并等待 User 确认

打印：

```
✅ Be-preflight 完成 · <phase>

   API 端点数：N 个
   业务规则 card 数：M 条
   架构约束：已提炼
   Contract Gaps：
     BLOCKER: X 项
     WARNING: Y 项

   输出：
     design/tasks/preflight/<phase>-be-build-spec.json
     design/tasks/preflight/<phase>-business-rule-cards.json
     design/tasks/preflight/<phase>-arch-constraints.json
     design/tasks/preflight/<phase>-contract-gaps.md

⏸  请 User review contract-gaps.md 后确认，Builder 方可开工。
   （若 BLOCKER = 0，可直接运行 /be-builder <phase>）
```

## 硬约束

1. **不写任何后端代码** — 只分析产出 spec 文件，不动 `backend/` 任何文件
2. **信息必须来自读取文件** — 不凭记忆写 API 路径或字段名；所有约束必须有来源文件 + 行号
3. **BLOCKER 存在时必须停止** — `has_blocking_gaps = true` 时，打印 gap 列表，等 User 决议后再继续
4. **arch 文档是后端真源** — api-contracts 是前端调用真源；两者冲突时必须记录 gap，不自行决策
5. **等待 User 确认后才声明完成** — 不自行跳过 contract-gaps.md 的 review 步骤

## 参考文档

- `design/arch/<phase>-*.md` — Phase 架构文档（API / 领域模型 / 状态机 / 符号清单）
- `frontend/packages/api-contracts/src/` — 前端 API 调用真源
- `design/analysis/<phase>-business-analysis.yml` — 业务决策记录
- `design/落地实施计划_v1.0_AI自动执行.md` — 整体实施计划（Phase 范围 / DoD）
