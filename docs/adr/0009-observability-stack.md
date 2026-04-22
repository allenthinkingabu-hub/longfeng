# ADR 0009 — 观测栈：Micrometer + OTEL + Sentry + Prometheus + Grafana + Loki + Tempo

**Status**: Accepted · 2026-04-22

## 决策

- Metrics: **Micrometer → Prometheus → Grafana**
- Tracing: **OpenTelemetry → Tempo**
- Logs: **Loki**（结构化 JSON · 带 traceId / request_id）
- Error tracking: **Sentry**（前后端共用 DSN）

## 禁用

- Netflix Servo（已死）
- Hystrix Metrics（Sentinel Dashboard 替代）

## 黄金四指标 SLO

- 延迟 P95 / P99
- 流量 QPS
- 错误率
- 饱和度（JVM · DB pool）

## 参考

- 落地计划 §14 Phase S10
