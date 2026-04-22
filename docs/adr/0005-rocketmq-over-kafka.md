# ADR 0005 — 消息队列：RocketMQ（不用 Kafka）

**Status**: Accepted · 2026-04-22

## 选项

1. **RocketMQ 5.1 + `rocketmq-spring-boot-starter` / `spring-cloud-starter-stream-rocketmq`**（本次采纳）
2. Kafka（吞吐高 · 语义弱，事务消息能力差）
3. RabbitMQ（运维复杂，不适合本项目规模）

## 决策

采纳 **RocketMQ 5.1**。

## 理由

- 与 Alibaba 家族一致 · ADR 0002 的 Outbox 事务消息需要 RocketMQ 原生支持
- 顺序消息 / 事务消息 / 延迟消息 / 定时消息 → 艾宾浩斯 T0–T6 调度天然契合
- 控制台 UI 中文友好 · 与 Nacos / Sentinel 组一家
- 延迟消息无需额外组件（Kafka 需 + delayed-queue plugin）

## 后果

- 4.x → 5.x 迁移需关注 namespace 功能与 proxy 模式
- 生产需 Broker 2+2 副本 · NameServer 3 节点

## 参考

- 落地计划 §1.3 · §1.6
