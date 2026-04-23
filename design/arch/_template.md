---
phase_id: "<phase-id>"
biz_gate: draft                                 # draft | in_review | approved
biz_approved_by: ""
biz_approved_at: ""
business_arch_diagram: "#0-业务架构图"           # 必填 · 指向本文件第 0 节锚点
special_requirements:                           # 必填 · 允许显式 none · 不允许字段缺失
  - question: ""
    answer: ""
    raised_at: ""
gate_status: draft                              # draft | in_review | approved (G-Arch)
approved_by: ""
approved_at: ""
sources:
  - business: "业务与技术解决方案_AI错题本_基于日历系统.md §XX"
  - design:   "Sd设计阶段_决策备忘_v1.0.md §X"
---

<!--
落地计划 v1.8 · Arch 文档模板（按 AC 分节五行齐全）
§1.5 通用约束 #13 机械等式：
  - grep -cE '^## AC: SC-' > 0
  - 每 AC 节须含 API / Domain / Event / Error / NFR 五行
G-Arch Gate 硬检查：check-ac-coverage.sh <phase> --arch 返回 0
-->

## 0. 业务架构图（Business Architecture · 锚点 #0-业务架构图）

<!-- Mermaid flowchart 或 C4-Context · 节点 label 是下游 §1 领域模型的命名来源 -->

```mermaid
flowchart LR
  Actor[角色] --> Capability[业务能力]
```

## 1. 领域模型（Domain Model）

- 聚合根 / 实体 / 值对象（字段 · 不变量 · 生命周期）
- Mermaid classDiagram / stateDiagram-v2

## 2. 数据流（Data Flow）

- 上游 → 本 Phase → 下游 · Mermaid sequenceDiagram

## 3. 事件与契约（Events & Contracts）

- HTTP API：内嵌 OpenAPI 3.0 YAML 片段
- RocketMQ topic + payload JSON Schema

## 4. 非功能指标（NFR）

- SLO（P50/P95/P99 · 可用性 · 错误预算）
- 容量（QPS · 并发 · 存储增速）
- 隐私（PII 红线 · 数据保留期）
- 成本上限（AI / OSS / 流量）

## 5. 外部依赖

- Nacos / Sentinel / RocketMQ / OSS / LLM（模型名 + 版本 + 降级 + 超时 + 重试）

## 6. ADR 候选

- 本 Phase 触发的新 ADR 或引用既有 ADR

---

## AC: SC-XX.AC-Y · <AC 原文>

- **API**: `POST /path` → `OpenAPI schema ref: #/components/schemas/XX`
- **Domain**: `AggregateRoot.methodSignature(args) -> Result`（聚合根方法签名）
- **Event**: `topic: domain.xxx.created` · payload schema 见 §3 · 消费方：XX-service
- **Error**: `413 IMAGE_TOO_LARGE` · `409 VERSION_CONFLICT` · 路径：`GlobalExceptionHandler#handleXxx`
- **NFR**: P95 < 500ms · QPS 峰值 100 · 容量上限 10MB/请求

## AC: SC-XX.AC-Z · <AC 原文>

- **API**: ...
- **Domain**: ...
- **Event**: ...
- **Error**: ...
- **NFR**: ...
