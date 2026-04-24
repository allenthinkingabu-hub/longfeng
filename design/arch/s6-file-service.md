---
phase_id: s6
exempt: true
exempt_reason: "B 级适用性 · file-service 以封装外部存储为核心 · 领域极简（file_asset 单表 S1 已落）· SPI 接口 StorageProvider 契约即架构 · 见 落地计划 §1.5 / §10.2 / ADR 0009"
phase_level: B
biz_gate: approved
biz_approved_by: "@allen"
biz_approved_at: "2026-04-24T06:00:00+08:00"
gate_status: approved
approved_by: "@allen"
approved_at: "2026-04-24T06:00:00+08:00"
sources:
  - business: "业务与技术解决方案_AI错题本_基于日历系统.md §2A.5 图片上传 · §2A.7 复习日历图片附件 · §2B.SC-11 OSS 上传压缩 · §3.2 file_asset 表"
  - design: "落地实施计划_v1.0_AI自动执行.md §10 Phase S6 · §1.5 B 级适用性 · ADR 0009 本地 MinIO / 阿里云 OSS 双实现"
  - business_analysis: "design/analysis/s6-business-analysis.yml · schema 1.1 · 3 AC · 2 critical"
special_requirements:
  - question: "Q-A · sc_covered 权威源"
    answer: "mapping 权威 · sc_covered=[SC-11]。主执行档 §10.1 文案 SC-03/SC-14 归属留 TODO · 下次主文档迭代修。"
    raised_at: "2026-04-24T05:55:00+08:00"
  - question: "Q-B · OssProvider 本会话落地深度"
    answer: "只落 MinioProvider + OssProvider stub（UnsupportedOperationException）。AK/SK vault 接入留 S10。IT 用 Testcontainers MinIO。"
    raised_at: "2026-04-24T05:55:00+08:00"
  - question: "Q-C · 原图保留策略（§10.1 Q1）"
    answer: "90 天后归档 · 保留 thumb+medium。original 由 S10 归档 job 迁冷存 · thumb (≤320px) + medium (≤1920px) 永久保留。"
    raised_at: "2026-04-24T05:55:00+08:00"
  - question: "Q-D · OSS bucket 策略（§10.1 Q2）"
    answer: "分 prod/staging 两个 bucket · StorageProperties.bucket 按 profile 切。本会话 Q-B MinIO stub · OSS bucket 实际配置留 S10。"
    raised_at: "2026-04-24T05:55:00+08:00"
  - question: "A1-A8 业务假设批量"
    answer: "按主执行档 §10.1 原文 approve · A1 FEATURE_STORAGE_PROVIDER ∈ {minio, oss} / A2 前端直传 presign / A3 异步 RocketMQ 图片处理 / A4 10MB 上限 + 9 张批量 / A5 TTL 900s / A6 file_asset 字段 S1 已落 / A7 GDPR 软删 + 延迟 30d 硬删 · S10 job / A8 dev-compose 启 MinIO"
    raised_at: "2026-04-24T05:55:00+08:00"
---

本 Phase 豁免 Design Gate · 0.2 架构设计因 B 级适用性（StorageProvider SPI 接口契约即架构 · file_asset 单表 S1 已落 · 领域极简）自动 approved。

- 业务理解：✅ 已完成（§10.1 · A1-A8 假设 User 批准 · Q-A/B/C/D 决策归档 · 见 front matter special_requirements）
- 架构设计：N/A · StorageProvider SPI 接口即契约 · 权威符号来源为 `backend/file-service/src/main/java/com/longfeng/file/provider/*.java` 接口定义 · file_asset DDL 在 S1 V1.0.XXX 已落
- 符号一致性：check-arch-consistency.sh s6 识别 B 级 `exempt: true` 后直接放行（exit 0）
