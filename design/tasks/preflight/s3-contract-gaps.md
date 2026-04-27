# Contract Gaps — s3

> 前端 api-contracts vs arch 文档差距分析。
> 前端真源：`frontend/packages/api-contracts/src/clients/wrongbook.ts` + `types.ts`
> 后端真源：`design/arch/s3-wrongbook.md §4.1`

---

## BLOCKER · 必须决议才能开工

### G-01 · Tag 管理 API 形状不匹配

| 维度 | 前端调用 | Arch 定义 |
|---|---|---|
| 端点 | `PATCH /wrongbook/items/{id}/tags` | `POST /wrongbook/items/{id}/tags`（加标签） + `DELETE /wrongbook/items/{id}/tags/{tagCode}`（去标签）|
| Body | `tags: string[]`（批量替换语义） | `{ tagCode, weight }`（单条操作） |
| 版本控制 | `If-Match: version`（header） | 无乐观锁 |
| 语义 | 全量 replace（传什么就是什么） | 逐条 add/remove |

**影响**：前端调用一个 PATCH 接口完成所有标签更新，若后端只有 POST/DELETE 个体接口，前端无法正常工作。

**建议方案**：
- (a) **推荐** · Arch 补充 `PATCH /wrongbook/items/{id}/tags` 端点，body = `{ tags: string[] }`，语义 = 全量替换，Service 层做 diff（删多余 + 加缺失），`If-Match` header 对应 `version` 乐观锁
- (b) 前端改用 POST/DELETE 个体接口（需修改 api-contracts，影响 S7 前端）

**来源**：`wrongbook.ts:27-32` vs `s3-wrongbook.md §4.1 /wrongbook/items/{id}/tags`

---

### G-02 · `GET /wrongbook/tags` 端点缺失

| 维度 | 前端调用 | Arch 定义 |
|---|---|---|
| 端点 | `GET /wrongbook/tags` | **未定义** |
| 响应 | `{ tags: string[] }` | — |
| 用途 | 获取所有可用标签列表（筛选/打标签用） | — |

**影响**：前端标签筛选器无法获取标签列表，S7 筛选功能不可用。

**建议方案**：
- (a) **推荐** · Arch 补充 `GET /wrongbook/tags` 端点，查询 `tag_taxonomy` 表中 `status=1`（active）的标签，按 `subject` 分组返回
- (b) 复用 `tag_taxonomy` 的 subject/bloomLevel 过滤，返回 `{ tags: string[], grouped?: { [subject]: string[] } }`

**来源**：`wrongbook.ts:31-33` vs `s3-wrongbook.md §4.1`（无此端点）

---

### G-03 · Status 类型与值不兼容

| 维度 | 前端类型 | Arch 定义 |
|---|---|---|
| 类型 | `WrongItemStatus = 'pending' \| 'analyzing' \| 'completed' \| 'error'` (string) | `SMALLINT 0/1/2/3/8/9` |
| 值数量 | 4 个值 | 6 个值 |
| `error` 状态 | 存在 | **不存在**（无 error 态） |
| 映射关系 | 不明确 | — |

**影响**：`WrongItemVO.status` 序列化时前后端完全不兼容；前端的 `error` 状态在后端无对应。

**建议方案**：
- (a) **推荐** · 后端 `WrongItemVO.status` 序列化时映射：0=`pending`, 1=`analyzing`, 2/3/8=`completed`, 9=`completed`；`error` 状态用独立字段（如 `analysis_error: boolean`）而非 status，或等 S4 Phase 定义 error 语义
- (b) 前端扩展 WrongItemStatus 类型覆盖全部 6 个后端态（需修改 api-contracts）
- (c) 后端新增 `status=4`（error）并同步前端类型（需 arch + DDL + 前端同步改）

**来源**：`types.ts:13` vs `s3-wrongbook.md §1.3 D3 + §2.1`

---

## WARNING · 建议决议但不阻断开工

### G-04 · `WrongItemCreate` 缺少 `student_id`

| 维度 | 前端发送 | Arch 定义 |
|---|---|---|
| Body | `{ subject, stem_text, tags?, image_id? }` | `CreateWrongItemReq: { studentId(required), subject, stemText, ... }` |
| studentId | **不在 body 中** | required 字段 |

**建议**：studentId 从 JWT token 中提取（Controller 从 SecurityContext 获取），无需前端传 body。Arch 文档 `CreateWrongItemReq` 中的 `studentId` 改为从 auth context 注入。

**来源**：`types.ts:22-27` vs `s3-wrongbook.md §4.1 CreateWrongItemReq`

---

### G-05 · 字段命名风格不一致（snake_case vs camelCase）

| 前端字段（snake_case） | Arch DTO 字段（camelCase） |
|---|---|
| `stem_text` | `stemText` |
| `created_at` | `createdAt` |
| `image_url` | `originImageKey`（语义也不同） |

**建议**：后端 Jackson 配置 `spring.jackson.property-naming-strategy=SNAKE_CASE`，或在 DTO 上加 `@JsonProperty("stem_text")`。`image_url` 需要 S6 预签名 URL 生成逻辑（获取 `object_key` 后调 S6 生成 URL）。

**来源**：`types.ts:17-26` vs `s3-wrongbook.md §4.1 components/schemas`

---

### G-06 · `mastery` 范围差异

| 维度 | 前端 | Arch |
|---|---|---|
| 类型 | `mastery: number // 0..100` | `mastery: SMALLINT 0..2` |

**建议**：确认 VO 层返回语义。选项：
- (a) 原值返回（0/1/2），前端 types.ts 注释更新为 `// 0..2`
- (b) 换算为 0-100（`mastery * 50`），保持前端注释
- (c) 等 S5 SM-2 定义 mastery 语义时统一决策

**来源**：`types.ts:20` vs `s3-wrongbook.md §2.1`

---

### G-07 · `image_url` 字段在 arch VO 中缺失

| 维度 | 前端期望 | Arch VO |
|---|---|---|
| 字段 | `image_url?: string`（预签名 URL） | `WrongItemVO: { type: object }`（schema 为空）|

**建议**：arch VO schema 需补全所有字段。`image_url` 由 `wrong_item_image.object_key` + S6 预签名生成，本 Phase 可返回 null/omit，S6 完成后补充。

**来源**：`types.ts:21` vs `s3-wrongbook.md §4.1 WrongItemVO`

---

### G-08 · `WrongItemListParams.status` 类型差异

| 维度 | 前端 | Arch |
|---|---|---|
| status 参数 | `status?: 'active' \| 'mastered'` | `status: integer` |

**建议**：Controller 接收 string query param，映射 `'active'→[0,1,2,3]`、`'mastered'→[8]`，再转换为 QueryDSL predicate。

**来源**：`types.ts:30-31` vs `s3-wrongbook.md §4.1 GET /wrongbook/items`

---

## 端点覆盖对照

| 端点 | arch 定义 | 前端调用 | 状态 |
|---|---|---|---|
| `POST /wrongbook/items` | ✅ | ✅ | OK |
| `GET /wrongbook/items` | ✅ | ✅ | WARNING G-08 |
| `GET /wrongbook/items/{id}` | ✅ | ✅ | WARNING G-07 |
| `PATCH /wrongbook/items/{id}` | ✅ | — | 前端未调用（S7 无直接编辑功能）|
| `DELETE /wrongbook/items/{id}` | ✅ | ✅ | OK |
| `PATCH /wrongbook/items/{id}/tags` | ❌ 未定义 | ✅ | **BLOCKER G-01** |
| `POST /wrongbook/items/{id}/tags` | ✅ | — | arch 定义但前端未用此形式 |
| `DELETE /wrongbook/items/{id}/tags/{tagCode}` | ✅ | — | arch 定义但前端未用此形式 |
| `GET /wrongbook/tags` | ❌ 未定义 | ✅ | **BLOCKER G-02** |
| `POST /wrongbook/items/{id}/images` | ✅ | — | 前端未直接调用（S6 回调触发）|
| `POST /wrongbook/items/{id}/difficulty` | ✅ | — | 前端未调用（S7 无难度设置 UI）|
| `POST /wrongbook/items/{id}/attempts` | ✅ | — | 前端未调用（S7 无作答 UI）|
| `GET /wrongbook/items/{id}/attempts` | ✅ | — | 前端未调用（S7 无历史作答 UI）|

---

## User 确认（2026-04-27 · 全部采用推荐方案）

**BLOCKER — 已决议：**
- [x] G-01 · 方案 (a)：arch 补 `PATCH /wrongbook/items/{id}/tags` bulk replace，`If-Match: version` header
- [x] G-02 · 方案 (a)：arch 补 `GET /wrongbook/tags`，查 tag_taxonomy active 标签
- [x] G-03 · 方案 (a)：VO 层映射 `0→pending, 1→analyzing, 2/3/8/9→completed`；`error` 态延至 S4 定义

**WARNING — 已决议：**
- [x] G-04 · studentId 从 JWT auth context 注入，`CreateWrongItemReq` 移除 studentId 字段
- [x] G-05 · snake_case 全局 Jackson 配置（`spring.jackson.property-naming-strategy=SNAKE_CASE`）
- [x] G-06 · mastery 返回原值 0..2（arch §2.1 INV-07 权威），前端 types.ts 注释为历史遗留
- [x] G-07 · image_url 本 Phase 返回 null，S6 完成后在 WrongItemService 补生成预签名 URL
- [x] G-08 · `GET /items?status=active/mastered`，Controller 层转换为 SMALLINT predicate

**arch 文档变更（design/arch/s3-wrongbook.md）：**
- §4.1 OpenAPI paths：`POST/DELETE /items/{id}/tags` → `PATCH /items/{id}/tags`；新增 `GET /wrongbook/tags`
- §4.1 components/schemas：`AddTagReq` 替换为 `BulkTagReq`；`WrongItemVO` schema 补全；`CreateWrongItemReq` 移除 studentId
- §8 符号清单：REST paths 更新，`AddTagReq` → `BulkTagReq`

**be-build-spec.json `has_blocking_gaps` → false ✅**
**Builder 可开工：运行 `/be-builder s3`**
