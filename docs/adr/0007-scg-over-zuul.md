# ADR 0007 — API 网关：Spring Cloud Gateway + Sentinel Adapter

**Status**: Accepted · 2026-04-22

## 选项

1. **Spring Cloud Gateway 4.1 + Sentinel Gateway Adapter**（本次采纳）
2. Zuul 2（Netflix OSS · 已停止演进）
3. Kong / APISIX（外部网关 · 额外组件）

## 决策

采纳 **Spring Cloud Gateway**。**Zuul / Kong 全局禁用**。

## 理由

- 与 Spring Cloud 2023.0.1 BOM 同版本线
- 响应式 Reactor Netty · 吞吐显著高于 Zuul 1.x
- Sentinel Adapter 开箱即用（限流 / 熔断 / 黑白名单）
- Filter 链可插拔 · TraceId / Jwt / RateLimit 三 Filter 按 order 装配（S2 实现）

## 后果

- Reactor 编程模型学习曲线
- Filter 实现必须走 `WebFilter` 接口 · 同步 servlet API 不可用

## 参考

- 落地计划 §1.3 · §6 Phase S2
