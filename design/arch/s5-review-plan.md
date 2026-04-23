---
phase_id: s5
biz_gate: approved
biz_approved_by: "@allen"
biz_approved_at: "2026-04-23T18:00:00+08:00"
gate_status: draft                   # G-Arch 阶段补完 §1-6 后 → approved + 打 s5-arch-frozen tag
approved_by: ""
approved_at: ""
exempt: false                        # A 级 Phase · 非豁免
phase_level: A
sources:
  - business: "业务与技术解决方案_AI错题本_基于日历系统.md §2A.4 艾宾浩斯 · §2A.7 复习日历 · §3.2 核心表 DDL · §2B.SC-07/08/09/10"
  - design: "落地实施计划_v1.0_AI自动执行.md §9 Phase S5 · §25 Playbook · §1.5/1.7/1.8 通用约束"
  - decision_memo: "Sd设计阶段_决策备忘_v1.0.md §4 Code-as-Design · ADR 0005 RocketMQ 事务消息 · ADR 0006 JPA over MyBatis · ADR 0011 查询走 JPA · ADR 0013 SM-2 over Ebbinghaus（本 Phase 新立）"
  - ebbinghaus: "design/艾宾浩斯.md · T0-T6 曲线 + SM-2 quality=0-5 映射"
  - business_analysis: "design/analysis/s5-business-analysis.yml · schema 1.1 · 5 AC · 3 critical"
special_requirements:                # §1.7 规则 A' · AskUserQuestion 2 轮 8 题 Q-A..Q-H 决策
  - question: "Q-A · sc_covered 权威源"
    answer: "mapping 权威 · sc_covered = [SC-07, SC-08, SC-09, SC-10]。主执行档 §9 的 SC-06/09 锚点留 TODO · 下次主文档迭代再修正。"
    raised_at: "2026-04-23T17:45:00+08:00"
  - question: "Q-B · review_plan 数据模型"
    answer: "混合 · 7 行骨架 + SM-2 每节点复习后微调。Consumer 消费 analyzed 事件一次性 INSERT 7 行（node_index 0..6 · 偏移 [2h, 1d, 2d, 4d, 7d, 14d, 30d]）· 每行独立 ease_factor/interval_days。"
    raised_at: "2026-04-23T17:45:00+08:00"
  - question: "Q-C · SM-2 quality<3 处理"
    answer: "reset 到 2.5（论文标准）· nextEaseFactor=cfg.easeInit(2.5) · nextIntervalDays=1。"
    raised_at: "2026-04-23T17:45:00+08:00"
  - question: "Q-D · T0 节点首次复习时刻"
    answer: "创建后 2 小时（按用户 timezone · 见 Q-E）· Consumer 侧 next_review_at = now() + 2h。"
    raised_at: "2026-04-23T17:45:00+08:00"
  - question: "Q-E · 跨时区每日定义"
    answer: "user_profile.timezone。库存 timestamptz UTC · 前端按 profile 渲染 · GET /review-plans?date= 后端按 header X-User-Timezone 解析（默认 Asia/Shanghai）。"
    raised_at: "2026-04-23T17:55:00+08:00"
  - question: "Q-F · Q-B 混合模式精确语义"
    answer: "节点独立 SM-2 · 7 行每行独立持有 ease_factor/interval_days · complete 当前节点只 UPDATE 该行 · 不级联后续节点。"
    raised_at: "2026-04-23T17:55:00+08:00"
  - question: "Q-G · review.mastered 触发阈值"
    answer: "连续 3 次 & ease≥2.8 · consecutiveGoodCount >= 3 && easeFactor >= 2.8 → 所有 7 行 plan status=mastered + deleted_at · 发 review.mastered 事件。"
    raised_at: "2026-04-23T17:55:00+08:00"
  - question: "Q-H · acceptance-criteria-signed.yml 签字方式"
    answer: "AI 代拟 threshold 待 User 审签。AI 按 business-analysis.yml 为 SC-07/08/09/10 拟 acceptance[] · draft_by: ai-planner-s5 · User 再单次签 signed_by/signed_at。"
    raised_at: "2026-04-23T17:55:00+08:00"
---

# S5 · review-plan-service 架构

> **G-Biz 已 approved @ 2026-04-23T18:00** · §9.1 业务理解闭环完成。
> **G-Arch 待办**：本文件 §1-6 节（领域模型 / 数据流 / 事件契约 / NFR / 外部依赖 / ADR 候选 · 含按 AC 分节五行齐全）在 §25 Playbook Step 7（Planner 切卡）前必须补完 · 打 `s5-arch-frozen` tag。

## 0. 业务架构图（Business Architecture · G-Biz 产物）

### 业务范围摘要（≤ 300 字）

S5 `review-plan-service` 实现 AI 错题本的复习计划调度：消费 S4 产出的 `wrongbook.item.analyzed` 事件后，对每条错题幂等 **INSERT 7 行 `review_plan`**（`node_index 0..6` · 艾宾浩斯偏移 `[2h, 1d, 2d, 4d, 7d, 14d, 30d]`）· 每行独立持有 `ease_factor=2.5` + `interval_days`（**节点独立 SM-2 · Q-B/F**）。由 XXL-Job 每 5 分钟扫 `next_review_at ≤ now()` 的行 · 经 Feign 发 `review.due` 事件给 notification-service。用户完成复习后 `POST /review-plans/{id}/complete {quality: 0-5}` · 同事务调 `SM2Algorithm.compute`（**quality<3 reset 到 2.5 · Q-C**）更新当前节点行 + 写 `review_outcome` + 乐观锁（`dispatch_version`）+ 发 `review.completed` 事件。连续 3 次 `ease≥2.8` 触发 mastered（**Q-G**）· 软删所有 7 行 + 发 `review.mastered`。对 calendar-platform `core-service` 的 Feign 调用走 Sentinel 熔断 + Caffeine 10min cache（**Q-G SC-10.AC-1**）。时间戳统一 UTC 存储 · 前端按 `user_profile.timezone` 换算（**Q-E**）。

本 Phase **不负责**：错题录入（S3）· AI 解析（S4）· 通知推送（notification-service via S6）· 前端 UI（S7/S8）· 匿名态（S11）· 家长监督聚合视图（S5 仅提供 GET /review-stats · S8 做视图）。

### 业务架构图（Mermaid `flowchart`）

```mermaid
flowchart LR
  subgraph 外部["外部服务"]
    S4[S4 · ai-analysis-service]
    S6[S6 · notification-service]
    S8[S8 · 前端复习/学情 UI]
    CAL[calendar-platform · core-service]
  end

  subgraph S5["S5 · review-plan-service"]
    CONS[WrongItemAnalyzedConsumer<br/>幂等 INSERT 7 行]
    SVC[ReviewPlanService<br/>createSevenNodes / complete]
    ALGO[SM2Algorithm<br/>纯函数 · quality<3 reset]
    JOB[ReviewDueJob<br/>@XxlJob · 5min · 乐观锁]
    CTL[ReviewPlanController<br/>5 API 端点]
    FEIGN[CalendarFeignClient<br/>+ Sentinel + Caffeine]
  end

  subgraph DB["S1 · PostgreSQL"]
    RP[(review_plan<br/>7 行/错题)]
    RE[(review_event)]
    RO[(review_outcome)]
    OBX[(review_plan_outbox<br/>ADR 0005)]
  end

  subgraph MQ["RocketMQ 5.1"]
    T1[wrongbook.item.analyzed]
    T2[review.due]
    T3[review.completed]
    T4[review.mastered]
  end

  S4 -- "event" --> T1
  T1 --> CONS
  CONS --> SVC
  SVC --> RP
  SVC --> ALGO

  JOB --> RP
  JOB -- "due 节点" --> T2
  T2 --> S6

  S8 -- "POST complete" --> CTL
  CTL --> SVC
  SVC --> RO
  SVC --> RE
  SVC -- "Outbox" --> OBX
  OBX -- "反投" --> T3
  T3 --> S8
  SVC -- "连续 3 次 ease≥2.8" --> T4
  T4 --> S8

  S8 -- "GET /review-plans?date=" --> CTL
  S8 -- "GET /review-stats?range=" --> CTL

  CTL --> FEIGN
  FEIGN -- "GET /calendar/nodes" --> CAL

  classDef critical fill:#fee,stroke:#c66;
  class CONS,SVC,FEIGN critical;
```

**图注**（与 business-analysis.yml ac_coverage 对齐）：
- **SC-07.AC-1**（critical · 消费者幂等）：S4 → `T1` → `CONS` → 7 行 `RP`
- **SC-07.AC-2**：`ALGO` 纯函数（被 `SVC` 调用）
- **SC-08.AC-1**（critical · 复习主循环 · 乐观锁）：S8 → `CTL` → `SVC` → `RP`/`RO`/`RE`/`OBX` → `T3`/`T4`
- **SC-09.AC-1**：`CTL` GET /review-stats · JPA 聚合 `RE`/`RO`
- **SC-10.AC-1**（critical · Feign 雪崩防护）：`CTL` → `FEIGN` → `CAL` · 熔断 + 10min cache

### 假设清单（A1-A10 · 见落地计划 §9.1 · 已 User /biz-ok）

| # | 假设 | 对应 Q 决策 |
|---|---|---|
| A1 | 消费 `wrongbook.item.analyzed` → 7 行 `review_plan` · 偏移 `[2h,1d,2d,4d,7d,14d,30d]` | Q-B/D |
| A2 | 7 行每行独立 `ease/interval` · 节点独立 SM-2 · complete 只 UPDATE 当前行 | Q-F |
| A3 | XXL-Job `review-due-scan` 5min 周期 · 批 500 · 乐观锁 `dispatch_version` | §9.0.5 AC-4（原 SC-06.AC-4 · 映射为 SC-07 内部细节 · 不独立 AC） |
| A4 | `review_outcome.quality` 0-5 分 · 前端映射 4 档 | §9.1 A4 |
| A5 | `complete` 同事务重算 · 不走异步 | §9.1 A5 |
| A6 | 每错题至多 1 套 7 行 · 唯一索引 `(wrong_item_id, node_index)` · mastered 软删全部 7 行 | Q-B/G |
| A7 | Feign 调 wrongbook/calendar 走 Sentinel 熔断 · 降级占位 `{subject:"unknown", ...}` / Caffeine 10min cache | Q-G SC-10.AC-1 |
| A8 | XXL-Job HA 走数据库锁（内置）· 不 Redisson | §9.1 A8 |
| A9 | 不跨天合并提醒 · 每节点单独发 · 聚合在 S8 前端做 | §9.1 A9 |
| A10 | `review_event` 保留 180 天 · 归档 job 不在本 Phase | §9.1 A10 |

### 歧义与缺口（Q1-Q3 · 已 User /biz-ok）

| # | 歧义 | Q 决策 |
|---|---|---|
| Q1 | SM-2 quality<3 时 ease_factor reset vs 保留 | **Q-C · reset 到 2.5**（SM-2 论文标准） |
| Q2 | T0 节点首次复习时刻 | **Q-D · 创建后 2 小时**（按用户 timezone） |
| Q3 | 跨时区每日定义 | **Q-E · user_profile.timezone**（默认 Asia/Shanghai） |

### G-Biz 签字记录

- [x] `design/arch/s5-review-plan.md` front matter `biz_gate: approved` · 签字于 front matter
- [x] `biz_approved_by: @allen` · `biz_approved_at: 2026-04-23T18:00:00+08:00`
- [x] 假设 A1-A10 已 User 过目（批量 via Q-B/D/F/G 决策）
- [x] 歧义 Q1-Q3 已 User 回复（Q-C/D/E 决策）
- [x] 额外 Q-A（SC 归属）· Q-G（mastered 阈值）· Q-H（Oracle 签字模式）决策
- [ ] **User /biz-ok**（在 PR description 或直接回复确认 · 解锁 §9.2 G-Arch 阶段）

---

## 1. 领域模型（Domain Model · G-Arch 待完成 · 按 AC 分节五行齐全）

> **G-Arch 待补**：Mermaid classDiagram（ReviewPlan / ReviewEvent / ReviewOutcome / SM2State）+ stateDiagram-v2（active → mastered）· **按 AC: SC-07.AC-1 / SC-07.AC-2 / SC-08.AC-1 / SC-09.AC-1 / SC-10.AC-1 分节**（v1.8 § 1.5 约束 #13 · 每 AC 子节五行齐全 API/Domain/Event/Error/NFR）。

（TBD · Step 7 Planner 切卡前必须完成）

## 2. 数据流（Data Flow · G-Arch 待完成）

> **G-Arch 待补**：Mermaid sequenceDiagram · 3 条路径（消费 analyzed → 7 行 / complete 单节点 / XXL-Job 扫 due）· AC 分节同 §1。

（TBD）

## 3. 事件契约（Events & Contracts · G-Arch 待完成）

> **G-Arch 待补**：OpenAPI YAML 片段（5 端点 · `GET /review-plans?date=` `POST /review-plans/{id}/complete` `POST /review-plans/batch-reset` `GET /review-plans/{id}` `GET /review-stats`）· RocketMQ JSON Schema（4 topic：`wrongbook.item.analyzed` 入 · `review.due/completed/mastered` 出）· Feign 契约（wrongbook + notification + calendar core-service）· AC 分节同 §1。

（TBD）

## 4. 非功能指标（NFR · G-Arch 待完成）

> **G-Arch 待补**：SLO（SM-2 ≤10ms · XXL-Job QPS≤100 · Feign P95≤50ms · 通知时效≤1min）· 容量（1 万 DAU × 70 行/人 = 70 万行 · review_event 180d ≈ 200 万行）· 可用性 99.9% · 成本。

（TBD）

## 5. 外部依赖（External Dependencies · G-Arch 待完成）

> **G-Arch 待补**：XXL-Job 2.4 · wrongbook-service Feign · notification-service Feign · calendar-platform core-service Feign + Sentinel + Caffeine · RocketMQ 5.1 · PostgreSQL 16 · Nacos 2.3。

（TBD）

## 6. ADR 候选（ADR Candidates · G-Arch 待完成）

> **G-Arch 待补**：
> - **ADR 0013 · SM-2 over Ebbinghaus 固定间隔**（本 Phase 新立 · 决策依据 Q-B/C/F · 动态反馈 vs 固定曲线 · ease_factor 自适应）
> - 引用 ADR 0005（RocketMQ 事务消息 · review.due/completed/mastered 走 Outbox 兜底）
> - 引用 ADR 0006（JPA over MyBatis）
> - 引用 ADR 0011（查询走 JPA 不引入 CQRS · §9.2 §5）

（TBD · 打 `s5-arch-frozen` tag 前所有 ADR 必须落档到 `docs/adr/`）
