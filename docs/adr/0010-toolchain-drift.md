# ADR 0010 — 工具链漂移：本地 JDK 25 / Node 25 · CI 基线 JDK 21 / Node 20

**Status**: Accepted · 2026-04-22
**Context**: S0 执行机器安装的是 JDK 25 + Node 25，落地计划 §1.3 基线为 JDK 21 LTS + Node 20 LTS。

## 决策

- **本地开发**允许 JDK 25 / Node 25 · 但 Maven `<release>21</release>` 强制产物目标 Java 21 字节码
- **CI 矩阵必须基线 JDK 21 + Node 20**（后续 S0 CI workflow 补齐时锁 `actions/setup-java@v4` with `java-version: 21`）
- **Docker 镜像**走 `eclipse-temurin:21-jre` / `node:20-alpine` · 绝不走 `latest`
- 每个后续 Phase 的 Builder Agent 在本地编译成功后，必须通过 CI 验证基线版本编译通过

## 理由

- Spring Boot 3.2.5 官方支持到 Java 21（Java 25 非 LTS · 兼容性风险未评估）
- 团队升级 JDK 需统一节奏 · 不在 S0 Phase 做工具链大升级
- AI Agent 在本机跑通不等于 CI 跑通 · CI 基线是唯一权威

## 后果

- 本地与 CI 可能在 JVM 特性或 Hibernate 6 行为上出现差异 · 以 CI 结果为准
- 发现不兼容时走 ADR 0010-rev 修订 · 禁止擅自升级 BOM 版本

## 参考

- 落地计划 §1.3 · §1.4 约定 6（不编造依赖）
