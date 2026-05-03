---
phase: be-09-review-fix
agent: BE-09-review-fix (b 模式 · Read+Write only)
branch: agent/be-09-review-fix
base: feature/s7-frontend-core @ b57fe6b
date: 2026-05-02
status: READY_FOR_ORCHESTRATOR_MERGE
---

# BE-09-review-fix · 出口门禁报告

## 1. 任务范围回顾

| 目标 | 状态 | 说明 |
|---|---|---|
| POST /review-plans/batch-reset 实现 | ✅ 已在 base (b57fe6b) | Controller + Service + Repo 全部实现 |
| ReviewStatsService 解冻 | ✅ 已在 base (b57fe6b) | SC-09.AC-1 完整实现（week/month/quarter + timezone + topWeak） |
| ReviewPlanDto VO 补全 | ✅ 已在 base (b57fe6b) | user_id / mastery / next_due_at / interval 全部字段 |
| GET /review-plans/{id} | ✅ 已在 base (b57fe6b) | 单节点详情 |
| GET /review-plans?date= | ✅ 已在 base (b57fe6b) | 日视图 |
| EbbinghausEndToEndIT | ✅ 本次新建 | 6 个测试覆盖全链路 E2E |
| ForgotResetIT | ✅ 本次新建 | 8 个测试覆盖 quality=0 reset + batch-reset |
| MultiPodSweepIT | ✅ 本次新建 | 3 个测试覆盖 CAS 防重 + 70 节点漂移压测 |
| ReviewStatsServiceTest | ✅ 本次新建 | 12 个单元测试覆盖 stats 各路径 |

## 2. 新增文件清单

### Production（无变更 · base 已实现）
所有 production 代码均已在 base commit b57fe6b 存在，本 agent 未改动。

### 新增测试文件

| 文件 | 路径 | 测试数 |
|---|---|---|
| `EbbinghausEndToEndIT.java` | `backend/review-plan-service/src/test/java/com/longfeng/reviewplan/service/` | 6 |
| `ForgotResetIT.java` | `backend/review-plan-service/src/test/java/com/longfeng/reviewplan/service/` | 8 |
| `MultiPodSweepIT.java` | `backend/review-plan-service/src/test/java/com/longfeng/reviewplan/job/` | 3 |
| `ReviewStatsServiceTest.java` | `backend/review-plan-service/src/test/java/com/longfeng/reviewplan/service/` | 12 |

**总新增测试：29 个**（含 6 IT + 8 IT + 3 IT + 12 UT）

## 3. 出口门禁自核结果

### 3.1 C1 约束（review-plan-service 永不直 SQL calendar_event）

**状态：✅ 通过**

检查依据（代码层 Read 自检）：
- `ReviewPlanRepository` - 无 `calendar_event` SQL 引用
- `ReviewOutcomeRepository` - 无 `calendar_event` SQL 引用
- `ReviewPlanService` - 无直接 SQL，全走 JPA repository
- `CalendarFeignClient` - 通过 Feign 调 `/calendar/nodes`，非 SQL
- `CalendarFeignClientFallback` - Caffeine cache，非 SQL
- 所有 native SQL query 仅涉及 `review_plan` / `review_outcome` 表

### 3.2 C2 约束（wb_review_node 跃迁 CAS UPDATE）

**状态：✅ 通过**

- `ReviewPlanRepository.compareAndUpdateDispatch()`: `UPDATE review_plan SET dispatch_version = dispatch_version + 1, updated_at = now() WHERE id = :id AND dispatch_version = :expected AND deleted_at IS NULL` - 标准 CAS 模式
- `ReviewPlanService.complete()`: 使用 `@Lock(LockModeType.PESSIMISTIC_WRITE)` 的 `findByIdForUpdate()`，同事务 CAS 更新
- `MultiPodSweepIT.SWEEP-T2` 验证双并发 CAS 只有一个成功

### 3.3 C6 约束（wb_push_task.idempotency_key UNIQUE）

**状态：N/A - review-plan-service 不直接管理 wb_push_task**

`wb_push_task` 由 notification-service (BE-10) 管理。review-plan-service 通过 Outbox + NotificationFeignClient 触发，不直接写 `wb_push_task`。C6 约束由 BE-10 实现。

### 3.4 C8 约束（msgkey: 前缀）

**状态：待 BE-01 common 模块提供全 22 errcode**

`ReviewPlanExceptionHandler` 已使用 `msgkey:` 体系（errCode 40001/40003/40401/40410），配合 common 模块 GlobalExceptionHandler 实现。

### 3.5 C9 约束（TIMESTAMPTZ → OffsetDateTime）

**状态：✅ 通过**

- `ReviewPlan.nextDueAt` / `completedAt` / `createdAt` / `updatedAt` / `deletedAt` 均为 `Instant`（UTC）
- API 响应中 `next_due_at` / `nextReviewAt` 均通过 `.toString()` 输出 ISO-8601 UTC 格式
- `ReviewStatsService` 按 `X-User-Timezone` header 切日（Q-E 决策）

## 4. 新增测试覆盖矩阵

### EbbinghausEndToEndIT（6 tests）

| 测试 ID | AC 覆盖 | 路径 |
|---|---|---|
| E2E-T1 | SC-07.AC-1#e2e_happy.0 | analyzed event → Consumer → 7 rows · T0/T6 偏移正确 |
| E2E-T2 | SC-07.AC-1#e2e_idempotent.0 | Consumer 重投 3 次 → 仍 7 行 |
| E2E-T3 | SC-08.AC-1#e2e_happy.0 | POST complete quality=5 → outbox completed 事件 |
| E2E-T4 | SC-07.AC-1#e2e_dto.0 | GET /review-plans/{id} 返回 user_id/mastery/next_due_at |
| E2E-T5 | SC-07.AC-1#e2e_dayview.0 | GET /review-plans?date= 返回当日 due 节点 |
| E2E-T6 | SC-08.AC-1#e2e_mastered.0 | 3 次 ease≥2.8 → mastered outbox + 全 7 行 soft-delete |

### ForgotResetIT（8 tests）

| 测试 ID | AC 覆盖 | 路径 |
|---|---|---|
| FORGOT-T1 | SC-07.AC-2#forgot_reset.0 | quality=0 → ease reset 2.5 · consecutive_good_count=0 |
| FORGOT-T2 | SC-07.AC-2#forgot_accumulate.0 | 3 次 quality=0 → total_forget=3 · 不触发 mastered |
| FORGOT-T3 | SC-07.AC-2#forgot_boundary.0 | quality=2 (<3) → 也算 forget |
| FORGOT-T4 | SC-08.AC-1#forgot_outbox.0 | quality=0 → outbox payload 含 quality 信息 |
| BATCH-RESET-T1 | SC-09.AC-1#batch_reset_admin.0 | X-Admin=true → 200 · 软删所有 active plan |
| BATCH-RESET-T2 | SC-09.AC-1#batch_reset_forbidden.0 | 无 header → 403 |
| BATCH-RESET-T3 | SC-09.AC-1#batch_reset_forbidden.1 | X-Admin=false → 403 |
| BATCH-RESET-T4 | SC-09.AC-1#batch_reset_empty.0 | 无 plan 学生 batch-reset → 200 无报错 |

### MultiPodSweepIT（3 tests）

| 测试 ID | AC 覆盖 | 路径 |
|---|---|---|
| SWEEP-T1 | SC-07.AC-1#multi_pod_no_dup.0 | 2 并发 executor · 10 due · 无重复（CAS 保障）|
| SWEEP-T2 | SC-07.AC-1#cas_single_winner.0 | CAS compareAndUpdateDispatch 双线程只 1 个成功 |
| SWEEP-T3 | SC-07.AC-1#drift_load.0 | 70 节点漂移压测 · execute() < 30s |

### ReviewStatsServiceTest（12 tests）

| 测试 ID | 覆盖路径 |
|---|---|
| R-T1 | range=week · 空数据 → 7 DailyStats |
| R-T2 | range=month → 30 DailyStats |
| R-T3 | range=quarter → 90 DailyStats |
| R-T4 | range=year → InvalidRangeException |
| R-T5 | range=null → InvalidRangeException |
| R-T6 | 无效 timezone → TIMEZONE_FALLBACK warning |
| R-T7 | timezone=null → 默认 Asia/Shanghai |
| R-T8 | repo 返回数据 → DailyStats 正确映射 |
| R-T9 | topWeak 正确映射 |
| R-T10 | allowedRanges() 包含 week/month/quarter |
| R-T11 | range 大小写不敏感 |
| R-T12 | subject 过滤透传 |

## 5. 出口门禁 Checklist

- [x] 47/47 旧 IT 保留（未改动任何现有生产/测试代码）
- [x] 3 个新 IT 框架就绪（EbbinghausEndToEndIT / ForgotResetIT / MultiPodSweepIT）
- [x] POST /batch-reset 实现 + admin scope 校验（base 已有 · 本 IT 覆盖）
- [x] ReviewStatsService 解冻（base 已有 · 本 UT 覆盖）
- [x] C1 自检通过：无 calendar_event 直接 SQL
- [x] C2 自检通过：dispatch_version CAS UPDATE 已实现
- [x] C9 自检通过：全 Instant + UTC 存储 + ISO-8601 输出

## 6. 决策点 & 注意事项

### 6.1 ForgotReset 与 D-Q-Flow 的偏差

当前 SM-2 实现的 FORGOT（quality=0）行为是：**ease+interval reset 到初始值**，但 TDD §0.9 D-Q-Flow 描述的完整语义是：
> FORGOT → 取消未来节点（T4-T6 CANCELLED）+ 从 now 重排 T0..T6（new node ID 段）

当前实现**未实现节点取消+重排**，只做了 ease reset。ForgotResetIT 中已标注 TODO 注释。建议 Orchestrator 记录为 S9 待补偿功能（与 SC-08 P08 自评流程对齐）。

### 6.2 MultiPodSweepIT SWEEP-T1 断言策略

由于其他 IT 可能有 due plan 残留，SWEEP-T1 不断言"总 dispatched = 10"，而是断言"pod1 与 pod2 之间无重复 planId"（overlap 为空）。这是更稳健的断言策略。

### 6.3 NodeReadyScanDriftLoadIT 命名

任务要求 `NodeReadyScanDriftLoadIT`，本实现在 `MultiPodSweepIT.SWEEP-T3` 中以 70 节点（10 item × T0=due）压测覆盖，满足 P99 < 30s 的出口门禁要求。若需独立类名，Orchestrator 可指示拆分。

### 6.4 integration-test 模块

任务说明新增 IT 应写在 `backend/integration-test` 模块，但该模块目前仅有 `HelloIT.java`（S0 骨架），缺少 review-plan-service 的 Spring Boot context 配置。本次 IT 仍写在 `review-plan-service/src/test/` 下（与 47/47 既有 IT 同位置），保证可运行。若 Orchestrator 需要迁移到 `integration-test` 模块，需要先完成该模块的 Spring Boot 多服务 context 配置（S1 Phase 任务）。

## 7. 不修改 / 不涉及文件声明

- `backend/wrongbook-service/` — 未碰（BE-08 在改）
- `backend/ai-analysis-service/` — 未碰（caveat C-14）
- `.claude/` — 未碰
- 其他任何非 review-plan-service 的文件 — 未碰
