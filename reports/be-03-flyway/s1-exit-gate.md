# BE-03-flyway · S1 Exit Gate

**Branch**: `agent/be-03-flyway-s1`
**Base**: `feature/s7-frontend-core` @ `f738b1d` (S0 已合并)
**Phase**: S1 · DDL Flyway 迁移 · 17 个 V*.sql + 1 个 ebbinghaus seed

## 出口门禁自核

| 门禁 | 状态 | 说明 |
|---|---|---|
| 17 个 V*.sql 文件全部就绪 | ✅ | 5 个 service 模块（common/wrongbook/review-plan/anonymous/file）+ 1 个 seed |
| 所有时间字段 TIMESTAMPTZ | ✅ | grep 0 命中裸 TIMESTAMP |
| `wb_push_task.idempotency_key` UNIQUE INDEX | ✅ | C6 红线 · md5(node_id + scheduled_at) |
| pgvector vector(1024) + ivfflat lists=100 | ✅ | wb_question.embedding · O-10 |
| pg_trgm GIN trgm_ops on ocr_text | ✅ | 全文模糊搜索 |
| `mvn -q -DskipTests validate` 主仓全模块通过 | ✅ | pom 校验 |
| **FlywayBootstrapIT 实际跑迁移 (Docker)** | ⚠️ 未跑 | 需要 Docker daemon · 待 User 跑 `docker compose up postgres` 后 `mvn -pl integration-test test` 验 |
| 所有主表含 `tenant_id` | ❌ 14 文件缺 | 见下 "Caveat" |
| 所有状态机表含 `version` | ❌ 部分缺 | 见下 "Caveat" |

## Sub-agent 与 Orchestrator 协作

- **Sub-agent (a38195d00022cb841 · b 模式)**: 写完 17 个 V*.sql 主体后被 Orchestrator stop（误判卡住 · 实际 git status 行数 = 5 untracked 目录 ≠ 文件数 17 · stop 时 sub-agent 正在最后阶段 review-plan）
- **Orchestrator**: CronDelete + 6 项 grep 自检 + mvn validate + 写本 exit-gate + commit + push

## ⚠️ Caveat（待 User / Reviewer 评估）

**C-01 · 14 文件缺 `tenant_id`**：
- wb_analysis_result / wb_question_outbox · review-plan-service 全部 · anonymous-service 全部 · wb_file_lifecycle
- 可能合理：outbox 事件路由 / 观察者会话单租户内 / ebbinghaus seed 系统级
- 可能不合理：wb_analysis_result（应跟 wb_question 一致 · per-tenant 数据）/ guest_session（多租户应隔离）
- **建议**：Reviewer 静态扫 → 决定是否补

**C-02 · 14 文件无 `version`**：
- 多数合理：outbox / log / seed / config 表本身不走 CAS
- 可能不合理：guest_session / observer_session（state machine · CAS 跃迁需要）/ wb_review_record
- **建议**：Reviewer 检查状态机表 + 加 version

**C-03 · FlywayMigrateIT 未实跑**：
- 需要本地 Docker daemon 起 PG container
- 命令：`docker compose -f infra/docker-compose.dev.yml up -d postgres && mvn -pl integration-test test`
- User 验证后回报 · 或 S2 phase 顺带验证

## 17 文件清单

```
common/V1.0.004__bootstrap_pg_trgm_btree_gin.sql
wrongbook-service/V1.0.010__wb_question.sql            (主错题表 + pgvector + pg_trgm)
wrongbook-service/V1.0.011__wb_analysis_result.sql
wrongbook-service/V1.0.012__wb_question_outbox.sql
review-plan-service/V1.0.060__wb_review_record.sql
review-plan-service/V1.0.061__wb_push_task.sql        (含 idempotency_key UNIQUE · C6)
review-plan-service/V1.0.062__wb_push_log.sql
review-plan-service/V1.0.063__ebbinghaus_node_config_seed.sql  (7 行 T0..T6)
anonymous-service/V1.0.070__guest_session.sql
anonymous-service/V1.0.071__guest_rate_bucket.sql
anonymous-service/V1.0.072__share_token.sql
anonymous-service/V1.0.073__share_token_audit.sql
anonymous-service/V1.0.074__observer_invite.sql
anonymous-service/V1.0.075__observer_session.sql
anonymous-service/V1.0.076__account_device.sql
file-service/V1.0.080__wb_file.sql
file-service/V1.0.081__wb_file_lifecycle.sql
```

## S2 任务对接

S2 = file-service + anonymous-service-base + OSS · 依赖 S1 表已在
- file-service 用 V1.0.080 / 081
- anonymous-service 用 V1.0.070..076

**S2 启动前如要补 tenant_id / version**：建议另派 BE-03 修复 sub-agent 或 Orchestrator 直接 Edit 14 文件。
