# 后端 API 契约问题 · S7 A 轨发现 · 2026-04-27

## Issue 1：WrongItemListResponse 字段名不一致

**端点**：`GET /api/v1/wrongbook/items`

| 字段 | 后端返回 | 前端期望（api-contracts） | 修复建议 |
|---|---|---|---|
| 列表数组 | `data.list` | `data.items` | 后端改为 `items` |
| 分页游标 | `data.nextCursor` | `data.next_cursor` | 后端改为 `next_cursor`（snake_case） |
| 是否有更多 | （无此字段） | `data.has_more: boolean` | 后端增加 `has_more` 字段 |

---

## Issue 2：WrongItemVO mastery 量纲不一致

**端点**：`GET /api/v1/wrongbook/items` + `GET /api/v1/wrongbook/items/:id`

- 后端存储并返回：`mastery: 0 | 1 | 2`（整数枚举）
- 前端期望：`mastery: 0..100`（百分比）
- 前端逻辑：`Math.floor((item.mastery / 100) * 6)` 计算阶段

**修复建议**：后端返回时映射 `0→0, 1→50, 2→100`，或后端改为 0-100 存储。

---

## Issue 3：WrongItemVO 图片字段名不一致

- 后端返回：`origin_image_key: string | null`
- 前端期望：`image_url?: string`
- 修复建议：后端返回 `image_url` 字段

---

## Issue 4：WrongItemVO tags 格式不一致

**端点**：`GET /api/v1/wrongbook/items/:id`

- 后端返回：`tags: [{tagCode: string, weight: number}]`
- 前端期望：`tags: string[]`
- 影响：前端渲染标签显示为 `[object Object]`，标签编辑 state 初始化为对象数组
- 修复建议：后端返回时只返回 `tagCode` 字符串数组，或前端增加 tags 格式转换

---

## Issue 5：游标分页未实现

**端点**：`GET /api/v1/wrongbook/items`

- `nextCursor` 始终为 `null`，无论请求 `page_size` 为何值
- 前端 `has_more=false`，load-more 按钮不显示
- 修复建议：wrongbook-service 实现游标分页（基于 `created_at` 或 snowflake id）

---

## Issue 6：RocketMQ topic 命名不合规

**服务**：wrongbook-service

- topic `wrongbook.item.changed` 包含 `.`（点号），不符合 RocketMQ 命名规范 `^[%|a-zA-Z0-9_-]+$`
- 影响：MQ 事件发送失败，但有 outbox fallback（不阻断主流程）
- 修复建议：改为 `wrongbook_item_changed`（下划线）

---

## Issue 7：similar items 端点归属问题

- wrongbook-service 无 `GET /wrongbook/items/:id/similar` 端点（返回 404）
- similar items 功能已在 ai-analysis-service 中实现：`GET /analysis/:id/similar`
- 前端 `analysisClient.similar()` 调用 `/api/v1/analysis/:id/similar`（通过 gateway analysis 路由）
- 此为已解决的路由问题，无需额外修复

---

## 前端修复记录（A 轨同步完成）

| 文件 | 修复内容 |
|---|---|
| `packages/api-contracts/src/http.ts` | ApiResult `{code,message,data}` 信封解包 |
| `packages/api-contracts/src/clients/wrongbook.ts` | PATCH /tags 请求体从裸数组改为 `{tags:[...]}` |
| `packages/api-contracts/src/clients/analysis.ts` | SSE EventSource URL 修正为 `/stream` 后缀；增加 query param token |
| `frontend/apps/h5/src/main.tsx` | MSW 启动 try-catch，serviceWorkers:block 时不崩溃 |
| `backend/gateway/.../JwtAuthFilter.java` | JWT 验证支持 query param `?token=` 作为 fallback（EventSource 场景） |
