---
phase_id: s1
exempt: true
exempt_reason: "B 级适用性 · DDL 本身即数据架构文档 · 见 落地计划 §1.5 / §5.2"
biz_gate: approved
biz_approved_by: "@allenthinking"
biz_approved_at: "2026-04-22T06:33:00Z"
gate_status: approved
approved_by: "@allenthinking"
approved_at: "2026-04-22T06:33:00Z"
sources:
  - business: "业务与技术解决方案_AI错题本_基于日历系统.md §4 数据库设计（语义引用 · 命名以 落地计划 §5.6 为准）"
  - design: "落地实施计划_v1.0_AI自动执行.md §5.1 / §5.6 / §5.7"
  - decision_memo: "Sd设计阶段_决策备忘_v1.0.md §4 Code-as-Design · ADR 0006 JPA over MyBatis"
special_requirements:
  - question: "DDL source authority"
    answer: "落地计划 §5.6 verbatim · 23 表新命名权威 · 语义从解决方案 §4 借用 · 差异登记 ADR 后续修订"
    raised_at: "2026-04-22T06:20:00Z"
  - question: "Q1 · PostgreSQL session TIMEZONE"
    answer: "UTC · ALTER DATABASE wrongbook SET TIMEZONE UTC · 前端按 user_profile.timezone 换算"
    raised_at: "2026-04-22T06:20:00Z"
  - question: "Q2 · pg_trgm 加入时机"
    answer: "本 Phase 不加 · M4 需要全文检索时再加 · 避免提前引入未用扩展"
    raised_at: "2026-04-22T06:20:00Z"
  - question: "A2 · 主键策略"
    answer: "Override plan A2 · BIGINT PRIMARY KEY with Snowflake 应用生成 · 不用 bigserial · 不用 UUID"
    raised_at: "2026-04-22T06:28:00Z"
  - question: "A1 A3 A4 A5 A6 A7 A8"
    answer: "按 §5.1 原文 approve · A1 Flyway-only (3 U-scripts) · A3 timestamptz UTC + biz-time nullable · A4 blanket GIN · A5 deleted_at 软删 · A6 FK RESTRICT 业务表 / 事件表无 FK · A7 subject CHECK 约束 · A8 audit_log append-only"
    raised_at: "2026-04-22T06:28:00Z"
