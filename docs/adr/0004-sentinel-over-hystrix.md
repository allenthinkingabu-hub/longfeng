# ADR 0004 — 流控/熔断：Sentinel 首选 · Resilience4j 回退

**Status**: Accepted · 2026-04-22

## 选项

1. **Sentinel 1.8.8**（首选）— Alibaba 家族 · Spring Cloud Gateway Adapter 原生
2. **Resilience4j 2.2**（回退）— 仅限 Reactor 场景下 Sentinel 适配器有缺口时使用
3. Hystrix / Ribbon（Netflix OSS · 已停止维护）

## 决策

- 默认采纳 **Sentinel**（`spring-cloud-starter-alibaba-sentinel` + `spring-cloud-starter-alibaba-sentinel-gateway`）
- 仅在明确证明 Sentinel 适配器缺口时，Builder Agent 可在 ADR 说明后启用 Resilience4j
- **Hystrix / Ribbon 全局禁用**

## 理由

- 控制台 UI 中文友好 · 规则热更新（Nacos 后端存储）
- Gateway Adapter 与 Spring Cloud Gateway 4.1 原生兼容
- 滑动窗口 + 令牌桶 + 并发数三种流控模型 · 覆盖大部分场景

## 参考

- 落地计划 §1.3.1 · §1.6 规则 B（禁用 Hystrix）
