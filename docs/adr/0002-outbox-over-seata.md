# ADR 0002 — 分布式事务：Outbox + RocketMQ 事务消息（不用 Seata）

**Status**: Accepted · 2026-04-22
**Context**: 错题落库 + 触发 AI 解析 + 发送 RocketMQ 事件 = 跨资源操作。如何保一致性？

## 选项

1. **Outbox + RocketMQ 事务消息**（本次采纳）— 业务库写 outbox 行 · Relay 发到 RocketMQ · consumer 拉取处理
2. Seata AT / TCC / SAGA — 全局事务协调器（TC）管二阶段提交
3. 本地事务 + 业务代码补偿 — 出错了业务层写补偿 SQL

## 决策

采纳 **Outbox + RocketMQ 事务消息**。**Seata 不采纳**。

## 理由

- **最终一致性够用**：错题 → AI 解析 → 通知是**异步弱一致**业务，不需要跨资源强一致
- **Seata TC 本身是 SPOF**：引入额外协调器 = 增加一个故障域 · 团队运维负担 ↑
- **与 Alibaba 家族对齐**：RocketMQ 事务消息是 Alibaba 原生能力 · Spring AI + Spring Cloud Alibaba 全家桶一致
- **Outbox 表可审计**：`outbox` / `audit_log` 双表设计（S1 DDL）天然支持"事件溯源"
- **DeletionPolicy 简单**：Outbox 行发送成功后 soft delete，失败时 retry-exponential

## 后果

- 业务代码必须**同事务**写 outbox 行（不能先业务后事件）
- Relay Worker 必须幂等（X-Request-Id + 乐观锁）
- 消息顺序由 RocketMQ 的 orderly consumer 保证（同 key 入同队列）

## 参考

- 落地计划 §1.3.1 · §1.6 规则 B（禁用 Seata）
- 方案 §3.4 outbox 表 DDL
