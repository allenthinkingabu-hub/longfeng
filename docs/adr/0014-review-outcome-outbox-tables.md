# ADR 0014 · S5 补 review_outcome + review_plan_outbox 迁移（S1 DDL 漂移）

- **Status**: Accepted · 2026-04-23
- **Phase**: S5 · review-plan-service
- **Raised-by**: G-Arch 阶段识别 S1 DDL 漂移（`review_outcome` / `review_plan_outbox` 表未建）
- **Related**: ADR 0005（RocketMQ 事务消息 Outbox）· S1 Retrofit complete 报告

## Context

S5 Phase G-Arch 阶段整理 §3.4 DB Schema 时发现：

- S1 Phase（v1.8 Retrofit 已 compliant）实际建表清单：
  - `review_plan`（V1.0.016）✓
  - `review_event`（V1.0.018）✓
  - **`review_outcome`** — **未建** · S5 `complete` 事务需要（审计 · 记 quality + easeBefore/After）
  - **`review_plan_outbox`** — **未建** · S5 走 Outbox 兜底（ADR 0005 · review.completed/mastered 事件可靠投递）
  - `review_plan` 的唯一索引 `(wrong_item_id, node_index) WHERE deleted_at IS NULL` — **未建**（本 ADR 补）

S5 的 SC-07.AC-1（Consumer 幂等 INSERT 7 行）强依赖此唯一索引兜底。若不补 · MQ 重投时 `ON CONFLICT DO NOTHING` 无法生效 · 违反 I-1 不变量。

## Decision

S5 Phase 在 `backend/common/src/main/resources/db/migration/` 新增 3 个迁移：

- `V1.0.053__review_outcome.sql` — 建 `review_outcome` 表 · 字段 `id/plan_id/quality/completed_at/ease_factor_before/ease_factor_after/interval_days_before/interval_days_after`
- `V1.0.054__review_plan_outbox.sql` — 建 `review_plan_outbox` 表 · 字段 `id/plan_id/event_type/payload_jsonb/created_at/dispatched_at/status` · Outbox Relay 扫表发 MQ
- `V1.0.055__review_plan_mastered_index.sql` — 建 partial unique index `CREATE UNIQUE INDEX uk_review_plan_item_node ON review_plan(wrong_item_id, node_index) WHERE deleted_at IS NULL`

## Rationale

- **走 S5 新增 migration 而非 Hotfix 回 S1**：S1 已打 `s1-v1.8-compliant` tag · Retrofit complete 报告冻结。改 S1 会破坏 tag 合规语义
- **Flyway 版本号递增**：V1.0.053-055 接 S1 V1.0.052（idem_key）· 按 `§5.1 A1` Flyway 版本化约定
- **review_outcome 单独表**（不复用 review_event）：review_event 语义是"事件轨迹"（due/completed/mastered 时间点）· review_outcome 语义是"复习结果审计"（quality + ease diff）· 职责分离
- **Outbox 走 jsonb 字段**：payload 灵活 · 不用为每种事件类型建独立表

## Alternatives Rejected

| 方案 | 拒绝理由 |
|---|---|
| 走 Hotfix 回 S1 补 review_outcome/outbox 到 V1.0.018 系列 | S1 v1.8 Retrofit 已打 tag · 改 S1 违反 §26.3 不阻塞原则 |
| 复用 review_event 存 outcome 数据 | 语义混乱 · event 表记轨迹 · outcome 记审计 · 职责不同 |
| 不用 Outbox · 裸发 MQ | 丢消息风险 · 违反 ADR 0005 |

## Consequences

- **正面**：S5 事务完整性保障 · `complete` 同事务写 plan + outcome + outbox · Relay 异步重发 MQ
- **风险**：
  - `review_plan_outbox` 需 Relay 组件扫表（S10 补 · 本 Phase 内可先用定时 task 起住）
  - `UNIQUE INDEX ... WHERE deleted_at IS NULL` PostgreSQL 特定语法 · 迁移到其他 DB 需重写（本项目锁 PG16 · 无迁移顾虑）
- **DB 存储**：review_outcome ~900 万行/年（同 review_event）· outbox ~35 万行/7d

## References

- 落地实施计划 §9.2（S5 架构设计 · 外部依赖）
- `design/arch/s5-review-plan.md` §3.4 DB Schema
- ADR 0005（RocketMQ 事务消息 Outbox）
- S1 Retrofit complete 报告 `reports/retrofit/s1-complete.md`
