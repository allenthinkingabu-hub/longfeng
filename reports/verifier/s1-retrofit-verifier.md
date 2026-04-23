---
phase_id: s1
retrofit_tag: s1-retrofit-start
plan_ref: reports/retrofit/s1-plan.md
signed_by: "@allen"
approved_at: 2026-04-23T17:20:00+08:00
critical_ac_count: 5
critical_ac_ids: [S1.DDL-AC-1, S1.DDL-AC-2, S1.DDL-AC-5, S1.DDL-AC-6, S1.DDL-AC-8]
verifier_rewrite: N/A
exemption_clause: "§26.2 条款 4 类推"
generated_at: 2026-04-23T17:35:00+08:00
---

# S1 Retrofit Verifier 记录 · 整体走豁免条款 4 类推

## 一、critical AC 清单（共 5 条）

| AC ID | statement 摘要 | critical_reason |
|---|---|---|
| S1.DDL-AC-1 | Flyway V 脚本版本化 · 禁止手改 · U 脚本仅 3 个 | 合规 · K12 库变更需审计可追溯 |
| S1.DDL-AC-2 | 主键 BIGINT + Snowflake · UUID 仅匿名域 | 并发 · 匿名域身份隔离 |
| S1.DDL-AC-5 | 软删除 deleted_at + 分区索引 | 删除 + 合规 + 未成年人数据 |
| S1.DDL-AC-6 | 业务 FK RESTRICT · 事件表无 FK | 删除 + 未成年人数据保护 |
| S1.DDL-AC-8 | audit_log append-only · 无 deleted_at | 合规 · 审计不可篡改 |

## 二、为何整体走豁免（§26.2 条款 4 类推）

§26.2 条款 4 原文：「Verifier 复写暴露旧 bug 走 Hotfix 不算 Retrofit 失败。理由：Verifier 是独立角色 · 发现的是旧代码的真问题 · 阻塞 Retrofit 不合理。」

该条款的底层逻辑：**独立复写的价值来自业务语义的重解释**—— Verifier 在独立会话中重写测试，用自己理解的业务口径断言系统行为，若与 Builder 测试结果背离，暴露的是业务实现或 Builder 测试的偏差。

**S1 不存在这种语义空间**：

1. **DDL 契约无业务代码逻辑**：S1 的 8 条 AC 全部是 schema 级断言（表存在 / 列类型 / 索引类型 / FK 约束类型 / CHECK 约束 / 文件存在性）· 没有可被"不同语义口径"重新实现的业务方法。
2. **断言路径已充分机械化**：5 条 critical AC 的 verification_matrix 全部指向：
   - `FlywayMigrateIT#migrate_all_v_scripts_and_assert_schema`（表数/索引数/FK 数/CHECK 数/pgvector/ivfflat 一次性断言）
   - `ddl-count.sh`（psql 查 pg_tables / pg_indexes / pg_constraint）
   - `ddl-idempotency.sh`（双跑幂等）
   - SQL 文件静态扫描（`grep -E 'UPDATE|DELETE' V1.0.050__audit_log.sql` 等）
3. **"独立复写"在此等价于"重复跑一遍已有机械断言"**：无论 Verifier 会话怎么理解 `critical=true` AC · 最终落到代码上都是重新跑 FlywayMigrateIT + 查 pg_catalog · 无新信号。

**类推决策**：Step 5 整体豁免 · 不单独启动 Verifier 会话 · 不产独立复写测试。签字来源：`reports/retrofit/s1-plan.md` @allen 2026-04-23T17:20:00+08:00 direct-reply（风险项 §五 #4 默认方案接受 · 含 Step 5 走豁免）。

## 三、替代保障路径（Retrofit 后仍有的 critical AC 验证）

豁免 Verifier 不等于放弃验证。S1 critical AC 的验证保障由以下机制替代承担：

| 层 | 机制 | 执行时机 | 覆盖 AC |
|---|---|---|---|
| 1 · 本 Retrofit Step 6 | V-S1-01..09 全绿 + V-S1-20（schema 断言 yml） | 本 Retrofit 终态闸 | 全部 8 条（机械断言路径） |
| 2 · 各下游 Phase 首次调用 S1 DDL 时 | JPA Entity 字段类型与 DDL 不匹配会启动失败 | S2/S3 各 Phase `mvn verify` | AC-2（主键）/ AC-3（时间戳） |
| 3 · 各 Phase DoD 闸 | `check-arch-consistency.sh` + `ddl-count.sh` 重跑 | 各 Phase tag 前 | AC-1/4/5/6/7 间接 |
| 4 · S9 E2E Runner | 跨 Phase 集成跑 · 删除路径走 `ON DELETE RESTRICT` 会红 | S9 | AC-5（软删）/ AC-6（FK RESTRICT） |
| 5 · S10 合规审计 | audit_log UPDATE/DELETE 审计 + 归档 job | S10 | AC-8（append-only） |

## 四、Retrofit 是否暴露了旧 S1 bug？

**无**。本 Retrofit 仅动 `design/arch/s1-data.md`（末尾补 3 行列表）+ `design/analysis/s1-business-analysis.yml`（新增精简 yml）· 未触碰任何 migration / IT / 脚本 · 0 行代码变更 · 0 回归风险。

若后续在各下游 Phase 首次 `mvn verify` / S9 E2E 时暴露 DDL 契约问题（例如某表主键实际是 bigserial 而非 bigint · 违反 AC-2）· 按 §26.2 条款 4 走 **Hotfix 独立分支 + `fix(s1): hotfix exposed by <下游 phase> [HOTFIX-v1.8]`**  · 不算本 Retrofit 失败。

## 五、签字传递

本文件由 Builder Agent 在 Step 5 自动产出 · 接受 User 对 plan §五 #4 默认方案的签字传递（`reports/retrofit/s1-plan.md` front matter `signed_by: @allen`）· 不需独立再签。

若后续想加强 S1 critical AC 保障（例如在 S10 前独立跑一次真正的 Verifier 会话 · 对 5 条 critical AC 重写断言）· 可在 `reports/verifier/s1-retrofit-verifier-v2.md` 追加 · 但不影响本 Retrofit 终态。
