# Contract Gaps — s4 (ai-analysis-service)

> 以下是 **frontend api-contracts**（前端调用真源）与 **design/arch/s4-ai-analysis.md**（后端真源）之间的差距，需要 User 确认处理方式后 Builder 方可开工。
>
> 来源：
> - arch §4.1 OpenAPI 片段：design/arch/s4-ai-analysis.md L380-416
> - 前端 client：frontend/packages/api-contracts/src/clients/analysis.ts
> - 前端 types：frontend/packages/api-contracts/src/types.ts L57-73

## 决议状态总览（2026-04-27 · @allenthinking 拍板）

| Gap | 严重度 | 决议 | 落地动作 |
|---|---|---|---|
| G-01 | BLOCKER | **方案 (c) 双端点并存** | 保留 `GET /analysis/{itemId}` REST JSON · 新增 `GET /analysis/{itemId}/stream` SSE · 前端 `explainStream` 路径改为 `/api/v1/analysis/{itemId}/stream` |
| G-02 | BLOCKER | **方案 (a) 后端跟前端字段** | SimilarItem = `{ id: string, stem_text: string, subject: string, distance: number }` · arch §4.1 待同步修订 |
| G-03 | WARNING | 接受 | retry 是管理员接口 · 前端不调，仅后端实现 |
| G-04 | WARNING | 接受 | provider 是观测探针 · 前端不调，仅后端实现 |
| G-05 | WARNING | 落定 | AnalysisVO 字段表见下方推导（G-05 章节） |
| G-06 | WARNING | 不归后端 | 前端 owner 自行处理路径前缀风格不一致 |

✅ 全部 BLOCKER 已决议 · `s4-be-build-spec.json` 中 `has_blocking_gaps` 已更新为 `false` · Builder 可开工。

---

## BLOCKER · 必须 User 决议才能开工

### G-01 · `GET /analysis/{itemId}` 协议不匹配（REST JSON vs SSE）

| 维度 | arch 定义 | 前端实际调用 |
|---|---|---|
| 路径 | `/analysis/{itemId}` | `/api/v1/analysis/{itemId}`（前端写死含 `/api/v1/` 前缀） |
| 方法 | GET | GET（用 EventSource） |
| Content-Type | `application/json` | `text/event-stream`（SSE） |
| 响应 schema | `AnalysisVO`（type:object · 字段未细化） | 流式 `ExplainChunk { chunk: string, done?: boolean }` |
| 终止条件 | HTTP 200 即终止 | 末条 chunk 携带 `done:true` 后 `es.close()` |
| 来源 | arch L383-390 | analysis.ts:11-27 + types.ts:70-73 |

**冲突核心**：arch 设计是一次性返回完整 AnalysisVO 的 REST 端点；前端实现的是流式 SSE 订阅，期望 LLM 边生成边推送 explain 文本块。两者 wire-level 不兼容。

**候选处理方式**（请 User 选择 a/b/c/d）：

- **(a) 实现为 SSE**（推荐 · 与前端一致 · 提升首字节延迟感受）
  - controller 用 `produces=MediaType.TEXT_EVENT_STREAM_VALUE` + `Flux<ExplainChunk>` 或 `SseEmitter`
  - AnalysisService 新增 `streamExplain(itemId)` 从已持久化的 wrong_item_analysis.error_reason 中分块推送（若 status=0）；尚未分析则触发即时分析并流式回传
  - 缺点：与 §3.1 主流程（consumer 异步分析 → DB → 前端拉取）的"先入库再读取"模型不一致 · 需明确：SSE 端点是仅读已存数据，还是触发即时分析
  - 子选项：
    - (a1) **仅读已存** · status=0 时分块返回 error_reason；status≠0 返回单条提示 chunk + done:true · 404 if 未分析
    - (a2) **触发即时分析** · 旁路 MQ 直接同步调 LLM 流式回传 · 风险：与 consumer 路径产生双写竞态

- **(b) 实现为 REST JSON**（与 arch 一致）
  - 前端需要改：去掉 EventSource，用普通 GET + JSON
  - 需 User 协调前端（types.ts 移除 ExplainChunk · analysis.ts 改 explainStream → getAnalysis）
  - 优点：与 §3.1 异步分析 + 前端拉取模型自然契合
  - 缺点：失去流式体验（首字节延迟 = 全部生成完成）

- **(c) 双端点并存**
  - 保留 `GET /analysis/{itemId}` 返回 AnalysisVO（REST JSON · 拉取已存结果）
  - 新增 `GET /analysis/{itemId}/stream` SSE（流式 explain · 已存即分块回放，未存即触发）
  - 前端 explainStream 路径改为 `/api/v1/analysis/{itemId}/stream`
  - 优点：语义清晰 · 兼顾两类需求；缺点：实现工作量增加

- **(d) User 另定**

**Builder 视角推荐**：(c) · 因为 §3.1 异步主流程不变（consumer + DB），SSE 仅做读取 explain 的"流式回放"或"未存即时分析"，不破坏现有数据流。

### G-01 决议：方案 (c) 双端点并存 ✅

**两个端点的职责切分**：

| 端点 | 协议 | 职责 | 行为 |
|---|---|---|---|
| `GET /analysis/{itemId}` | REST JSON · `application/json` | 取已分析的完整结果 | 200 + AnalysisVO（status=0/1）· 404 if 未分析或 status=9 待 retry |
| `GET /analysis/{itemId}/stream` | SSE · `text/event-stream` | 流式回放 explain 文本 | 已存 status=0：分块推送已存 error_reason · 末条 done:true · 未存或 status=9：返回单条提示 chunk + done:true（不在该端点触发即时分析） |

**SSE 取舍说明**：

- **为什么需要 SSE**：LLM 输出讲解文本是逐 token 流式生成的，给用户的最佳体验是"边等边看"（首字节 < 1s · 类 ChatGPT 打字机效果），而不是"5-10s 后突现一大段"。
- **为什么不在 SSE 端点直接触发即时分析**：原 §3.1 主流程是 consumer 异步分析 → 入库 → 前端拉取 · 双写入路径会与 consumer 路径竞态（同一 `(item_id, version)` 重复 INSERT）。所以 SSE 仅做**已存数据的流式回放**，不旁路 MQ。
- **为什么前端体感仍流畅**：上游 wrongbook.item.changed 事件触发后，consumer 在秒级完成分析（P95 ≤ 15s）。前端进入详情页时 99% 概率分析已完成，SSE 直接从 `error_reason` 分块回放即可。极少数未完成的场景，前端可短轮询 `GET /analysis/{itemId}` 直到 200 后再开 SSE。
- **后端实现方式**：`@GetMapping(produces=TEXT_EVENT_STREAM_VALUE)` + `SseEmitter` 或 `Flux<ExplainChunk>` · 不强制引入 WebFlux 全栈 · 分块策略：按句号 / 80 字符为单位切（实现细节由 Builder 拍板）。
- **前端待跟进**：`analysis.ts:12` 的 `EventSource('/api/v1/analysis/${itemId}')` 需改为 `EventSource('/api/v1/analysis/${itemId}/stream')`。这是前端 owner 任务，不阻塞后端 Builder 开工。

**arch 同步修订（提 PR 改 `design/arch/s4-ai-analysis.md` §4.1）**：
- 保留 `GET /analysis/{itemId}` 的 REST JSON 定义
- 新增 `GET /analysis/{itemId}/stream` SSE 端点定义
- §8 符号清单 REST paths 加上 `GET /analysis/{itemId}/stream`

---

### G-02 · `SimilarItem` 字段不兼容

| 维度 | arch 定义（§4.1 L415） | 前端期望（types.ts:58-63） |
|---|---|---|
| 主键字段名 | `itemId` | `id` |
| 主键类型 | `integer` | `string` |
| `stem_text` | （缺）| `string`（必填） |
| `subject` | （缺）| `string`（必填） |
| `distance` | `number`（一致） | `number`（一致） |

**冲突核心**：arch 仅定义 `{itemId, distance}` 极简形态；前端需要带题干和学科直接渲染（避免 N+1 查询）。同时主键命名 + 类型双重差异。

**候选处理方式**：

- **(a) 后端跟随前端**（推荐 · 遵循 S3 的 snake_case + string ID 风格 · 减少前端改动）
  - 后端 SimilarItem VO 字段：`{ id: string, stem_text: string, subject: string, distance: number }`
  - id 序列化为字符串（沿用 S3 WrongItemVO.id 的 Snowflake-Long → string 习惯）
  - similar 查询时 join wrong_item 取 stem_text + subject
  - 更新 arch §4.1 SimilarItem 定义（提交 PR 同步修订）

- **(b) 前端跟随后端**
  - 前端改 types.ts：SimilarItem = `{ itemId: number, distance: number }`
  - 前端列表项需要的 stem_text/subject 由调用方再次拉 wrong_item 详情
  - 缺点：N+1 查询体验差

- **(c) User 另定**

**Builder 视角推荐**：(a) · 与 S3 风格一致，避免前端 N+1。

### G-02 决议：方案 (a) 后端跟前端字段 ✅

**最终 SimilarItem schema**：
```yaml
SimilarItem:
  type: object
  required: [id, stem_text, subject, distance]
  properties:
    id:        { type: string, description: "Snowflake-Long 序列化为 string · 与 S3 WrongItemVO.id 风格一致" }
    stem_text: { type: string, description: "题干文本（非 PII 脱敏前的原文 · 因相似题查询结果回前端展示）" }
    subject:   { type: string, description: "学科 · math/physics/chemistry/english/chinese" }
    distance:  { type: number, description: "pgvector 余弦距离 · 1 - cosine_similarity · 范围 [0, 2] · 仅返回 ≤ 1.5 的项" }
```

**Builder 落地动作**：
- `findSimilar(itemId, k)` SQL 用 native query：`SELECT id, stem_text, subject, 1 - (embedding <=> ?::vector) AS distance FROM wrong_item WHERE embedding IS NOT NULL AND id <> ? AND deleted_at IS NULL ORDER BY embedding <=> ?::vector LIMIT ?`
- VO 序列化时 `id: Long` → `String.valueOf(id)`
- distance > 1.5 在 Service 层 `.filter()` 掉 · 不在 SQL 层（避免索引失效）
- arch §4.1 SimilarItem 定义同步修订（提 PR）

---

## WARNING · 建议确认但不阻断 Builder

### G-03 · `POST /analysis/{itemId}/retry` 前端无对应 client

- arch L391-397 定义该端点，前端 api-contracts 中无调用（analysis.ts 仅有 similar + explainStream）
- **建议处理**：管理员接口 · 由独立 admin 工具或 Postman 调用 · 不阻塞 · Builder 仍按 arch 实现

### G-04 · `GET /analysis/provider` 前端无对应 client

- arch L398-402 定义观测端点，前端无调用
- **建议处理**：保留为运维探针 · OpenAPI 中保留 · Builder 实现 controller + service 即可

### G-05 · `AnalysisVO` 字段未在 arch 内细化

- arch §4.1 L414 仅 `AnalysisVO: { type: object }` 占位
- 若 G-01 选 (b) 或 (c)，AnalysisVO 字段必须细化
- **建议字段**（基于 §2.1 类图 + S1 DDL 推导）：
  ```yaml
  AnalysisVO:
    id: string             # Snowflake → string
    wrong_item_id: string  # → string
    version: integer       # INT
    model_provider: string # 'dashscope' | 'openai' | 'stub'
    model_name: string
    status: string         # 'success' | 'fallback' | 'pending' (前端友好枚举)
    explain: string        # 来自 error_reason 字段（漂移 DD 决议）
    cause_tag: string      # 'CONCEPT' | 'CALCULATION' | 'COMPREHENSION' | 'HANDWRITING' | 'OTHER'
    auto_tags: string[]    # 来自 knowledge_points
    solution_steps: object # JSONB 透传
    finished_at: string    # ISO 8601
  ```
- **建议处理**：在解决 G-01 时一并写定，或 Builder 按上方推导实现并在 PR description 列出待 review

### G-06 · 前端路径前缀风格不一致

- `similar()` 用 `/analysis/${itemId}/similar`（依赖 httpClient baseURL 注入 `/api/v1/`）
- `explainStream` 用 `EventSource('/api/v1/analysis/${itemId}')`（写死 `/api/v1/`）
- **建议处理**：前端层面统一（不属后端职责）· Builder 后端路径仍按 arch `/analysis/...` 实现 · gateway 统一加 `/api/v1/` 前缀

---

## User 确认清单

- [x] **G-01**（SSE vs REST）选定方案：**(c) 双端点并存** · 详见 G-01 决议章节
- [x] **G-02**（SimilarItem 字段）选定方案：**(a) 后端跟前端** · 详见 G-02 决议章节
- [x] G-03 / G-04 接受现状（管理/观测端点 · 仅后端实现，前端不调）
- [x] G-05 AnalysisVO 字段表已落定（见下方推导）
- [x] G-06 前端路径前缀不一致由前端 owner 自行处理，不影响后端
- [x] `s4-be-build-spec.json` 中 `has_blocking_gaps` 已更新为 `false`
- [x] Builder 可开工：`/be-builder s4`

---

## 备注

- S4 后端骨架（`com.longfeng.aianalysis.{entity, repo, service, llm, controller, ...}`）已存在 · 见 `backend/ai-analysis-service/src/main/java/com/longfeng/aianalysis/`
- §8 符号清单的所有类已建有空文件 · Builder 任务为按 spec 填充实现
- 上游 S3 的 `wrongbook.item.changed` topic 由 wrongbook-service 发布；S4 仅订阅
- 上游 S1 DDL 已包含 `wrong_item_analysis` 表（V1.0.012）和 `wrong_item.embedding` 列；S4 仅新增 `ai_usage_log`（V1.0.023）
