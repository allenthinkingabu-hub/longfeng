# ADR 0015 · 采用 XXL-Job 2.4 而非 Quartz/ElasticJob

- **Status**: Accepted · 2026-04-23
- **Phase**: S5 · review-plan-service
- **Raised-by**: G-Arch 阶段 · `ReviewDueJob` 调度器选型
- **Related**: ADR 0006（JPA）

## Context

S5 `review-due-scan` 每 5min 扫 `next_review_at ≤ now()` 的 `review_plan` 行 · 经 Feign 发 `review.due` 事件给 notification-service。需分布式调度器：

- **必要特性**：HA（两副本防单点）· 可视化 Admin（运维触发/暂停/查看日志）· 分布式锁防重复派发
- **禁用特性**：`@Scheduled` 应用级定时（多副本重复触发 · §9.4 显式禁止）

## Decision

采用 **XXL-Job 2.4.1** ：
- `xxl-job-admin` 独立部署（外部服务 · 2 副本 HA · 内置 DB 锁 `quartz_scheduler_state` 表保单实例执行）
- `xxl-job-executor-spring-boot-starter` 内嵌到 `review-plan-service` · appname `review-plan-executor`
- `@XxlJob("review-due-scan")` 注解声明任务 · 5min cron
- 批大小 500 · 乐观锁 `dispatch_version` · CAS 失败跳过（被其他实例抢占）

## Rationale

- **HA 内置**：admin 端 DB 锁保证同一任务只在 1 个 executor 上执行 · 无需额外 Zookeeper/Redis
- **可视化 Admin**：运维可暂停/触发/看日志 · 不用命令行
- **已被团队内其他项目验证**（calendar-platform 在用 XXL-Job）
- **Spring Boot starter 成熟**（`xxl-job-core` 2.4.x 稳定）

## Alternatives Rejected

| 方案 | 拒绝理由 |
|---|---|
| **Quartz** + JDBCJobStore | 重 · 需自己做 HA（ClusteredJobStore 配置复杂）· 无可视化 Admin |
| **ElasticJob 3.x** | 依赖 Zookeeper 集群 · 运维成本高 |
| **@Scheduled + 分布式锁（Redisson）** | 侵入性大 · 需额外 Redis · 失败恢复弱 · §9.4 显式禁止 |
| **Spring Cloud Task + Batch** | 适合批处理 · 不适合定时任务 |

## Consequences

- **正面**：
  - 运维可直接 UI 操作 · 降低 ops 负担
  - HA 架构成熟 · 单点故障影响有限（1 executor 挂 · 另 1 继续 · 5min 粒度延迟可接受）
- **风险**：
  - XXL-Job admin 自身是 SPOF（本 Phase 不解决 · S10 上 admin HA）· admin 挂时 scan 停摆 · 但 complete 路径不受影响
  - 与 RocketMQ/PostgreSQL 相比 · XXL-Job 依赖多一个基础设施组件 · 增加部署复杂度
- **成本**：xxl-job-admin 2 台 1C2G VM · 复用 K8s 资源池 · 近 0 成本

## References

- 落地实施计划 §9.2 外部依赖 · §9.4 工具白名单（`@Scheduled` 禁用）· §9.7 Step 7
- `design/arch/s5-review-plan.md` §2.3（XXL-Job 扫 due → review.due 事件）
- XXL-Job 官方文档 `https://www.xuxueli.com/xxl-job/`
