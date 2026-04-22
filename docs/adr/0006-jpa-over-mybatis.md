# ADR 0006 — 持久层：Spring Data JPA + QueryDSL 5（不用 MyBatis）

**Status**: Accepted · 2026-04-22

## 选项

1. **Spring Data JPA (Hibernate 6) + QueryDSL 5**（本次采纳）
2. MyBatis / MyBatis-Plus（国内传统栈）
3. 直接 JdbcTemplate / jOOQ

## 决策

采纳 **JPA + QueryDSL**。**MyBatis / MyBatis-Plus 全局禁用**。

## 理由

- **类型安全动态查询**：QueryDSL 的 `JPAQuery` + Q 类元模型编译期校验 · MyBatis 的 `<if>` XML 靠运行期
- **pgvector 支持好**：Hibernate 6 原生向量类型 + `VectorType` 注解 · 与 Spring AI Embedding 无缝串联
- **Migration 单一来源**：Flyway + JPA 实体 · 避免 MyBatis `*.xml` + 实体双份状态
- **OpenAPI 生成一致**：Schema 来自 JPA 注解（`@Entity` + Bean Validation）· 方案文档 §2A.5

## 后果

- 复杂多表联查需写 QueryDSL DSL · 学习曲线比 MyBatis XML 陡
- N+1 必须用 `@EntityGraph` / `fetch join` 防御 · CI 的 hibernate-statistics 断言会拦下
- 禁止直接 `EntityManager.createNativeQuery` 绕开类型（仅 DDL script 豁免）

## 参考

- 落地计划 §1.3.1 · §1.6 规则 B（禁用 MyBatis）
