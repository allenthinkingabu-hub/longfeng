# ADR 0003 — 注册/配置中心：Nacos（不用 Eureka/Consul/Apollo）

**Status**: Accepted · 2026-04-22

## 选项

1. **Nacos 2.3**（本次采纳）— 注册 + 配置 二合一
2. Eureka（Netflix OSS · 社区维护停滞）
3. Consul（HashiCorp · 需额外部署 Connect）
4. Apollo（配置专用 · 服务注册需额外组件）

## 决策

采纳 **Nacos 2.3**。**Eureka / Consul / Apollo 全部禁用**。

## 理由

- 与 Spring Cloud Alibaba 家族一致 · BOM 统一管理版本
- 注册 + 配置一体化 · 运维单一组件
- 命名空间隔离 dev/staging/prod 开箱即用
- 中文社区与故障处理经验更丰富

## 后果

- 注册中心 HA 需至少 3 节点（生产）· S10 Helm Chart 中落地
- 配置变更走 Nacos 推送 · 敏感配置（AI Key / DB password）走 External Secrets 后再加密注入

## 参考

- 落地计划 §1.3 工具链 · §1.6 规则 B
