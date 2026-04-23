# ADR 0013 · 采用 SM-2 而非纯 Ebbinghaus 固定间隔

- **Status**: Accepted · 2026-04-23
- **Phase**: S5 · review-plan-service
- **Raised-by**: G-Biz Q-B/C/F 决策（AskUserQuestion 轮 1/2）
- **Related**: ADR 0006（JPA）· ADR 0005（RocketMQ 事务消息）

## Context

S5 复习调度服务需选择复习节奏算法。候选：
- **纯 Ebbinghaus 固定曲线** · 偏移数组 `[2h, 1d, 2d, 4d, 7d, 14d, 30d]` 写死 · 不根据用户表现自适应
- **纯 SM-2（SuperMemo 2）动态算法** · ease_factor 随 quality 反馈调整 · interval 动态计算 · T0-T6 节点语义丢失
- **混合**（本 ADR 决策）· 7 行骨架 + 每节点独立 SM-2 微调

## Decision

采用**混合方案**：

1. Consumer 消费 `wrongbook.item.analyzed` 事件后 · 幂等 INSERT 7 行 `review_plan`（`node_index 0..6` · 偏移 `[2h,1d,2d,4d,7d,14d,30d]`）
2. 每行独立持有 `ease_factor=2.5`（初值）+ `interval_days`（对应偏移）
3. 用户 `POST complete` 当前节点时 · `SM2Algorithm.compute` 微调**当前行**的 `ease_factor`/`interval_days`/`next_review_at` · **不级联后续节点**
4. `quality<3` 时 `reset` 到 `ease=2.5`/`interval=1`（SM-2 论文标准 · Q-C）
5. `quality≥3` 时按论文公式 `clamp` 到 `[1.3, 2.5]`（ease）/ `≤60d`（interval）
6. 连续 3 次 `ease≥2.8` 触发 `mastered` · 所有 7 行软删 + 发 `review.mastered`（Q-G）

## Rationale

- **保留 Ebbinghaus 经典曲线的直观性**（T0-T6 节点可视化 · S8 前端展示需要）
- **引入 SM-2 动态反馈**（用户记忆强度个性化 · 不一刀切）
- **节点独立 SM-2**（Q-F）而非聚合根共享 · 简化事务边界 · `complete` 只 UPDATE 1 行

## Alternatives Rejected

| 方案 | 拒绝理由 |
|---|---|
| 纯 Ebbinghaus | 忽略用户记忆差异 · 记不住的反复背 / 记住的浪费时间 |
| 纯 SM-2 单行 | T0-T6 节点语义丢失 · S8 可视化"当前在第几轮"无依据 |
| 聚合根共享 SM-2 | complete 需级联更新 N+1..6 节点 next_review_at · 事务范围扩大 · 乐观锁冲突概率升 |

## Consequences

- **正面**：算法 P99 ≤ 10ms（纯函数）· complete 事务单行 UPDATE · 409 率可控
- **风险**：`ease_factor` 在节点间不共享 · 同一错题不同节点的 ease 可能分化 · 后续可能需聚合视图（延迟到 S10 监控阶段评估）
- **存储**：700 万行 `review_plan`（1 万 DAU × 100 错题 × 7 节点）· 1.4GB/年

## References

- SuperMemo SM-2 原论文（Wozniak 1990）
- 落地实施计划 §9.1 A1-A10 · §9.0.5 AC-3
- `design/艾宾浩斯.md` T0-T6 曲线定义
- `design/analysis/s5-business-analysis.yml` SC-07.AC-2 verification_matrix
