# Architecture — Longfeng AI 错题本

> S0 占位版本 · 详细架构回归方案文档 `design/业务与技术解决方案_AI错题本_基于日历系统.md` §2A。
> 每个领域重镇 Phase（S3/S4/S5/S7/S8/S11）会产出自己的 `design/arch/<phase-id>.md`，作为**代码符号的唯一真源**（见落地计划 §1.7）。

## 顶层视图（参考方案 §2A）

```
[ Web / H5 / 小程序 ]
        ↓ HTTPS
[ Spring Cloud Gateway (Sentinel Adapter) ]
        ↓ OpenFeign / HTTP
┌─────────┬──────────────┬─────────────┬──────────────┬─────────────┬────────────────┐
│ gateway │ wrongbook-svc│ ai-analysis │ review-plan  │ file-service│ anonymous-svc  │
└─────────┴──────────────┴─────────────┴──────────────┴─────────────┴────────────────┘
        ↓ JPA / QueryDSL 5       ↓ Spring AI ChatClient        ↓ OSS/S3
[ PostgreSQL 16 + pgvector 0.6 ] [ dashscope / openai ]        [ MinIO/OSS ]
        ↓
[ RocketMQ 5.1 · Nacos 2.3 · Redis 7 · XXL-Job 2.4 ]
```

## 技术栈速查

见落地计划 §1.3.1 **后端技术栈决策**。

## ADR 索引

- 0001 — monorepo 结构
- 0002 — Outbox + RocketMQ 事务消息（不用 Seata）
- 0003 — Nacos（不用 Eureka/Consul/Apollo）
- 0004 — Sentinel 首选（Resilience4j 回退）
- 0005 — RocketMQ（不用 Kafka）
- 0006 — JPA + QueryDSL（不用 MyBatis）
- 0007 — Spring Cloud Gateway（不用 Zuul）
- 0008 — Spring AI（不用 LangChain4j）
- 0009 — Micrometer + OTEL + Sentry
- 0010 — 工具链漂移（JDK 25 / Node 25 本地执行 · Java 21 / Node 20 CI 基线）
