# AI 后端自动开发三段式方案

> 版本：v1.2 · 2026-04-27
> 背景：在执行落地实施计划（`落地实施计划_v1.0_AI自动执行.md`）的后端 Phase（S3/S4/S5/S6/S11）时，AI 仅凭 Phase 规范文档开发，产出的后端存在三类偏差：接口形状和前端对不上、业务规则实现有遗漏、架构模式用错。本方案设计三段式 Skill Pipeline，在动代码前强制完成三类信息提取与对齐，确保最终产出是可运行、业务正确、前端可直接对接的后端服务。

---

## 问题定性

### 三类偏差，三个独立来源

AI 读完 Phase 规范后写出的代码，可能同时存在三类偏差——它们来源不同，解法也不同：

| 偏差类型 | 典型表现 | 根本原因 |
|---|---|---|
| **① 接口形状偏差** | status 枚举用了领域值而非前端期望值；缺少 `mastery` 字段；tags 接口设计不一致 | Phase 规范没有对照 `api-contracts` 包写，AI 不知道前端实际期望什么 |
| **② 业务行为偏差** | `@Version` 注解加了但 PATCH 接口没校验 version；状态机没有 guard 导致非法转换；幂等用了 GET+SET 而非原子 SET NX EX | Phase 规范里业务规则是散文，AI 实现时容易简化或遗漏 |
| **③ 架构模式偏差** | 业务逻辑写在 Controller；用了 JdbcTemplate 而非 QueryDSL；RocketMQ 不在事务边界内直接 syncSend | 架构约束散落在规范各处，AI 一次读完 500 行后细节被稀释 |

**实证（S3）**：

接口形状偏差 4 个（和 `api-contracts` 包的真实断点）：

| 断点 | S3 规范 | 前端实际期望 | 页面影响 |
|---|---|---|---|
| Status 枚举 | `draft/analyzed/scheduled/reviewed/mastered` | `pending/analyzing/completed/error` | ListPage 轮询逻辑永远不触发 |
| `mastery` 字段 | `WrongItemVO` 未包含 | `mastery: number (0..100)` | 掌握度分类过滤 + UI 全挂 |
| 标签更新接口 | `POST/DELETE /items/{id}/tags/{tagId}` 逐条 | `PATCH /items/{id}/tags` 批量替换 | 标签保存直接 404 |
| 标签列表接口 | 不在 9 个端点清单内 | `GET /wrongbook/tags` | 标签选择器无数据源 |

业务行为偏差举例：
- `@SQLDelete` 注解有，但 `@Where` 子句写错 → 软删条目仍能查到
- 状态机枚举有，Service 层无 guard → `reviewed → draft` 非法转换没有拦截
- `producer.publish()` 调用在 `@Transactional` 边界外 → 主库回滚后消息已发出

架构模式偏差举例：
- `repository.findById()` 直接出现在 Controller → 层次边界违反
- 幂等用了先 GET 后 SET → 高并发下双写
- 使用 `JdbcTemplate.queryForList()` 做跨表查询 → 白名单 Block

### 根本原因

S3 规范文档约 500 行，混合了四种性质不同的内容：

```
业务规则（必须实现）      ← AI 应该重点读，但容易被稀释
架构约束（必须遵守）      ← AI 应该遵守，但容易遗忘
执行步骤（参考用）        ← AI 读了但会当作主线走
背景说明（理解用）        ← AI 读了会影响实现偏好
```

AI 一次性读完，重要约束被大量文本稀释；加上规范是"后端视角写的"，完全没有引用前端 `api-contracts` 包——所以三类偏差同时出现。

---

## 核心思路：三类约束，分开提取，精准注入

Phase 规范里隐含三类约束，性质不同，需要分开对待：

```
约束类型              来源                              回答的问题
────────────────────────────────────────────────────────────────
① API 契约           api-contracts 包（前端真值）       对外暴露什么形状？
② 业务规则           business-analysis.yml + 规范假设清单  业务上必须发生什么？
③ 架构约束           design/arch/<phase>.md + 工具白名单  用什么方式实现？
```

**提取后分层注入**：每个 Builder Layer 只收到它需要的那类约束摘录，不收到整个规范文档：

```
Layer 1 · entity      读 ③ 架构约束（JPA 注解、软删、pgvector）
Layer 2 · repo        读 ③ 架构约束（JPA+QueryDSL、禁 native SQL）
Layer 3 · service     读 ② 业务规则（主）+ ③ 架构约束（辅）
Layer 4 · controller  读 ① API 契约 + ③ 架构约束（层次边界）
Layer 5 · openapi     读 ① API 契约（端点数验证）
```

---

## 适用范围

本方案适用于 `落地实施计划_v1.0_AI自动执行.md` 中所有**后端 A 级 Phase**：

| Phase | 服务 | 前端 client（① API 契约来源） | 架构文档（③ 架构约束来源） |
|---|---|---|---|
| **S3** | `wrongbook-service` | `api-contracts/clients/wrongbook.ts` | `design/arch/s3-wrongbook.md` |
| **S4** | `ai-analysis-service` | `api-contracts/clients/analysis.ts` | `design/arch/s4-ai-analysis.md` |
| **S5** | `review-plan-service` | `api-contracts/clients/review.ts`（待建） | `design/arch/s5-review-plan.md` |
| **S6** | `file-service` | `api-contracts/clients/files.ts` | `design/arch/s6-file-service.md` |
| **S11** | `anonymous-service` | `api-contracts/clients/anonymous.ts`（待建） | `design/arch/s11-anonymous.md` |

S1（纯 DDL）、S2（网关骨架）、S5.5（联调闸）使用简化版，豁免前端契约对齐步骤。

---

## 三段式 Skill Pipeline

### 总览

```
信息输入层（三类来源）
  ① api-contracts/src/              前端真值
  ② <phase>-business-analysis.yml   业务规则
  ③ design/arch/<phase>.md          架构约束
     + 落地实施计划 §X.4 工具白名单
           │
           ▼
  ┌──────────────────────────────────┐
  │  Stage 1: be-preflight           │  提取三类约束，产三份施工图
  │  /be-preflight <phase>           │
  └──────────────┬───────────────────┘
                 │ has_blocking_gaps?
                 ├── YES → 停机，User 确认 contract-gaps.md
                 └── NO  ↓
  ┌──────────────────────────────────┐
  │  Stage 2: be-builder             │  分层构建，每层精准注入
  │  /be-builder <phase> <layer>     │
  │  layer 1: entity     (读 ③)      │  → mvn compile ✅
  │  layer 2: repo       (读 ③)      │  → mvn compile ✅
  │  layer 3: service    (读 ②+③)   │  → mvn compile ✅
  │  layer 4: controller (读 ①+③)   │  → mvn verify  ✅
  │  layer 5: openapi    (读 ①)      │  → paths ≥ N   ✅
  └──────────────┬───────────────────┘
                 ▼
  ┌──────────────────────────────────┐
  │  Stage 3: be-accept              │  三维验收
  │  /be-accept <phase>              │
  │  维度 A：接口契约（① 形状对齐）  │
  │  维度 B：业务行为（② 行为正确）  │
  │  维度 C：架构合规（③ 模式遵守）  │
  └──────────────┬───────────────────┘
                 ▼
  reports/phase-<phase>-be-acceptance.md
                 │
                 ├── 全部通过 → git tag <phase>-done
                 └── 有失败  → 打回指定 Layer 修复
```

### 设计原则

1. **三真值源优先级**：`api-contracts`（前端） > `business-analysis.yml`（业务） > Phase 规范（实现参考）。三者冲突时 pre-flight 停机让 User 决策，AI 不自行选择。
2. **精准注入，不注入全文**：每个 Builder Layer 的 context 只包含本层需要的那类约束摘录，防止 AI 因信息过载走捷径。
3. **门禁必须机器可判定**：`mvn compile`、`mvn verify`、`grep`、`curl` 返回值——不允许 AI 自我声明"已通过"。
4. **停机优于漂移**：有阻塞性冲突或门禁连续 3 次失败，Skill 必须停机，不允许 AI 假设继续。
5. **文件是阶段间的唯一交接媒介**：be-preflight 与 be-builder 之间、层与层之间，交接的是**文件路径**，不是对话 context。每层 Builder 启动时从磁盘读取所需文件的对应片段，与前序对话历史完全隔离。这是防止 context 膨胀和约束稀释的根本机制。

---

## Context 管理：文件管道原则

### 问题

be-preflight 产出 3 个 JSON 文件（约 10-20KB），如果把它们全部注入每一层 Builder 的 context，加上已写好的代码，到 Layer 4-5 时 context 已经过大，Layer 1 写下的架构约束会被后续内容淹没。**这不只是 context 大小问题，是 context 稀释问题**——信息越多，重要约束越容易被 AI 跳过。

### 核心原则：文件是管道，不是 context

```
Unix pipe 类比：
  process A → stdout → file → process B 读 file → 不共享进程内存

be-preflight → 写 3 个 JSON 到磁盘 → be-builder layer N 读对应片段 → 不共享对话历史
```

每个阶段（每层 Builder）是独立启动的新 Agent，它的 context 只包含：
- **本层需要读的文件名 + 读取范围**（不是整个文件）
- **当前 git 状态**（已有代码，通过 git diff 或文件读取获得）
- **本层的门禁命令**

不包含：be-preflight 的任何对话历史、其他层的对话历史、完整的三个 JSON 文件。

### 三个 Context 控制机制

#### 机制一：分层过滤读取（必须做）

每层只读本层需要的字段，由 be-builder Skill 的启动 prompt 明确指定读取范围：

```
Layer 1 启动时注入：
  arch-constraints.json → 只读 pattern_mandates[applies_to_layers 含 1]
                        + layer_boundaries.entity
  （约 1-2KB，不读 integration_specs / nfr_targets / tool_allowlist）

Layer 2 启动时注入：
  arch-constraints.json → 只读 pattern_mandates[applies_to_layers 含 2]
                        + layer_boundaries.repository
  （约 1KB）

Layer 3 启动时注入：
  business-rule-cards.json → 当前批次的 rule_cards（见机制二）
  arch-constraints.json    → pattern_mandates[applies_to_layers 含 3]
                           + integration_specs + nfr_targets + layer_boundaries.service
  （约 4-6KB，是五层中最重的）

Layer 4 启动时注入：
  be-build-spec.json       → endpoints + vo_definitions
  arch-constraints.json    → layer_boundaries.controller（禁止项清单）
  （约 3-4KB）

Layer 5 启动时注入：
  be-build-spec.json       → build_layers[4].validation（仅端点数期望值）
  （< 1KB）
```

#### 机制二：Sub-layer 拆分（Layer 3 rule_cards 过多时）

当 `business-rule-cards.json` 的 rule_cards 超过 5 张（即 Service 方法较多），Layer 3 拆成多个 Sub-layer，每个 Sub-layer 只处理一批 rule_cards：

```
判断条件：rule_cards.length > 5

拆分规则：每批 2-3 张，按领域相关性分组（同一聚合根的方法放一批）

示例（S3 有 6+ 张规则卡）：
  Layer 3a：createItem + updateStatus（写操作主流程）
    → mvn compile ✅ → commit [SC-07.AC-1]
  Layer 3b：softDelete + updateTags（状态变更）
    → mvn compile ✅ → commit [SC-04.AC-1][SC-02.AC-1]
  Layer 3c：listItems + getItem + getTags（读操作）
    → mvn compile ✅ → commit [SC-08.AC-1]

每个 Sub-layer 启动时 context 只含本批 rule_cards + arch-constraints Layer 3 摘录
编译门禁是增量的：只要新增代码能编译，不要求全量重新编译
```

#### 机制三：独立 Agent per Layer（推荐实现方式）

每一层 be-builder 作为独立的 Sub-agent 调用，彻底隔离对话历史：

```
/be-builder s3 layer1
  └── 启动新 Sub-agent
      context = {
        task: "实现 Layer 1 entity",
        input_files: { arch_constraints_slice: "..." },  // 过滤后的片段
        existing_code: git_diff_since_last_commit,
        gate_command: "mvn -pl wrongbook-service -am -q -DskipTests compile"
      }
      → 写代码 → 门禁 → commit → 返回结果给主流程

/be-builder s3 layer2
  └── 启动新 Sub-agent（无 layer1 对话历史）
      context = {
        task: "实现 Layer 2 repo",
        input_files: { arch_constraints_slice: "..." },  // 新的过滤片段
        existing_code: git_diff_since_s3-L1-commit,
        gate_command: "mvn ... compile && ls Q*.java | wc -l"
      }
```

每个 Sub-agent 的 context 大小固定，不随层数增加而膨胀。

### Context 大小对比

| 方案 | Layer 3 有效 context | Layer 4 有效 context | 问题 |
|---|---|---|---|
| 全量注入（不控制） | ~20KB + 已有代码 | ~20KB + 更多代码 | 约束被稀释，越写越偏 |
| 机制一（过滤读取） | ~5KB + 已有代码 | ~4KB + 已有代码 | 聚焦，约束清晰 |
| 机制一 + 二（Sub-layer） | ~3KB + 本批相关代码 | ~4KB + 已有代码 | Layer 3 粒度最细 |
| 机制一 + 二 + 三（独立 Agent） | ~3KB，无历史负担 | ~4KB，无历史负担 | context 永远干净 |

### 文件存放约定

三个施工图文件存放在固定路径，Builder 按路径读取，不依赖对话传递：

```
design/analysis/<phase>-be-build-spec.json
design/analysis/<phase>-business-rule-cards.json
design/analysis/<phase>-arch-constraints.json
design/analysis/<phase>-contract-gaps.md
```

Builder 启动时第一步永远是：**读文件，不靠记忆**。Agent 不应该凭对话历史里"我记得 be-preflight 说过的约束"来写代码，必须重新从文件读取，确保约束的精确性。

---

## Stage 1：`/be-preflight <phase>` — 三类约束提取与对齐

### 目标

从三个信息源分别提取约束，合并成三份施工图文件，同时暴露冲突点供 User 决策。

### 信息源与提取内容

**来源 ①：api-contracts/src/**（前端真值，提取 API 契约）

```
读 clients/<phase>.ts  → 所有 HTTP 调用：方法、路径、请求 body/params、response 类型引用
读 types.ts            → 展开所有引用类型：字段名、类型、枚举值、可选/必填
```

**来源 ②：business-analysis.yml + Phase 规范假设清单**（提取业务规则）

```
读 design/analysis/<phase>-business-analysis.yml
  → ac_coverage[*].user_journey      每个 AC 的完整操作序列
  → ac_coverage[*].domain_entities   涉及的领域对象
  → ac_coverage[*].risks             需要防守的边界条件

读 落地实施计划 §X.1 假设清单（A1-A12 等）
  → 每条假设提取为一条业务规则（"幂等作用域仅 POST"/"软删后 GET 返回 404 非 200"）
```

**来源 ③：design/arch/<phase>.md + 工具白名单**（提取架构约束）

```
读 design/arch/<phase>.md
  § 领域模型（classDiagram）→ Entity 字段、关联关系、@Version/@SQLDelete 位置
  § 数据流（sequenceDiagram）→ 调用链顺序（Controller→Service→Repo→MQ）
  § 事件契约              → MQ topic、payload 字段、降级路径
  § 非功能指标            → P95/P99 目标、重试上限、TTL 值
  § 外部依赖              → 每个中间件的超时/重试/降级配置
  § ADR 候选              → 强制模式（JPA not MyBatis）、禁用模式（Spring Statemachine）

读 落地实施计划 §X.4 工具白名单
  → 允许列表 + 显式禁止列表
```

### 执行步骤

```
Step 1  提取 ① API 契约（来源：api-contracts）
Step 2  提取 ② 业务规则（来源：business-analysis.yml + 规范假设清单）
Step 3  提取 ③ 架构约束（来源：design/arch/<phase>.md + 工具白名单）
Step 4  对比 Step 1（前端真值）vs Phase 规范端点清单，找冲突：
          ENDPOINT_MISSING   前端调但规范没有           → BLOCKING
          ENUM_MISMATCH      枚举值集合不同             → BLOCKING
          API_DESIGN_MISMATCH HTTP 方法或路径结构不同   → BLOCKING
          FIELD_MISSING      response 缺前端要的字段    → WARNING
          NAMING_MISMATCH    camelCase vs snake_case    → WARNING
          FIELD_EXTRA        规范有但前端不用           → INFO
Step 5  产出三份施工图文件（见下方格式）
Step 6  产出 contract-gaps.md（仅含 BLOCKING + WARNING 条目，需 User 决策）
Step 7  has_blocking_gaps = true → 停机；false → 输出"施工图就绪"
```

### 产出文件一：`be-build-spec.json`（API 契约施工图）

```json
{
  "phase": "s3",
  "service": "wrongbook-service",
  "generated_at": "2026-04-27T10:00:00Z",
  "has_blocking_gaps": false,
  "endpoints": [
    {
      "id": "create_item",
      "http_method": "POST",
      "path": "/wrongbook/items",
      "sc_ac": "SC-07.AC-1",
      "request": {
        "body_fields": [
          { "name": "subject",   "type": "string",   "required": true  },
          { "name": "stem_text", "type": "string",   "required": true  },
          { "name": "tags",      "type": "string[]", "required": false },
          { "name": "image_id",  "type": "string",   "required": false }
        ],
        "idempotency_header": "X-Request-Id"
      },
      "response": { "success_code": 201, "body_type": "WrongItemVO" }
    },
    {
      "id": "list_items",
      "http_method": "GET",
      "path": "/wrongbook/items",
      "sc_ac": "SC-08.AC-1",
      "request": {
        "query_params": [
          { "name": "cursor",  "type": "string",              "required": false },
          { "name": "status",  "type": "enum:active|mastered","required": false },
          { "name": "subject", "type": "string",              "required": false },
          { "name": "limit",   "type": "number",              "required": false }
        ]
      },
      "response": {
        "success_code": 200,
        "body_fields": ["items:WrongItemVO[]", "next_cursor:string?", "has_more:boolean?"]
      }
    }
  ],
  "vo_definitions": {
    "WrongItemVO": {
      "source": "api-contracts/src/types.ts",
      "fields": [
        { "name": "id",         "type": "string",                              "note": "后端 Long → JSON string，@JsonSerialize(using=LongToStringSerializer)" },
        { "name": "subject",    "type": "string"                                                      },
        { "name": "stem_text",  "type": "string",                              "note": "@JsonProperty(\"stem_text\")，规范用 stemText" },
        { "name": "tags",       "type": "string[]"                                                    },
        { "name": "status",     "type": "enum:pending|analyzing|completed|error", "note": "GAP-01 已解决：VO 层做领域状态→前端枚举映射" },
        { "name": "mastery",    "type": "number(0..100)",                      "note": "GAP-03 已解决：新增字段，默认 0，S5 异步回填" },
        { "name": "image_url",  "type": "string?"                                                     },
        { "name": "created_at", "type": "string(ISO8601)"                                             },
        { "name": "version",    "type": "number"                                                      }
      ]
    }
  },
  "build_layers": [
    { "layer": 1, "name": "entity",     "primary_input": "arch-constraints.json" },
    { "layer": 2, "name": "repo",       "primary_input": "arch-constraints.json" },
    { "layer": 3, "name": "service",    "primary_input": "business-rule-cards.json", "secondary_input": "arch-constraints.json" },
    { "layer": 4, "name": "controller", "primary_input": "be-build-spec.json",       "secondary_input": "arch-constraints.json" },
    { "layer": 5, "name": "openapi",    "primary_input": "be-build-spec.json", "validation": "paths >= 10" }
  ]
}
```

### 产出文件二：`business-rule-cards.json`（业务规则施工图）

每个 Service 方法一张规则卡，把规范假设清单和 business-analysis.yml 的 user_journey 结构化：

```json
{
  "phase": "s3",
  "rule_cards": [
    {
      "method": "createItem",
      "sc_ac": "SC-07.AC-1",
      "pre_conditions": [
        "subject 必须在 tag_taxonomy 预置学科内（math/physics/chemistry/english/chinese）",
        "stem_text 不能为空，长度 ≥ 2"
      ],
      "invariants": [
        "同 X-Request-Id → 返回相同 id，DB count = 1（幂等，来源：假设 A9）",
        "初始 status = DRAFT（内部），VO 返回 pending",
        "初始 mastery = 0",
        "必须先 Redis SET NX EX idem:wb:{requestId}，再 repo.save()，顺序不可颠倒"
      ],
      "side_effects": [
        "persist WrongItem（主表）",
        "publish wrongbook.item.changed {action:created, itemId, version, occurredAt}（RocketMQ 事务消息，来源：假设 A7）",
        "RocketMQ 失败 → 降级写 wrong_item_outbox（status=PENDING），不抛出异常",
        "同事务写 audit_log（来源：假设 A5）",
        "Redis SET idem:wb:{requestId}=itemId TTL 10min（来源：假设 A9）"
      ],
      "error_cases": [
        "subject 不在 taxonomy → 400 INVALID_SUBJECT",
        "stem_text 空 → 400 INVALID_INPUT",
        "Redis 连接失败 → 降级 DB 唯一索引兜底，不中断主流程"
      ]
    },
    {
      "method": "updateStatus",
      "sc_ac": "SC-07.AC-2",
      "pre_conditions": ["itemId 存在且未软删"],
      "invariants": [
        "状态转换必须符合状态机（来源：假设 A3）：",
        "  允许：draft→analyzed, analyzed→scheduled, scheduled→reviewed, reviewed→mastered",
        "  允许双向：mastered↔scheduled（SM-2 质量 < 3 回退）",
        "  禁止：任何其他转换 → 抛 IllegalStateTransitionException → 400"
      ],
      "side_effects": [
        "publish wrongbook.item.changed {action:updated}（同 createItem 的 outbox 降级逻辑）"
      ],
      "error_cases": [
        "非法状态转换 → 400 INVALID_STATE_TRANSITION",
        "乐观锁冲突 → 捕获 ObjectOptimisticLockingFailureException → 指数退避重试最多 3 次 → 超限返回 409（来源：假设 A8）"
      ]
    },
    {
      "method": "softDelete",
      "sc_ac": "SC-04.AC-1",
      "invariants": [
        "软删后 GET /wrongbook/items/{id} 必须返回 404，不是 200（来源：假设 A12）",
        "软删不物理删除，@SQLDelete + @Where(deleted=false) 实现过滤"
      ],
      "side_effects": [
        "同事务写 audit_log（action=delete）",
        "publish wrongbook.item.changed {action:deleted}"
      ],
      "error_cases": [
        "id 不存在 → 404",
        "已软删再次删 → 404（@Where 已过滤，findById 返回 empty）"
      ]
    },
    {
      "method": "updateTags",
      "sc_ac": "SC-02.AC-1",
      "invariants": [
        "批量替换：先删 WrongItemTag 旧关联，再插新关联（来源：GAP-02 决策：PATCH 批量替换）",
        "version 字段必须校验（If-Match header），不匹配 → 409"
      ],
      "error_cases": [
        "version 不匹配 → 409 CONFLICT",
        "tag 不在 TagTaxonomy 且非用户自定义格式 → 400 INVALID_TAG（自定义 tag 允许，预置学科 tag 要校验大小写）"
      ]
    }
  ]
}
```

### 产出文件三：`arch-constraints.json`（架构约束施工图）

把架构文档和工具白名单的约束结构化，按四类组织：

```json
{
  "phase": "s3",
  "pattern_mandates": [
    {
      "scenario": "数据库访问",
      "required": "Spring Data JPA + QueryDSL（来源：ADR-0006，假设 A6）",
      "banned": ["MyBatis", "MyBatis-Plus", "JdbcTemplate 跨表查询", "nativeQuery > 3行"],
      "applies_to_layers": [2, 3],
      "verification": "! git grep -nE 'mybatis|BaseMapper<|@MapperScan' -- backend/<service>/"
    },
    {
      "scenario": "消息发布",
      "required": "RocketMQ 事务消息 sendMessageInTransaction + 失败降级 wrong_item_outbox（来源：ADR-0002，假设 A7）",
      "banned": ["直接 syncSend 不在事务内", "Seata 分布式事务"],
      "applies_to_layers": [3],
      "verification": "grep 'sendMessageInTransaction\\|wrong_item_outbox' WrongItemProducer.java"
    },
    {
      "scenario": "软删除",
      "required": "@SQLDelete(sql=\"UPDATE wrong_item SET deleted=true WHERE id=?\") + @Where(clause=\"deleted=false\")",
      "banned": ["手写 WHERE deleted=false 查询", "Hibernate Envers"],
      "applies_to_layers": [1],
      "verification": "grep '@SQLDelete\\|@Where' WrongItem.java"
    },
    {
      "scenario": "状态机",
      "required": "手写枚举 + Service 层 guard 方法（来源：假设 A3，ADR-0010）",
      "banned": ["Spring Statemachine", "状态转换逻辑写在 Entity 内"],
      "applies_to_layers": [1, 3],
      "verification": "! grep 'spring-statemachine' pom.xml"
    },
    {
      "scenario": "幂等实现",
      "required": "Redis SET NX EX 原子命令（来源：假设 A9）",
      "banned": ["先 GET 后 SET（非原子）", "DB 唯一索引作为唯一兜底（可作降级但不作主路径）"],
      "applies_to_layers": [3],
      "verification": "grep 'setIfAbsent\\|SET.*NX.*EX' IdempotencyService.java"
    },
    {
      "scenario": "乐观锁",
      "required": "@Version Long version，JPA 自动管理，捕获 ObjectOptimisticLockingFailureException（来源：假设 A8）",
      "banned": ["手动 version++", "timestamp-based 乐观锁"],
      "applies_to_layers": [1, 3],
      "verification": "grep '@Version' WrongItem.java"
    }
  ],
  "layer_boundaries": {
    "controller": {
      "allowed": ["参数校验（@Valid）", "DTO 转换（MapStruct）", "HTTP 状态码映射", "ExceptionHandler"],
      "forbidden": ["业务判断", "repository 直接调用", "事务注解 @Transactional", "MQ 发送"]
    },
    "service": {
      "allowed": ["业务规则", "@Transactional 事务边界", "幂等", "乐观锁重试", "领域事件发布"],
      "forbidden": ["HTTP 细节（HttpServletRequest）", "JSON 序列化", "直接返回 Entity"]
    },
    "repository": {
      "allowed": ["JPA 查询", "QueryDSL Predicate", "自定义 @Query（≤3行）"],
      "forbidden": ["业务计算", "事务控制", "MQ 调用"]
    },
    "verification": [
      "! grep 'repository\\.' -- controller/",
      "! grep '@Transactional' -- controller/",
      "! grep 'HttpServletRequest' -- service/"
    ]
  },
  "integration_specs": {
    "rocketmq":    { "timeout_ms": 3000, "retry": 2,    "fallback": "wrong_item_outbox 表降级写入" },
    "redis":       { "timeout_ms": 200,  "fallback":    "DB 唯一索引兜底，不中断主流程" },
    "nacos":       { "timeout_ms": 3000, "fallback":    "读本地 bootstrap.yml" },
    "postgresql":  { "hikari_min": 5,    "hikari_max":  20, "open_in_view": false },
    "sentinel":    { "qps_limit": 500,   "failure_ratio": 0.5 }
  },
  "nfr_targets": {
    "write_p95_ms":              200,
    "read_p99_ms":               100,
    "idempotency_ttl_min":       10,
    "optimistic_lock_max_retry": 3,
    "availability_monthly":      "99.9%"
  },
  "tool_allowlist": {
    "required": ["spring-data-jpa:3.2.x", "querydsl-jpa:5.0.x", "rocketmq-spring-boot-starter:2.3.x", "mapstruct:1.5.x", "springdoc-openapi-starter-webmvc-ui:2.5.x", "testcontainers:1.19.x"],
    "banned":   ["mybatis", "mybatis-plus", "spring-statemachine", "hibernate-envers", "seata"]
  }
}
```

### 产出文件四：`contract-gaps.md`（User 决策表，仅含冲突项）

```markdown
# S3 契约对齐 — 需要 User 决策的冲突点

> 本文件由 be-preflight 自动生成。请在每项「决策」处填写后，重新运行 `/be-preflight s3`。

## BLOCKING（必须解决才能开工）

### GAP-01：Status 枚举不一致
| | 值 |
|---|---|
| S3 规范（领域状态机） | `draft/analyzed/scheduled/reviewed/mastered` |
| 前端 types.ts 期望 | `pending/analyzing/completed/error` |
**影响**：ListPage 轮询检查 `status === 'analyzing'`，永远不触发。
**选项**：A) VO 层做映射（推荐）/ B) 修改前端 types.ts / C) 自定义：___
**决策**：

### GAP-02：标签更新接口设计不一致
| | 设计 |
|---|---|
| S3 规范 | `POST/DELETE /items/{id}/tags/{tagId}`（逐条） |
| 前端调用 | `PATCH /items/{id}/tags`（批量替换 + If-Match） |
**选项**：A) 改为实现 PATCH 批量替换（推荐）/ B) 修改前端 / C) 自定义：___
**决策**：

## WARNING（建议解决，不阻塞但会导致功能缺失）

### GAP-03：`mastery` 字段缺失
前端 `WrongItemVO.mastery` 在规范 WrongItemVO 中未提及，ListPage 过滤 + UI 依赖它。
**建议**：新增字段 default 0，S5 异步回填。
**决策**：

### GAP-04：`GET /wrongbook/tags` 缺失
前端 `wrongbookClient.getTags()` 调此接口，规范 9 端点未包含。
**建议**：新增第 10 个端点返回 TagTaxonomy 列表。
**决策**：
```

---

## Stage 2：`/be-builder <phase> <layer>` — 精准注入分层构建

### 目标

按依赖顺序逐层实现，每层只注入本层需要的施工图摘录，每层有机器可判定的编译门禁。

### 五层定义

| Layer | 名称 | 主要输入 | 辅助输入 | 实现内容 | 门禁 |
|---|---|---|---|---|---|
| 1 | `entity` | `arch-constraints.json` A类（模式强制）+ B类（层次边界） | — | JPA Entity + Flyway migration | `mvn compile` exit 0 |
| 2 | `repo` | `arch-constraints.json` A类（JPA+QueryDSL）+ B类（repo边界） | — | Repository 接口 + QueryDSL Predicates + Q 类 | `mvn compile` exit 0 + Q类文件存在 |
| 3 | `service` | `business-rule-cards.json`（全部规则卡） | `arch-constraints.json` A/C/D类 | Service + IdempotencyService + MQ Producer | `mvn compile` exit 0 |
| 4 | `controller` | `be-build-spec.json` endpoints + vo_definitions | `arch-constraints.json` B类（层次边界） | Controller + DTO + MapStruct + ExceptionHandler + IT | `mvn verify` exit 0，IT 全绿 |
| 5 | `openapi` | `be-build-spec.json` build_layers[4].validation | — | 导出 OpenAPI YAML 入库 | paths ≥ 预期数 |

### 每层执行模式

每层作为独立 Sub-agent 启动，严格按以下步骤执行：

```
Step 0  【读文件，不靠记忆】
        从磁盘读取本层对应的施工图片段（见"Context 管理"章节的过滤规则）
        禁止使用对话历史中"AI 记得的"约束——必须从文件重新读取

Step 1  检查门禁前置条件
        确认上一层的 commit 已存在（git log --oneline | grep "L<N-1>"）
        否则停机，提示先完成上一层

Step 2  按本层施工图片段写代码
        Layer 1-2：按 arch-constraints 片段实现
        Layer 3：按 rule_cards 逐张实现（每张一个方法，不跳过）
        Layer 4：按 be-build-spec endpoints 逐个实现

Step 3  运行门禁命令
        通过 → 执行 Step 4
        失败 → 分析错误输出，修复，重新门禁（最多 3 次）
               3 次未过 → 停机，输出完整错误摘要，等 User 介入

Step 4  运行架构合规扫描（arch-constraints.json 中本层的 verification 命令）
        全部通过 → 执行 Step 5
        有失败   → 修复后重新门禁（不计入 Step 3 的重试次数）

Step 5  commit "feat(<phase>-L<N>[-<sub>]): <layer-name> [SC-XX-ACY]"
        打印"Layer <N> ✅ 建议执行：/be-builder <phase> layer<N+1>"
        Sub-agent 退出，释放 context
```

**Sub-layer 触发逻辑**（仅 Layer 3）：

```
be-builder 启动时检查：
  rule_cards.length <= 5 → 正常执行 Layer 3（单次）
  rule_cards.length > 5  → 自动拆分：
    输出"rule_cards = N 张，自动拆为 M 个 Sub-layer"
    Sub-layer 分组（每批 2-3 张，按领域相关性）
    依次执行 Layer 3a → 3b → 3c，每批独立门禁 + commit
    全部通过后打印"Layer 3 ✅（共 M 个 Sub-layer）"
```

### Layer 1（entity）特殊要求

Builder 必须从 `arch-constraints.json` 的 `pattern_mandates` 中找到所有 `applies_to_layers` 含 1 的条目，逐一落实到 Entity 代码：

```
软删除：@SQLDelete + @Where(clause="deleted=false")
乐观锁：@Version Long version
pgvector：embedding 字段用 PgVectorUserType（自定义 Hibernate 类型）
状态枚举：定义内部领域枚举（DRAFT/ANALYZED/...），不使用 String
```

### Layer 3（service）特殊要求

Service 层是业务规则最密集的地方，Builder 对每张 Rule Card 逐一实现：

```
对每张 rule_card：
  读 invariants  → 实现对应的业务逻辑和守护条件
  读 side_effects → 实现每一个副作用（顺序、事务边界、降级路径）
  读 error_cases  → 实现每种异常的捕获和返回码映射

再读 arch-constraints.json 的 integration_specs：
  rocketmq.timeout / retry / fallback → 对应 WrongItemProducer 配置
  redis.fallback                      → IdempotencyService 的降级逻辑
  nfr_targets.optimistic_lock_max_retry → Service 重试上限
```

---

## Stage 3：`/be-accept <phase>` — 三维验收

### 目标

从三个维度独立验证，确保接口形状、业务行为、架构实现三者同时达标。

### 三个验收维度

| 维度 | 来源 | 执行方式 | 对标前端三段式 |
|---|---|---|---|
| **A · 接口契约** | `be-build-spec.json` | 用前端真实调用格式 curl 每个端点，校验 response 字段/枚举/类型 | 视觉 pixel diff |
| **B · 业务行为** | `business-rule-cards.json` | 跑真实业务场景，验证 side_effects 和 error_cases 实际发生 | AC 覆盖矩阵 |
| **C · 架构合规** | `arch-constraints.json` | 静态 grep 扫描 + 运行时降级行为验证 | 设计系统 grep 扫描 |

### 维度 A：接口契约验收

对 `be-build-spec.json` 每个 endpoint，用 api-contracts 真实调用格式打：

```bash
# 包含所有必须的 headers（Authorization / X-Request-Id / If-Match）
# body 字段名用 snake_case（以 types.ts 为准）
# 校验：HTTP 状态码 / 所有 required 字段存在 / 枚举值在范围内 / id 为 string 类型
```

### 维度 B：业务行为验收

对每张 Rule Card 的 side_effects 和 error_cases 跑真实场景验证：

**S3 业务场景（共 9 个）：**

```
Scene 1 · 录入主流程（Capture 页）
  POST /wrongbook/items {subject:"math", stem_text:"2x+3=7"}
  → 201 · body.status=="pending" · body.mastery==0 · body.version==0
  → DB: wrong_item count=1
  → RocketMQ 或 wrong_item_outbox: 有 action=created 记录（验证 side_effect 不丢失）

Scene 2 · 列表加载（List 页）
  GET /wrongbook/items?status=active&subject=math&limit=20
  → 200 · body.items 是数组 · body.has_more 是 boolean

Scene 3 · 详情查看（Detail 页）
  GET /wrongbook/items/{id}
  → 200 · WrongItemVO 所有字段存在（含 mastery / version / tags）

Scene 4 · 标签批量替换（Detail 页 Tag Sheet）
  PATCH /wrongbook/items/{id}/tags  body:["math","algebra"]  If-Match:0
  → 200 或 204
  → DB: wrong_item_tag 恰好 2 行（不是追加，是替换）

Scene 5 · 软删后不可见（Detail 页 Delete）
  DELETE /wrongbook/items/{id} → 204
  GET /wrongbook/items/{id}    → 404（不是 200 带 deleted:true）
  → DB: audit_log 有 action='delete' 行（side_effect 验证）

Scene 6 · 幂等性（同 requestId 两次 POST）
  POST × 2（同 X-Request-Id） → 两次返回相同 id
  → DB: wrong_item count=1（不是 2）

Scene 7 · 乐观锁冲突（并发 PATCH）
  两线程同时 PATCH，body.version=0
  → 一次 200，一次 409

Scene 8 · 非法状态转换
  手动 PATCH status: reviewed→draft（非法转换）
  → 400 INVALID_STATE_TRANSITION

Scene 9 · Redis 降级（模拟 Redis 不可用）
  临时关闭 Redis，执行 POST /wrongbook/items
  → 仍然 201（降级到 DB 唯一索引兜底，不返回 500）
```

### 维度 C：架构合规验收

分静态扫描和运行时验证两类：

**静态扫描**（来自 `arch-constraints.json` 的 verification 字段）：

```bash
# 零 MyBatis
! git grep -nE "mybatis|BaseMapper<|@MapperScan" -- backend/<service>/

# 层次边界：Controller 中无 repository 调用
! git grep -nE "repository\." -- backend/<service>/controller/

# 层次边界：Controller 中无 @Transactional
! git grep -n "@Transactional" -- backend/<service>/controller/

# 软删注解
grep '@SQLDelete\|@Where' backend/<service>/src/main/java/**/WrongItem.java

# 乐观锁注解
grep '@Version' backend/<service>/src/main/java/**/WrongItem.java

# 幂等原子性
grep 'setIfAbsent' backend/<service>/src/main/java/**/IdempotencyService.java

# 工具白名单
ops/scripts/check-allowlist.sh <phase>

# 覆盖率
mvn -pl <service> -q jacoco:report
# line-covered-ratio >= 0.70
```

**运行时验证**（来自 Scene 8-9）：

```bash
# 降级路径：RocketMQ 断开后 wrong_item_outbox 有记录
docker stop rmq-broker
POST /wrongbook/items → 201
psql -c "SELECT count(*) FROM wrong_item_outbox WHERE status='PENDING'"
# 期望 >= 1

# Redis 降级：Redis 断开后主流程不崩
docker stop redis
POST /wrongbook/items → 201（不是 500）
```

### 验收报告格式

```markdown
# Phase S3 后端验收报告

生成时间：2026-04-27T15:30:00Z | 服务：wrongbook-service

## 维度 A · 接口契约
| 端点 | 状态码 | 字段 | 枚举 | 结果 |
|---|---|---|---|---|
| POST /wrongbook/items     | 201 ✅ | ✅ | status=pending ✅    | ✅ |
| GET /wrongbook/items      | 200 ✅ | ✅ | has_more:boolean ✅  | ✅ |
| GET /wrongbook/items/{id} | 200 ✅ | ✅ | mastery=0 ✅         | ✅ |
| PATCH .../tags            | 200 ✅ | ✅ | —                    | ✅ |
| DELETE .../items/{id}     | 204 ✅ | —  | —                    | ✅ |
| GET /wrongbook/tags       | 200 ✅ | ✅ | —                    | ✅ |

## 维度 B · 业务行为
| Scene | 描述 | 关键断言 | 结果 |
|---|---|---|---|
| 1 | 录入主流程 | status=pending, RocketMQ/outbox 有记录 | ✅ |
| 2 | 列表加载   | has_more:boolean                       | ✅ |
| 3 | 详情查看   | 所有字段含 mastery/version              | ✅ |
| 4 | 标签替换   | DB wrong_item_tag 恰好 N 行（替换非追加）| ✅ |
| 5 | 软删       | GET=404, audit_log 有记录               | ✅ |
| 6 | 幂等       | 两次同 rid → 同 id，DB count=1          | ✅ |
| 7 | 乐观锁     | 并发一 200 一 409                        | ✅ |
| 8 | 非法状态转换 | → 400 INVALID_STATE_TRANSITION         | ✅ |
| 9 | Redis 降级 | Redis 停后仍 201                        | ✅ |

## 维度 C · 架构合规
| 检查项 | 结果 |
|---|---|
| 零 MyBatis                  | ✅ 0 命中 |
| Controller 无 repository    | ✅ 0 命中 |
| Controller 无 @Transactional | ✅ 0 命中 |
| @SQLDelete + @Where 存在    | ✅ |
| @Version 存在               | ✅ |
| setIfAbsent 原子幂等        | ✅ |
| 工具白名单                  | ✅ pass |
| 行覆盖率                    | 73% ≥ 70% ✅ |
| RocketMQ 断开降级           | ✅ outbox 有记录 |

## 结论
**通过** — 可执行 `git tag s3-done && git push --tags`，进入 S4。
```

---

## 各 Phase 适配说明

### S3 · wrongbook-service
```
前端 client：api-contracts/clients/wrongbook.ts
架构文档：design/arch/s3-wrongbook.md
已知 GAP：4 个（见"问题定性"章节）
业务场景数：9 个（见 Stage 3）
端点数预期：10
```

### S4 · ai-analysis-service
```
前端 client：api-contracts/clients/analysis.ts
架构文档：design/arch/s4-ai-analysis.md
关键业务规则：
  - 向量相似度搜索（pgvector cosine distance，k 参数 1-10 范围）
  - SSE 流式响应（chunk 格式 {chunk:string, done?:boolean}）
  - AI 调用失败降级（返回 fallback 解析，不抛 500）
特殊架构约束：
  - Spring AI over LangChain4j（ADR-0008）
  - 通义千问 API Key 从 Nacos 读取，不硬编码
验收特殊处理：SSE 维度 A 需验证 Content-Type: text/event-stream
端点数预期：3
```

### S5 · review-plan-service
```
前端 client：api-contracts/clients/review.ts（待建，从 types.ts ReviewPlanVO 反推）
架构文档：design/arch/s5-review-plan.md
关键业务规则：
  - SM-2 算法实现（ease_factor / interval / node_index 更新公式）
  - 每日复习计划生成（next_due_at 计算）
  - mastery 回写 WrongItem（跨服务调用 wrongbook-service）
注意：be-preflight 需要先从 ReviewPlanVO 字段反推 client 接口，再做对齐
```

### S6 · file-service
```
前端 client：api-contracts/clients/files.ts
架构文档：design/arch/s6-file-service.md
关键业务规则：
  - 预签名 URL 生成（TTL 15min，包含 sha256 校验）
  - 文件完成回调（病毒扫描通过 → READY，失败 → QUARANTINED）
  - 直传不过网关（upload_url 是 OSS 直传地址）
验收特殊处理：presign 返回的 upload_url 格式校验（必须是合法 URL，不是空字符串）
端点数预期：2
```

### S11 · anonymous-service
```
前端 client：api-contracts/clients/anonymous.ts（待建）
架构文档：design/arch/s11-anonymous.md
特殊处理：S11 全栈 Phase，be-preflight 需从 S7 前端代码逆向提取 anonymous 相关调用
```

---

## 与现有 Phase 流程的集成

三段式 Skill 插入 Phase 的 §X.5 DoR 和 §X.9 DoD 之间：

```
§X.5 DoR 检查（现有）
     ↓
/be-preflight <phase>              ← 新增：三类约束提取 + 对齐
     ↓
User 确认 contract-gaps.md        ← 新增：人工决策冲突点
     ↓
/be-builder <phase> layer1         ← 替换 §X.7 Step 3-14（分层执行）
/be-builder <phase> layer2
/be-builder <phase> layer3
/be-builder <phase> layer4
/be-builder <phase> layer5
     ↓
/be-accept <phase>                 ← 替换 §X.8 验证步骤（三维自动验收）
     ↓
§X.9 DoD 判定（现有）
```

§X.8 原有验证步骤（V-SX-01 ~ V-SX-20）转为 be-accept 的输入 checklist，由 Skill 自动执行，不需要手动跑。

---

## Skill 命令速查

| 命令 | 阶段 | Context 来源 | 产出 |
|---|---|---|---|
| `/be-preflight <phase>` | 约束提取与对齐 | Phase 规范全文 + api-contracts + business-analysis.yml + design/arch/<phase>.md | `be-build-spec.json` + `business-rule-cards.json` + `arch-constraints.json` + `contract-gaps.md`（写入 design/analysis/） |
| `/be-builder <phase> layer1` | Entity 层构建 | 从磁盘读 arch-constraints 的 layer1 片段 + git 当前状态 | JPA Entity 类 + Flyway SQL + compile gate ✅ |
| `/be-builder <phase> layer2` | Repo 层构建 | 从磁盘读 arch-constraints 的 layer2 片段 + git 当前状态 | Repository + QueryDSL Predicates + compile gate ✅ |
| `/be-builder <phase> layer3` | Service 层构建 | 从磁盘读 business-rule-cards（按批）+ arch-constraints 的 layer3 片段 | Service + Idempotency + Producer + compile gate ✅（支持 Sub-layer 自动拆分） |
| `/be-builder <phase> layer4` | Controller 层构建 | 从磁盘读 be-build-spec endpoints + arch-constraints layer4 片段 | Controller + DTO + MapStruct + IT + verify gate ✅ |
| `/be-builder <phase> layer5` | OpenAPI 导出 | 从磁盘读 be-build-spec validation 字段 | wrongbook.yaml 入库 + paths 数量门禁 ✅ |
| `/be-accept <phase>` | 三维验收 | 从磁盘读三份施工图（全量）+ 运行中服务 | `reports/phase-<phase>-be-acceptance.md` |

> **关键**：be-builder 每层是独立 Sub-agent，从磁盘读文件，不共享对话历史。

---

## 与前端三段式对比

| 维度 | 前端三段式 | 后端三段式（v1.1） |
|---|---|---|
| **真值来源** | Mockup HTML（视觉） | `api-contracts`（接口契约）+ `business-analysis.yml`（业务）+ `design/arch`（架构） |
| **施工图数量** | 1 个（build-spec.json） | 3 个（be-build-spec + business-rule-cards + arch-constraints） |
| **Builder 粒度** | 区块（NavBar / CardItem…） | 层（entity / repo / service / controller） |
| **精准注入** | 每区块只注入该区块 spec | 每层只注入本层所需的施工图摘录 |
| **门禁** | `grep` 零硬编码色值 | `mvn compile/verify` exit 0 + 架构合规 grep |
| **Acceptance 维度** | 视觉保真 + 设计系统合规 + AC 完整性 | 接口契约 + 业务行为 + 架构合规 |
| **User 决策点** | token-mapping-review.md（近似映射） | contract-gaps.md（接口冲突 + 业务歧义） |
| **停机条件** | 无（token 近似自动处理） | has_blocking_gaps=true 停机 / 门禁 3 次失败停机 |
| **Context 管理** | 每区块独立 prompt，build-spec.json 是唯一传递媒介 | 每层独立 Sub-agent，3 个 JSON 文件是唯一传递媒介，不共享对话历史 |
| **Context 膨胀防护** | 区块粒度小，天然不膨胀 | 分层过滤读取 + Sub-layer 拆分（Layer 3 > 5 张时）+ 独立 Agent 隔离 |
