---
doc_id: PLAN-MVP-AI-AGENT-IMPL-01
title: AI 错题本 MVP · AI Agent 全链路落地实施计划 (Multi-Agent Parallel Delivery Plan)
upstream_design: design/arch/wrongbook-mvp-tech-design.md (TDD-WRONGBOOK-MVP-01)
upstream_prd: design/业务与技术解决方案_AI错题本_基于日历系统.md
date: 2026-05-02
author: AI Principal Architect & Delivery Manager
status: Draft v1.0 — 等待 User Review 通过后启动
audience: [Orchestrator (Lead), Builder Agents (FE/BE), QA Agent, DevOps Agent, Reviewer]
binding_constraints:
  - 一切计划锚定在 TDD §0–§21 + PRD v1.2 红线 (C1..C10)
  - 每个 Agent 必须在独立 git worktree 执行，禁止共享 workspace
  - 每个开发 Agent 必须交付 单元测试 + 集成测试 + 开发级 E2E 自测
  - QA Agent 独立轨道，跑 Web UI 真实交互 E2E（Playwright + miniprogram-automator）
  - 🛑 本计划文档输出后立即暂停，等待 User 明确 "Review 通过" 才允许 Orchestrator 派发任何 Sub-Agent
---

# AI 错题本 MVP · AI Agent 全链路落地实施计划

## 0. 元信息与启动协议

| 字段 | 值 |
|---|---|
| 文档 ID | PLAN-MVP-AI-AGENT-IMPL-01 |
| 上游设计 | `design/arch/wrongbook-mvp-tech-design.md` (TDD v1.0 · 4619 行) |
| 上游 PRD | `design/业务与技术解决方案_AI错题本_基于日历系统.md` v1.2 |
| 编排负责人 | Orchestrator Agent（Lead，本计划生成方） |
| 执行实体 | 18 个一线 Builder Agent + 1 QA Agent + 1 DevOps Agent + 1 Reviewer Agent |
| 工程基线 | `feature/s7-frontend-core` 分支 · commit `e810917` 之上 |
| 预计周期 | 14 工作日（与 TDD §17.1 对齐） |

### 0.1 🛑 强制暂停协议（Mandatory Hard Stop）

> **本计划文档输出完毕后，Orchestrator 立即冻结。在 User 未发出 "Review 通过" 或 "Approved" 明确指令之前，绝不允许：**
>
> 1. 派发任何 Sub-Agent / Builder Agent
> 2. 创建任何 git worktree
> 3. 执行任何代码变更（Edit / Write）
> 4. 触发任何构建 / 测试 / 部署命令
> 5. 修改本计划之外的任何文件
>
> User 通过指令样板：`Review 通过，开始 S0` / `Approved, kick off worktree setup` 等等。
> 局部修订指令样板：`修改 Phase Sx 的 Agent 拆分` / `把 SC-04 加到 smoke set` 等。

### 0.2 与 TDD / PRD 的覆盖关系

| 上游 | 本计划如何承接 |
|---|---|
| TDD §0 设计原则 + 决策注册表 | 全部进 `0_shared_context/decisions-register.md`，每个 Builder Agent 启动前必读 |
| TDD §1.3 Hard Non-Goals (C1..C10) | 进 `0_shared_context/red-lines.md`，作为 Reviewer Agent 静态检查清单 |
| TDD §3 包/文件清单 | 直接落到本计划 §5 各 Phase 的 "Files" 子表 |
| TDD §12 API 契约 | 进 OpenAPI yaml + Orval 生成 → `packages/api-contracts/gen/` |
| TDD §14 测试计划 | 落到本计划 §7 测试矩阵 + §6 QA Agent 编排 |
| TDD §17.1 14 天节奏 | 直接对应本计划 §5 的 Phase S0..S10 |
| TDD §20 D1..D8 风险硬化 | 进各 Phase 的 "风险硬化点" 子段 + Reviewer 红线检查 |

---

## 1. 现状基线 (Baseline · 工程拣货前的真实状态)

> **重要**：以下基线由 Orchestrator 本次会话调研代码库得出（2026-05-02），是 Builder Agent 不可绕过的事实起点。

### 1.1 后端服务真实状态

| 服务 | 路径 | java 文件数 | 测试数 | 状态评估 |
|---|---|---|---|---|
| `gateway` | `backend/gateway/` | 6 | 2 | 仅 JwtAuthFilter 雏形 · **AnonFilter / ShareFilter / ObserverFilter 缺** · Sentinel 路由缺 |
| `wrongbook-service` | `backend/wrongbook-service/` | 44 | 5 | CRUD 主体在 · **§S7-issues 7 项契约偏差未修** · pgvector hybrid search 缺 · embedding worker 缺 |
| `ai-analysis-service` | `backend/ai-analysis-service/` | 22 | 4 | S4 主体已落地 + SSE/WS 双协议在 · **TempFileSpooler / SafeGuardAdvisor / 4-provider hot-swap 待补** |
| `review-plan-service` | `backend/review-plan-service/` | 34 | 6 | 主体 47/47 IT 绿 · **缺 GET /review-plans · GET /{id} · POST /batch-reset · ReviewPlanDto VO** |
| `file-service` | `backend/file-service/` | 26 | 4 | presign + callback 在 · **TTL job 待补 · ClamAV 旁路 P1** |
| `anonymous-service` | `backend/anonymous-service/` | **3** | 1 | **仅骨架** · 35 个文件待全建（GuestSession/ShareToken/Observer/Device/RateLimit/Job 全空） |
| `common` | `backend/common/` | 11 | 2 | UserContext/TraceId 雏形 · **msgkey/GlobalExceptionHandler 全 22 errcode 待补** |
| `integration-test` | — | — | — | **整个模块缺失**（TDD §3.1 末尾要求新建） |

### 1.2 前端真实状态

| 区域 | 已建 | 缺失 |
|---|---|---|
| `frontend/apps/h5/src/pages/` | `Capture` · `List` · `Detail`（仅 3 页） | P00 Auth · P-HOME · P03 Analyzing · P04 Result · P07 ReviewToday · P08 ReviewExec · P09 ReviewDone · P10 CalendarMonth · P11 EventDetail · P12 Notifications · P13 Settings · P-LANDING · P-GUEST-CAPTURE · P-SHARED · P-WELCOMEBACK · P-OBSERVER（**16 页**） |
| `frontend/apps/miniapp/` | — | **整个 app 目录缺失** · 14 页（auth/home/camera/wrongbook/review/calendar/event/notification/me/landing/guest/shared/observer/welcome 全部待建） |
| `frontend/apps/h5/src/shells/` | — | AnonymousShell · TabShell · ObserverShell（3 个） |
| `frontend/apps/h5/src/bootstrap/` | — | resolve-entry.ts · deeplink-router.ts |
| `frontend/apps/h5/src/hooks/` | — | useEventSource · useDeviceFingerprint · useObserverGuard |
| `packages/ui-kit/` | s7 已落（Button/HeroDemo/ItemCard/MemoryCurve/ToastSheet/Watermark） | — |
| `packages/api-contracts/gen/` | wrongbook · ai-analysis · review-plan 雏形 | file · anonymous · notification · ai-models（待 Orval gen） |
| `packages/testids/` | s7 已立 | s8/s9/s10 testid 扩展 |
| `packages/i18n/` | zh-CN 部分 | en-US 占位骨架 + ja-JP 占位骨架（必须 ⊆ zh-CN） |
| `e2e/specs/` | `wrongbook-smoke.spec.ts`（仅 1 个） | SC-01..SC-16 共 **16 个 spec** + 19 个 POM 页面对象 + 5 个 fixtures |

### 1.3 已知阻塞项（必须在 S0 关闭）

| ID | 阻塞 | 来源 | 处置 |
|---|---|---|---|
| B-01 | S5 三端点 + ReviewPlanDto VO | `memory/project_s5_builder_state.md` + `design/tasks/preflight/s5-contract-gaps.md` | S5-Phase 必含 |
| B-02 | S7 后端 7 项契约偏差（list/cursor/mastery/image_url/tags/topic/similar） | `design/tasks/issues/S7-backend-api-contract-issues.md` | S4-Phase（合并到 wrongbook-service 修订）必含 |
| B-03 | RocketMQ topic `wrongbook.item.changed` 含点号违规 | 同上 Issue 6 | S4-Phase 修复 + Flyway 表名常量 |
| B-04 | `integration-test` 模块未建 | TDD §3.1 末尾 | S0-Phase 必建 |
| B-05 | `anonymous-service` 仅骨架（35 个文件待建） | 代码库基线扫描 | S2-S6 多 Phase 分摊（非单 Phase 可吞） |
| B-06 | gateway 4 个 Filter 链顺序未实现 | TDD §3.1 + §16.1 | S0-Phase 必含 |
| B-07 | `file-service` 不在 dev 启动栈，导致 S7 OCR E2E 失败 | `memory/project_s7_e2e_status.md` | S2-Phase 修 docker-compose dev profile |

### 1.4 跨仓服务的 Mock 策略

TDD §1.1.1 标注的 4 个跨仓服务（auth-service / user-service / calendar-core / calendar-reminder / notification）**不在本仓落地**。本计划下：

- **集成测试 (IT)**：用 WireMock/Testcontainers stub
- **开发自测 E2E**：用 MSW (前端) + WireMock (后端) 双层 stub
- **QA 真实 E2E**：要求 DevOps Agent 在 staging 起实际跨仓服务（O-01 议题不在本计划范围，本计划假设 staging 已有）
- **本仓 Feign client**：必含 Sentinel fallback（`@FeignClient(fallback=...)`），跨仓挂掉时降级链路必须可工作

---

## 2. Scope（落入本计划 / 出本计划）

### 2.1 In Scope

- TDD §1.1 全部 MVP 项（11 服务中本仓 7 个 + 19 页 H5 + 14 页小程序 + 9 业务功能 + 1 增量 SC-16 VIP）
- TDD §14 全测试金字塔（单元 60% + IT 25% + E2E 15%）
- TDD §17.1 全 11 个 Phase（S0..S10）
- 所有 D-* 决策默认值、C1..C10 红线
- 16 个 SC 的 E2E spec + 19 个 POM + 5 fixtures
- Helm chart 与 Grafana / Prometheus 配置（S10）

### 2.2 Out of Scope（明示豁免）

| 项 | 不做原因 | 处置 |
|---|---|---|
| 跨仓 4 服务（auth/user/calendar-core/calendar-reminder/notification） | 仓库边界外 | 本计划用 Feign + WireMock + Sentinel fallback；staging 已有真实服务由 DevOps 维护 |
| O-01..O-15 共 15 项 Open Issues（TDD §18） | 决策成熟度未到 | 列入 S10 之后的 P1 Backlog；不阻塞 MVP 上线 |
| Load Test / k6 压测 | TDD §14.7 标注 "Phase 2 上线前必须" | 进 S10.5 子任务但不阻塞 100% 上线；阻塞 Phase 2 |
| ClamAV 旁路扫 | TDD §1.1.1 file-service "P1" 标注 | 进 P1 Backlog，不在本计划 |
| Welcomeback (P-WELCOMEBACK) / Observer (P-OBSERVER) | TDD §1.1.2 标注 "P1" | 骨架建到 anonymous-service backend，前端页面 P1 落地 |
| ElasticSearch / ClickHouse | TDD §2.1 标注 "P1 引入·MVP 仅 pg_trgm" | 不在本计划 |
| 多租户 RLS（PG Row-Level Security） | O-07 议题 | 不启用，仅保留 `tenant_id` 字段 |

### 2.3 强制覆盖矩阵（"全部/必须 X" 条款下沉）

> 依本项目记忆 `feedback_plan_explicit_exemption.md`，所有主文档"全部/必须 X"条款必须在本计划列出对照表。

| TDD 强制条款 | 本计划承接位置 | 豁免？ |
|---|---|---|
| §0.9 D-Repo "monorepo 全 9 服务统一" | 本计划仅承担本仓 7 服务；跨仓 4 服务进 §2.2 豁免 | ✅ 部分豁免（仓库边界） |
| §0.9 D-Auth "Sa-Token + Spring Security 双闸" | gateway-Phase 必含 | ❌ 不豁免 |
| §0.9 D-State "CAS UPDATE 强制" | review-plan + wrongbook 全状态机走 CAS | ❌ 不豁免 |
| §0.9 D-Mem "AI 多模态用临时文件 spool" | ai-analysis-Phase 必含 TempFileSpooler | ❌ 不豁免 |
| §1.3 C1 "review-plan 永不直 SQL calendar_event" | 红线，Reviewer Agent 静态扫描 | ❌ 不豁免 |
| §1.3 C2 "wb_review_node 跃迁仅 CAS" | 同上 | ❌ 不豁免 |
| §1.3 C3 "匿名 session 永不写 wb_*" | 同上 | ❌ 不豁免 |
| §1.3 C4 "OBSERVER JWT 三重防护" | gateway + 业务 + 前端 ARIA | ❌ 不豁免 |
| §1.3 C5 "观察者不返回原图/email/chat_id" | DTO 白名单 | ❌ 不豁免 |
| §1.3 C6 "推送 idempotency_key" | review-plan + notification feign | ❌ 不豁免 |
| §1.3 C7 "byte[] 不在 heap" | TempFileSpooler 静态扫 | ❌ 不豁免 |
| §1.3 C8 "msgkey: 前缀 + i18n CI" | common-Phase + 各服务异常 | ❌ 不豁免 |
| §1.3 C9 "TIMESTAMPTZ + UTC 存储" | Flyway 全表 + 前端按 X-Timezone 渲染 | ❌ 不豁免 |
| §1.3 C10 "跨服务 OpenFeign + Sentinel" | 所有 Feign client 必带 fallback | ❌ 不豁免 |
| §14.1 "测试金字塔单元 60% + IT 25% + E2E 15%" | 每 Phase 出口门禁强制 | ❌ 不豁免 |
| §14.5 D-CI-Gate（13 项门禁） | S10-Phase Helm + CI 配置 | ❌ 不豁免 |
| §17.1 "14 天上线" | 本计划 14 工作日节奏 | ⚠️ 取决于 Agent 并行效率，若 staging 跨仓服务不就绪可能延后 1~2 天 |

---

## 3. AI Agent 角色矩阵

> 每个 Agent 是逻辑实体，对应一个 git worktree（§4）。同一个 Phase 内不同 Agent 可并行；跨 Phase 必须等上游 Phase 出口门禁通过。

### 3.1 编排层（Lead Agents · 单实例）

| 角色 | 实例数 | 模型建议 | 职责 |
|---|---|---|---|
| **Orchestrator** | 1 | Opus 4.7 | 总调度 · 分发任务 · 串联 Phase 出口门禁 · 跨 Agent 冲突仲裁 · 用户沟通唯一接口 |
| **Reviewer** | 1 | Opus 4.7 | 每个 Builder Agent 提 PR 后做 §1.3 C1..C10 红线静态扫 + Diff 复核；不通过则打回 |
| **DevOps** | 1 | Sonnet 4.6 | docker-compose dev profile · Helm chart · Grafana / Prometheus · 跨仓 stub 服务的运行时编排 |
| **QA Agent** | 1 | Opus 4.7 | 真实浏览器 / 小程序 E2E（Playwright + miniprogram-automator）· 16 SC 全用例 · 视觉回归 |

### 3.2 一线 Builder（并行执行实体 · 18 个）

| Agent ID | 域 | 主要 Phase | 模型建议 |
|---|---|---|---|
| `BE-01-common` | common 模块（UserContext / TraceId / msgkey / errcode） | S0 | Sonnet 4.6 |
| `BE-02-gateway` | gateway 4 Filter 链 + Sentinel + Nacos | S0 | Sonnet 4.6 |
| `BE-03-flyway` | 21 张表 + 7 outbox + ebbinghaus 配置初始化 | S1 | Sonnet 4.6 |
| `BE-04-file` | file-service presign / callback / TTL job / OSS SPI | S2 | Sonnet 4.6 |
| `BE-05-anon-session` | anonymous-service · GuestSession / Device / RateLimit | S2 | Sonnet 4.6 |
| `BE-06-anon-share-obs` | anonymous-service · ShareToken / ObserverInvite / ObserverSession | S2 | Sonnet 4.6 |
| `BE-07-ai` | ai-analysis-service · ChatClientFactory / Spooler / SafeGuard / Fallback | S3 | Opus 4.7（涉及多供应商配置 + Prompt 工程） |
| `BE-08-wrongbook-fix` | wrongbook-service · 修 S7 七项契约 · pgvector hybrid search · embedding worker | S4 | Sonnet 4.6 |
| `BE-09-review-fix` | review-plan-service · 补 3 端点 + ReviewPlanDto + s5-stats-v2 解冻 | S5 | Sonnet 4.6 |
| `BE-10-notify-feign` | 跨仓 notification feign client + 4 channel fallback | S6 | Sonnet 4.6 |
| `FE-01-shells-bootstrap` | h5 · AnonymousShell / TabShell / ObserverShell + bootstrap/resolve-entry + deeplink-router | S7 | Sonnet 4.6 |
| `FE-02-capture-flow` | h5 · P02 Capture / P03 Analyzing / P04 Result + useEventSource | S7 | Sonnet 4.6 |
| `FE-03-wrongbook-pages` | h5 · P05 List / P06 Detail（修 S7 契约对齐） | S7 | Sonnet 4.6 |
| `FE-04-review-pages` | h5 · P07 ReviewToday / P08 ReviewExec / P09 ReviewDone | S8 | Sonnet 4.6 |
| `FE-05-calendar-pages` | h5 · P10 CalendarMonth / P11 EventDetail（双形态同壳） | S8 | Sonnet 4.6 |
| `FE-06-anon-pages` | h5 · P-LANDING / P-GUEST-CAPTURE / P-SHARED + useDeviceFingerprint + useObserverGuard | S7-S8 跨 | Sonnet 4.6 |
| `FE-07-misc-pages` | h5 · P00 Auth / P-HOME / P12 Notifications / P13 Settings (含 SC-16 VIP 子区) | S8 | Sonnet 4.6 |
| `FE-08-miniapp` | 微信小程序 14 页（与 H5 业务对齐） | S7-S8 跨 | Opus 4.7（小程序生态独立 + 订阅消息） |

### 3.3 Agent 协作约束

- **写权限隔离**：任一 Builder Agent 仅在自己的 worktree 内 Edit/Write；不允许跨 Agent 共享文件锁
- **读权限放开**：所有 Agent 可读 main 分支 + `0_shared_context/*.md`
- **冲突仲裁**：两个 Agent 触及同一文件 → Orchestrator 串行调度（先合一个，第二个 rebase 后重跑测试）
- **API 契约同源**：所有 BE Agent 修改 OpenAPI 必须先更新 `packages/api-contracts/openapi/*.yaml`，FE Agent 通过 Orval gen 拿生成代码（§5.S0 步骤 3）
- **测试 ID 同源**：所有 FE Agent 使用 testid 必须经 `packages/testids/src/index.ts` 注册；Reviewer 静态扫 `data-testid="<literal>"` 是否在注册表

---

### 3.4 🔒 前端 Agent 铁律 (FE Iron Law · 不可豁免)

> **本铁律对所有 FE-01..FE-08 Agent 强制生效，无论新开发还是修复 BUG，无论 H5 还是小程序。Reviewer Agent 静态扫 + Orchestrator 出口门禁双保险，违规一律打回。**

#### 🚨 唯一权威入口 · 必先读 CLAUDE.md

**所有 FE / QA Agent 启动任务前的第 0 步**（早于本计划 §3.4 IRON-LAW-1 的 ui-plan、早于打开 mockup、早于切代码）：

```
1. 读项目根 CLAUDE.md 的「设计实施铁律」段
2. 按 CLAUDE.md 路由进入对应文档：
     - 实施 runbook：design/system/GUIDANCE.md（10 步流程 / 3 轨验收 / 5 类 fail 排查）
     - 设计真相：design/system/STYLE-TRUTH.md（archive 19 张反推 · token 唯一来源）
     - 设计宪法：design/system/DESIGN.md（铁律 + token 三层 + mood 5 类）
     - 单页规格：design/system/pages/{ID}.spec.md（数据 / 状态 / API / AC / testid）
3. 任何与 CLAUDE.md / 上述四份文档冲突的本计划条款，以 CLAUDE.md / GUIDANCE 为准
```

**冲突时的优先级**（高 → 低）：

| 优先级 | 来源 | 适用 |
|---|---|---|
| 1 | `CLAUDE.md` 设计实施铁律段（项目根） | 所有 FE / QA Agent 强制读 |
| 2 | `design/system/STYLE-TRUTH.md` | 设计真相 / token / hex 值 |
| 3 | `design/system/DESIGN.md` | 设计宪法 v2.0 |
| 4 | `design/system/GUIDANCE.md` | step-by-step 执行 |
| 5 | `design/system/pages/{ID}.spec.md` | 单页规格卡 |
| 6 | 本计划 §3.4 / §5.S7 / §5.S8 出口门禁 | 编排层补充约束 |

> **本计划 §3.4 IRON-LAW-1..4 只是把 CLAUDE.md / GUIDANCE.md 中"先读 mockup → 严格对齐 → 自跑验收 → 循环不停"的精神，落到 Agent 派发 / 出口门禁 / Reviewer 静态扫的执行层；具体 token 名 / hex 值 / 路径 / 命令以 CLAUDE.md 为唯一真相。**

#### IRON-LAW-1 · 先看 mockup 规划，再写代码

任何 FE Agent 接到任务后的**第一件事**（早于读 build-spec、早于切代码）必须是：

1. 打开对应的高保真 mockup HTML：`design/mockups/wrongbook/<NN>_<page>.html`
2. 按 mockup 把页面拆成视觉区块（block）+ 交互流程（flow）+ 状态切换（state），落到 worktree 内 `reports/<agent-id>/ui-plan.md`，结构如下：

```markdown
## <Page Name>（mockup: 0X_xxx.html）

### 视觉区块清单（按 DOM 顺序）
- B-01 顶栏（标题 / 返回 / 右上 icon）
- B-02 主图卡片（image_url / 占位）
- B-03 ...

### 交互流程清单
- F-01 点击 B-04 提交按钮 → loading → SSE 流 → P03 跳转
- F-02 ...

### 状态切换清单
- S-01 默认 / 加载中 / 错误 / 空 / 离线
```

3. 这份 ui-plan.md 是 commit 0（必须先于第一行业务代码 commit）。Reviewer Agent 扫 `git log --oneline | head -1` 是否为 "ui-plan: <page>"。

> 如果对应页面已有 `design/tasks/preflight/<Page>-build-spec.json`，必须先调用 `/fe-preflight` 校验或更新；不允许直接基于 mockup 头脑风暴跳过 build-spec。

#### IRON-LAW-2 · 严格 1:1 对齐 mockup（差一个就不行）

实现必须**像素级 + 元素级**还原 mockup：

| 维度 | 判定 |
|---|---|
| 颜色 | 必须用 `--tkn-*` design token；token 找不到 → 触发 `/fe-preflight` 重新映射，禁止硬编码 hex |
| 间距 | padding / margin / gap 必须用 `--tkn-spacing-*` 体系，与 mockup 测量值偏差 ≤ 2px |
| 字号 / 字重 | 必须用 ui-kit 字体 token；mockup 16px Bold → token 必须命中 `--tkn-font-body-bold` |
| 排版 | DOM 顺序、对齐方式、grid/flex 结构与 mockup 一致 |
| icon | 必须用 ui-kit `<Icon>` 注册的 icon；mockup 自带 SVG 无对应 → 通过 ui-kit 扩展，禁止 inline SVG |
| 文案 | 文本必须经 i18n key（`packages/i18n/zh-CN/*.json`），key 命名按 §13.1 规范 |
| 点击响应 | 任何可点击元素必须有 `:active` 反馈；按钮必有 loading state；mockup 给的转场必须实现 |
| 转场 / 动效 | mockup 标注的进入 / 退出动效必须实现（即便 mockup 是静态 HTML，README 或 motion 注释里写了也算约束） |
| testid | 所有 mockup 主元素必有 `data-testid`，命名按 `<screen>.<region>.<element>` 注册到 `packages/testids` |

判定**不通过**的场景：
- ❌ mockup 有 8px gap 实现了 12px
- ❌ mockup 是 6 阶遗忘曲线柱状图，实现成 5 阶
- ❌ mockup 标签是 #FFE2E2 浅红，实现用了 #FFCCCC
- ❌ mockup 主按钮带 loading spinner，实现是文字变 "加载中..."
- ❌ mockup 有"长按删除"，实现只支持"点击删除"

#### IRON-LAW-3 · 自跑 E2E 逐元素对比，FE Agent 自己当第一个 QA

开发完成后，FE Agent **必须**在交给 Orchestrator 之前自己跑下面三层验收，全绿才允许 push：

| 层 | 工具 / Skill | 通过判据 |
|---|---|---|
| **C 轨 视觉 diff** | `/fe-accept-diff <Page>` | 启 vite dev → Playwright 截图 → 与 mockup HTML 截图 pixel diff ≤ 1%；产出 `reports/<agent-id>/c-track-diff/<Page>.png`（含 expected / actual / diff 三联） |
| **B 轨 Mock e2e** | `/fe-accept-mock <Page>` | MSW 拦截 API + Playwright 跑 `test-plan.json` 全部 TC；testid 全部可见；点击 / SSE / 表单 / 表单 / loading / error 状态全绿 |
| **逐元素清单核对** | 手工/脚本 | 把 mockup 视觉区块清单（IRON-LAW-1 的 ui-plan.md）逐项 ✅，截图存 `reports/<agent-id>/element-checklist.md` |

**禁止行为**：
- ❌ 跳过 C 轨：用 "我目测对了" 代替 pixel diff
- ❌ 跳过 B 轨：用 "类型检查 + 单测过了" 代替 Playwright
- ❌ B/C 轨任何一个 fail 就交差："已知 gap 等 QA Agent 处理"
- ❌ 用 `screenshot.toMatchSnapshot({ threshold: 0.5 })` 放宽到 50%
- ❌ 改 mockup 让它对齐自己的实现（mockup 是 single source of truth）

#### IRON-LAW-4 · 循环不停，gap 不打回不停下

如果 IRON-LAW-3 任意一层 fail，FE Agent **不许**通知 Orchestrator "完成"；必须立即进入 `/fe-repair` 修复循环：

```
失败 → 定位 gap（M=mockup 偏差 / V=视觉 token / B=业务行为）
    → 修复（参 /fe-repair skill 的 M/V/B 三类路径）
    → 重跑 /fe-accept-diff + /fe-accept-mock
    → 仍 fail？再次循环
    → 连续 3 次仍 fail → 升级 User（不许猜，不许跳过）
```

适用范围：
- ✅ 新开发页面
- ✅ 修复 BUG（即便是后端契约变更触发的前端修复）
- ✅ 跨 Agent rebase 后的 conflict 解决
- ✅ 视觉细节调整（哪怕只改 1 个色号）

#### IRON-LAW · Reviewer 静态扫脚本（CI 强制）

```bash
# 所有 FE Agent push 后，Reviewer Agent 必跑：
1. 检查 worktree 内是否存在 reports/<agent-id>/ui-plan.md（commit 0 是否为 ui-plan）
2. grep 检查无硬编码颜色：rg -n '#[0-9a-fA-F]{3,8}' frontend/apps/<app>/src/pages/<page>/  → 必须为 0
3. grep 检查无硬编码间距：rg -nE 'padding:\s*\d+px|margin:\s*\d+px' --type=css 同上 → 必须为 0
4. 检查 reports/<agent-id>/c-track-diff/*.png 是否存在 + diff 报告 ≤ 1%
5. 检查 reports/<agent-id>/b-track-mock.log 是否包含 "all tests passed"
6. 检查 reports/<agent-id>/element-checklist.md 是否每行都打 ✅
违反任一项 → Reviewer 直接 reject PR，不进 Orchestrator review queue
```

---

## 4. Worktree 物理隔离协议

> 强制约束：本计划每一个 Builder Agent 必须在独立的 git worktree 中执行；不同 Agent 的代码修改、`mvn` 进程、`pnpm dev` 端口、`docker-compose` 命名空间绝不互相干扰。

### 4.1 Worktree 命名约定

```
~/build/longfeng-wrongbook-worktrees/
  ├── be-01-common/         (分支: agent/be-01-common, base: feature/s7-frontend-core)
  ├── be-02-gateway/        (分支: agent/be-02-gateway)
  ├── be-03-flyway/         (分支: agent/be-03-flyway)
  ├── ...
  ├── fe-01-shells-bootstrap/
  ├── ...
  └── qa-e2e/               (分支: agent/qa-e2e, 滚动 rebase main)
```

### 4.2 Orchestrator 创建 Worktree 的样板命令

> 这是 Orchestrator 在 User "Review 通过" 后执行的第一批命令；本计划文档输出阶段**不**执行。

```bash
# 主仓位置：~/build/longfeng-wrongbook
cd ~/build/longfeng-wrongbook

# 为每个 Agent 创建 worktree（示例 3 个，实际 18 + 1 个）
git worktree add -b agent/be-01-common \
  ~/build/longfeng-wrongbook-worktrees/be-01-common feature/s7-frontend-core

git worktree add -b agent/be-02-gateway \
  ~/build/longfeng-wrongbook-worktrees/be-02-gateway feature/s7-frontend-core

git worktree add -b agent/qa-e2e \
  ~/build/longfeng-wrongbook-worktrees/qa-e2e feature/s7-frontend-core

# 后续 Agent 完成 → push 到远端 → Reviewer 检查 → Orchestrator 合并到 feature/s7-frontend-core
```

### 4.3 端口与运行时隔离

| 资源 | 隔离策略 |
|---|---|
| `mvn` 进程 | 每个 worktree 自己跑 `mvn -pl <module>`；不共享 daemon |
| `pnpm dev` 端口 | h5 默认 5173 → 用 `PNPM_PORT_OFFSET=10` 让每个 FE Agent 偏移到 5183/5193/...（DevOps Agent 维护映射表） |
| Docker | 每个 worktree 用 `COMPOSE_PROJECT_NAME=lf-<agent-id>` 命名空间 · PG/Redis/MQ 端口偏移 +100 |
| Postgres schema | dev 共用一个 PG，但 schema 按 Agent 分（`anon_be05`、`anon_be06`），Flyway 单 Agent 持有写权 |
| OSS bucket | dev 用 MinIO；每 Agent 独立 bucket `dev-<agent-id>` |
| 日志目录 | `logs/<agent-id>/` |

### 4.4 Worktree 生命周期

1. **创建**：Orchestrator 派任务时一次性 `git worktree add`
2. **执行**：Agent 在 worktree 内 完成代码 + 单测 + IT + 开发级 E2E
3. **提交**：每完成一个 commit 单元（步骤 §5 中标注的子任务）就 commit · push
4. **合并**：所有出口门禁通过 → Reviewer 检查 → Orchestrator 在主 worktree `git merge --no-ff agent/xxx`
5. **清理**：合并后 `git worktree remove` + 删除分支

### 4.5 主仓保护

- 主 worktree（`~/build/longfeng-wrongbook`）**只做合并**，不做任何 Builder 级别的代码编写
- 任何 Builder Agent 接到 "在主仓修改" 的指令必须拒绝并向 Orchestrator 报告异常

---

## 5. Phase 编排（与 TDD §17.1 对齐）

> 每个 Phase 章节统一格式：
> - **目标**（一句话）
> - **依赖**（上游 Phase）
> - **并行 Agent 表**（哪些 Agent 同步跑）
> - **Files**（按 Agent 拆分的文件清单 · 引用 TDD §3 + 现有代码基线）
> - **风险硬化点**（来自 TDD §20 D1..D8 的相关项）
> - **自测命令**（每个 Agent 在 worktree 内必须能本地执行的）
> - **出口门禁**（Orchestrator 验证 Phase 完成的硬指标 · 全绿才能 unlock 下游）

---

### 5.S0 · 仓库整合 + 骨架（0.5 d）

**目标**：把跨服务公共件、Gateway Filter 链、integration-test 模块全部建到位，让后续 Phase 有可执行的依赖。

**依赖**：无（最上游）

**并行 Agent 表**

| Agent | 子任务 |
|---|---|
| `BE-01-common` | UserContext 三态 + TraceIdFilter + msgkey 异常基类 + 22 errcode + GlobalExceptionHandler + ObjectMapperConfig + FeignAutoConfig + ShedLockConfig |
| `BE-02-gateway` | AnonFilter / ShareFilter / ObserverFilter / AuthFilter 四级链 + Sentinel + RouteConfig（Nacos 接入） |
| `DevOps` | docker-compose dev profile（PG 16 + Redis 7 + RocketMQ 5 + MinIO 8 + Nacos 2.3 + XXL-Job 2.4.1）+ 端口/命名空间偏移规则 |
| `BE-03-flyway`（启动 IT 模块） | 新建 `backend/integration-test/` Maven module 骨架（pom + 一个 hello-world IT） |

**Files**（关键 · 完整清单见 TDD §3.1）

```
backend/common/src/main/java/com/longfeng/common/
  ├── context/UserContextHolder.java          # ThreadLocal · scope=USER/OBSERVER/GUEST
  ├── filter/TraceIdFilter.java
  ├── filter/ClockInjector.java
  ├── exception/BusinessException.java        # msgkey: 前缀
  ├── exception/GlobalExceptionHandler.java   # 22 errcode 全覆盖
  ├── exception/ErrCode.java                  # 22 枚举
  ├── config/ObjectMapperConfig.java
  ├── config/FeignAutoConfig.java
  └── config/ShedLockConfig.java

backend/gateway/src/main/java/com/longfeng/gateway/
  ├── filter/AnonFilter.java                  # 匿名分流 + Bucket4j 限速
  ├── filter/ShareFilter.java                 # HS256 + Bloom revoke
  ├── filter/ObserverFilter.java              # scope=READ + Bloom revoke
  ├── filter/AuthFilter.java                  # Sa-Token + Spring Security
  └── config/RouteConfig.java                 # Nacos + Sentinel

backend/integration-test/                     # 新模块
  ├── pom.xml
  └── src/test/java/com/longfeng/integration/HelloIT.java  # 验证 SpringBootTest 启动

infra/docker-compose.dev.yml                  # DevOps 维护
```

**风险硬化点**：TDD §20.6 D6（双协议） · §20.7 D7（OBSERVER 撤销）的网关层基础

**自测命令**

```bash
# BE-01-common
cd ~/build/longfeng-wrongbook-worktrees/be-01-common
mvn -pl common -am clean test -Djacoco.skip=false
# 期望：22 errcode 单测 PASS · UserContextHolder 主子线程传播 PASS · 覆盖率 ≥ 95%

# BE-02-gateway
cd ~/build/longfeng-wrongbook-worktrees/be-02-gateway
mvn -pl gateway -am clean test
# 期望：4 Filter 链顺序断言 PASS · WireMock 模拟 Nacos 路由 PASS

# DevOps
cd ~/build/longfeng-wrongbook
docker compose -f infra/docker-compose.dev.yml up -d
docker compose ps  # 期望：6 个容器全 healthy

# BE-03-flyway IT 骨架
mvn -pl integration-test -am clean test  # 期望：HelloIT 起 PG container 成功
```

**出口门禁（Orchestrator 验证）**

- [ ] `mvn -q -DskipTests validate` 全模块通过
- [ ] common 单测覆盖率 ≥ 95%（JaCoCo report）
- [ ] gateway 4 Filter 集成测试 PASS
- [ ] `infra/docker-compose.dev.yml` 6 容器 healthy ≥ 60s
- [ ] integration-test 模块 mvn test 启 Testcontainers 成功（无 Docker 报错）
- [ ] Reviewer Agent 静态扫：所有 BusinessException 含 `msgkey:` 前缀

---

### 5.S1 · DDL Flyway（1 d）

**目标**：21 张表 + 7 outbox + 7 节点配置全部 Flyway 迁移就绪，dev 环境一键起所有 schema。

**依赖**：S0 出口门禁

**并行 Agent 表**

| Agent | 子任务 |
|---|---|
| `BE-03-flyway` | 写 26 个 V*.sql 文件 + ebbinghaus_node_config 初始化数据 + Flyway 配置 |
| `Reviewer` | 静态扫：全表 `created_at` / `updated_at` / `version` / `tenant_id` 字段齐全；TIMESTAMPTZ 而非 TIMESTAMP（C9） |

**Files**（参 TDD §4.2-4.16）

```
backend/common/src/main/resources/db/migration/   # 共享主键序列等
  └── V1.0.001__bootstrap_extensions.sql          # CREATE EXTENSION pgvector / pg_trgm

backend/wrongbook-service/.../db/migration/
  ├── V1.0.010__wb_question.sql
  ├── V1.0.011__wb_analysis_result.sql
  └── V1.0.012__wb_question_outbox.sql

backend/review-plan-service/.../db/migration/      # 大部分已存在 V1.0.053..055
  ├── V1.0.060__wb_review_record.sql              # 若 s5 已建则跳
  ├── V1.0.061__wb_push_task.sql
  ├── V1.0.062__wb_push_log.sql
  └── V1.0.063__ebbinghaus_node_config_seed.sql   # 7 行初始化数据

backend/anonymous-service/.../db/migration/
  ├── V1.0.070__guest_session.sql
  ├── V1.0.071__guest_rate_bucket.sql
  ├── V1.0.072__share_token.sql
  ├── V1.0.073__share_token_audit.sql
  ├── V1.0.074__observer_invite.sql
  ├── V1.0.075__observer_session.sql
  └── V1.0.076__account_device.sql

backend/file-service/.../db/migration/
  ├── V1.0.080__wb_file.sql
  └── V1.0.081__wb_file_lifecycle.sql
```

**风险硬化点**：C9（TIMESTAMPTZ）· §20.8 D8（pgvector ivfflat lists=100 / pg_trgm GIN 共存索引顺序）

**自测命令**

```bash
cd ~/build/longfeng-wrongbook-worktrees/be-03-flyway
docker compose -f infra/docker-compose.dev.yml up -d postgres
mvn -pl integration-test -am test -Dtest=FlywayBootstrapIT
# 期望：所有 schema flyway:info state=Success · 21 张表 + 1 ebbinghaus seed 全建
```

**出口门禁**

- [ ] `flyway info` 全部状态 Success（21 表 + 7 outbox + 7 ebbinghaus 行）
- [ ] Reviewer 静态扫：所有 `*_at` / `expires_at` 列类型 = TIMESTAMPTZ
- [ ] Reviewer 静态扫：每张主表含 `tenant_id NOT NULL` + `version INT NOT NULL DEFAULT 0`（C2 CAS 前提）
- [ ] pgvector index DDL 包含 `lists=100`（O-10 参数）

---

### 5.S2 · file-service + anonymous-service-base + OSS（1 d）

**目标**：图片上传链路打通；anonymous-service 的 GuestSession + Device + RateLimit 子域上线。

**依赖**：S1

**并行 Agent 表**

| Agent | 子任务 |
|---|---|
| `BE-04-file` | presign / callback / OSS SPI 多 provider · TTL job · D-OSS-Key 路径策略 · ObjectKeyBuilder |
| `BE-05-anon-session` | GuestSessionService / GuestSessionRepository / DeviceFingerprintService（5 来源组合）/ AccountDevice / GuestRateLimiter (Bucket4j + Redis fallback) / GuestSessionExpiryJob |
| `DevOps` | docker-compose 加入 MinIO + ClamAV(占位 P1) · MinIO bucket 初始化（wrongbook / guest-tmp / shared-thumbnail 3 个） |

**Files** 关键

```
backend/file-service/src/main/java/com/longfeng/fileservice/
  ├── controller/PresignController.java         # POST /api/files/presign
  ├── controller/CallbackController.java
  ├── provider/AttachmentStorage.java           # SPI interface
  ├── provider/MinioAttachmentStorage.java
  ├── provider/ObsAttachmentStorage.java
  ├── provider/S3AttachmentStorage.java
  ├── support/ObjectKeyBuilder.java             # D-OSS-Key
  ├── job/FileTtlSweepJob.java                  # 30/180d 分层
  └── entity/{WbFile,WbFileLifecycle}.java

backend/anonymous-service/.../session/
  ├── GuestSessionService.java
  ├── GuestSessionRepository.java
  └── GuestSessionExpiryJob.java
backend/anonymous-service/.../device/
  ├── DeviceFingerprintService.java             # 5 来源（Canvas/WebGL/AudioContext/UA/Accept-Language）
  ├── AccountDeviceRepository.java
  └── FingerprintMatchPolicy.java
backend/anonymous-service/.../ratelimit/
  ├── GuestRateLimiter.java                     # Bucket4j + Redis
  ├── LandingRateLimiter.java                   # 30/min IP
  └── RateLimitFallbackToDb.java                # Redis 挂时降级写 guest_rate_bucket
```

**风险硬化点**：§20.4 D4 D4-1（设备指纹漂移）；C3 红线（匿名 session 永不写 wb_*）

**自测命令**

```bash
# BE-04-file
cd ~/build/longfeng-wrongbook-worktrees/be-04-file
mvn -pl file-service -am test            # 单测 ≥ 80%
mvn -pl integration-test -am test -Dtest=FilePresignIT  # 真 MinIO 容器

# BE-05-anon-session
cd ~/build/longfeng-wrongbook-worktrees/be-05-anon-session
mvn -pl anonymous-service -am test
mvn -pl integration-test -am test -Dtest=GuestRateLimitIT  # 双维度 fp+ip

# 开发级 E2E（非 QA）
curl -X POST http://localhost:18080/api/files/presign \
  -H 'Content-Type: application/json' \
  -d '{"objectName":"a.jpg","contentType":"image/jpeg","sizeBytes":102400}'
# 期望：返回 200 + 包含 presigned URL
```

**出口门禁**

- [ ] `FilePresignIT` 真 MinIO 上传 + callback PASS
- [ ] `GuestRateLimitIT` 双维度限速 PASS
- [ ] `DeviceFingerprintMatchTest` 同 fp / 多账号 / 漂移 三类 PASS
- [ ] Reviewer 静态扫：anonymous-service 代码内**未出现**任何 `wb_*` 表的 JPA Entity / Repository（C3）
- [ ] DevOps：MinIO 3 bucket 初始化完成

---

### 5.S3 · Spring AI 分析（2 d）

**目标**：AI 同步 + 流式分析、4 供应商热切、Prompt 注入防御、金标 100 张通过。

**依赖**：S0（common）+ S1（DDL）+ S2（file-service 给 OSS resource URL）

**并行 Agent 表**

| Agent | 子任务 |
|---|---|
| `BE-07-ai`（主） | ChatClientFactory（4 provider）· QuestionAnalyzerImpl + TempFileSpooler · SafeGuardAdvisor · FallbackOrchestrator · AnalysisStreamHub · SSE controller · WebSocket handler · 模板 .st 热更新 · 金标测试 |
| `BE-01-common`（协助） | 加 `AnalysisChunk` 共享 DTO 到 common |
| `Reviewer` | 静态扫 C7（byte[] 不在 heap）/ C8（msgkey:）+ Prompt 注入测试样本审查 |

**Files** 关键

```
backend/ai-analysis-service/src/main/java/com/longfeng/aianalysis/
  ├── llm/
  │   ├── ChatClientFactory.java
  │   ├── OpenAiClientConfig.java
  │   ├── QianwenClientConfig.java               # 默认 qwen-vl-max
  │   ├── ZhipuClientConfig.java
  │   └── LocalVllmClientConfig.java
  ├── prompt/wrong-question-analysis.st
  ├── service/
  │   ├── QuestionAnalyzer.java                  # interface
  │   ├── QuestionAnalyzerImpl.java
  │   └── AnalysisStreamHub.java                 # Map<taskId, Sinks.Many>
  ├── controller/
  │   ├── AnalyzeController.java                 # POST /analyze + GET /stream/{taskId} SSE
  │   ├── AnalyzeWebSocketHandler.java           # /ws/analyze/{taskId}
  │   └── AiCancelController.java
  ├── pii/
  │   ├── ImageNsfwDetector.java
  │   ├── FaceMaskingService.java
  │   └── PromptInjectionGuardAdvisor.java       # Spring AI Advisor
  ├── support/
  │   ├── TempFileSpooler.java                   # D-Mem
  │   └── FallbackOrchestrator.java
  └── consumer/AnalysisCompletedConsumer.java
```

**风险硬化点**：§20.1 D1 全部子项（同步 8 s 红线 / 流式 15 s / 多供应商热切 / 手填降级）；§16.3 Prompt 注入；C7（byte[] 不在 heap）

**自测命令**

```bash
cd ~/build/longfeng-wrongbook-worktrees/be-07-ai

# 单测
mvn -pl ai-analysis-service -am test
# 必含：ChatClientFactoryTest（4 provider 启动）+ TempFileSpoolerTest（盘满异常）+ SafeGuardAdvisorTest（5 类注入样本）

# 金标
mvn -pl ai-analysis-service -Dtest=QuestionAnalyzerGoldenTest test
# 期望：100 张样本 JSON 解析 ≥ 98%；P95 < 8s

# 开发级 E2E（手动验证 SSE）
curl -N "http://localhost:18083/api/ai/stream/test-task-id" \
  -H "Authorization: Bearer <dev-token>"
# 期望：SSE 流 4 个 stage 逐步输出
```

**出口门禁**

- [ ] 单测覆盖率 ≥ 80%
- [ ] 金标 100 张通过率 ≥ 98%
- [ ] `SafeGuardAdvisorTest` 5 类样本 100% 拦截
- [ ] `FallbackOrchestratorIT` 主→备→手填三段降级 PASS
- [ ] Reviewer 静态扫：源码内未出现 `byte[] image = ...` 持久变量（C7）

---

### 5.S4 · 错题 CRUD + 检索（1.5 d）· **含 S7 七项契约修复**

**目标**：wrongbook-service 修齐 S7 七项契约偏差 · 上线 pgvector + pg_trgm + RRF 混合检索 · embedding 异步 worker · OpenAPI 同步。

**依赖**：S0 + S1 + S3（AI 给 mastery 写回事件）

**并行 Agent 表**

| Agent | 子任务 |
|---|---|
| `BE-08-wrongbook-fix` | 修 7 项契约（list→items / nextCursor→next_cursor + has_more / mastery 0-2→0-100 / origin_image_key→image_url / tags→string[] / topic 改下划线 / 游标分页实现）+ pgvector hybrid search + EmbeddingAsyncWorker |
| `Reviewer` | OpenAPI diff 检查（与 packages/api-contracts 对齐）+ C8 msgkey 扫 |

**Files** 关键（修改优先 · 新建次之）

```
backend/wrongbook-service/src/main/java/com/longfeng/wrongbook/
  ├── controller/QuestionController.java                # 修响应字段
  ├── controller/WrongbookSearchController.java         # 新增
  ├── service/QuestionService.java                      # mastery 量纲转换
  ├── service/WrongbookSearchService.java               # RRF
  ├── support/EmbeddingAsyncWorker.java                 # @Async
  ├── mq/QuestionEventPublisher.java                    # topic 名改下划线
  └── dto/{WrongItemVO, WrongItemListResponse}.java     # 字段名修

backend/wrongbook-service/src/main/resources/openapi/wrongbook.yaml  # 同源更新
packages/api-contracts/openapi/wrongbook.yaml                        # mirror
packages/api-contracts/gen/                                          # Orval 重新 gen
```

**风险硬化点**：§20.8 D8（hybrid 索引）

**自测命令**

```bash
cd ~/build/longfeng-wrongbook-worktrees/be-08-wrongbook-fix
mvn -pl wrongbook-service -am test
mvn -pl integration-test -am test -Dtest=WrongbookSearchIT
# 必含：list→items 响应 schema PASS · 游标分页 has_more=true 切片 PASS · RRF 混合排序金标

# OpenAPI 自动同步
cd /Users/allenwang/build/longfeng-wrongbook
pnpm --filter @longfeng/api-contracts gen
git diff packages/api-contracts/gen/wrongbook.ts  # 必有 diff（字段重命名）
```

**出口门禁**

- [ ] S7-issues 7 项 100% 修复（与 issues/S7-backend-api-contract-issues.md 表逐项 ✅）
- [ ] `WrongbookSearchIT` PASS
- [ ] `openapi-diff` 不破坏现有契约（仅扩展不删除字段）
- [ ] FE Agent 接到信号：可基于新生成的 api-contracts 开始 P05/P06 修订

---

### 5.S5 · 艾宾浩斯引擎 + 日历（2 d）· **含 S5 三端点缺口补齐**

**目标**：补齐 review-plan-service 三个缺端点 + ReviewPlanDto VO；s5-stats-v2 解冻；70 节点漂移 P99 < 30s。

**依赖**：S0 + S1 + S4（wrongbook 发 question.created 事件）

**并行 Agent 表**

| Agent | 子任务 |
|---|---|
| `BE-09-review-fix` | GET /review-plans（日视图 + calendar Feign）· GET /review-plans/{id} · POST /review-plans/batch-reset · ReviewPlanDto VO（next_due_at / user_id / mastery / interval）· s5-stats-v2 解冻 · CompleteReviewResp 序列化修正 |
| `BE-10-notify-feign`（提前启动） | NotificationFeignClient stub + Sentinel fallback + 跨仓 WireMock 占位 |
| `Reviewer` | C1（review-plan 永不直 SQL calendar_event）+ C2（CAS UPDATE）+ C6（idempotency_key MD5） |

**Files** 关键

```
backend/review-plan-service/src/main/java/com/longfeng/reviewplan/
  ├── controller/ReviewPlanController.java       # +3 端点
  ├── dto/ReviewPlanDto.java                     # 新建
  ├── dto/DayViewResp.java                       # 已存在 → 补字段
  ├── dto/CompleteReviewResp.java                # 修序列化
  ├── service/ReviewStatsService.java            # s5-stats-v2 解冻
  └── feign/CalendarFeignClient.java             # 已存在 → 补 batchCreate
```

**风险硬化点**：§20.2 D2（节点状态机 ↔ 日历）；§20.3 D3（Saga + Outbox + 幂等）

**自测命令**

```bash
cd ~/build/longfeng-wrongbook-worktrees/be-09-review-fix
mvn -pl review-plan-service -am test  # 必含 47/47 + 新增 ≥ 12 个测试
mvn -pl integration-test -am test -Dtest=EbbinghausEndToEndIT,ForgotResetIT,MultiPodSweepIT

# 漂移压测
mvn -pl integration-test -am test -Dtest=NodeReadyScanDriftLoadIT
# 期望：70 节点 P99 < 30s
```

**出口门禁**

- [ ] 47/47 旧测试仍绿
- [ ] 3 个新端点 IT PASS
- [ ] `ReviewPlanDto` 含 `next_due_at` / `user_id` / `mastery` / `interval` 4 字段
- [ ] `EbbinghausEndToEndIT` PASS（拍题 → AI mock → save → 7 nodes + 7 outbox events）
- [ ] `ForgotResetIT` PASS（T3 FORGOT 旧 T4-T6 CANCELLED + 新 T0-T6 SCHEDULED）
- [ ] `MultiPodSweepIT` PASS（FOR UPDATE SKIP LOCKED 双实例无重复）
- [ ] Reviewer 扫：所有节点状态跃迁通过 `UPDATE wb_review_node SET ... WHERE version=?` CAS

---

### 5.S6 · 多渠道推送（1 d）

**目标**：4 渠道（微信订阅 / APP / 邮件 / 站内）扇出 + 模板 + DND；模拟 1000 条推送成功率 ≥ 99%。

**依赖**：S5（review.due topic）

**并行 Agent 表**

| Agent | 子任务 |
|---|---|
| `BE-10-notify-feign` | NotificationFeignClient（4 channel）+ DndService + IdempotencyKey MD5 实现 + PushTask outbox + WireMock 跨仓 stub |
| `Reviewer` | C6（idempotency 唯一索引）+ msgkey 模板键 |

**Files** 关键

```
backend/review-plan-service/.../feign/NotificationFeignClient.java  # 加 4 channel + Sentinel
backend/review-plan-service/.../service/DndService.java
backend/review-plan-service/.../entity/PushTask.java                 # idempotency_key 字段 + 唯一索引
backend/review-plan-service/.../job/PushTaskRelayJob.java
infra/wiremock-stubs/notification-service.json                       # DevOps 维护
```

**风险硬化点**：§20.5 D5（推送 + DND + 失败兜底）；C6 idempotency

**自测命令**

```bash
cd ~/build/longfeng-wrongbook-worktrees/be-10-notify-feign
mvn -pl integration-test -am test -Dtest=SsePushOrchestrationIT,TimezoneRescheduleIT

# 1000 条压测
mvn -pl integration-test -am test -Dtest=PushFanoutLoadIT
# 期望：成功率 ≥ 99% · DND 时段全部延迟到次日触达
```

**出口门禁**

- [ ] `SsePushOrchestrationIT` PASS（XXL-Job ready_at → push_task → notification feign → log）
- [ ] `TimezoneRescheduleIT` PASS（SH→LA 切换 DND 重算）
- [ ] 1000 条压测成功率 ≥ 99%
- [ ] `PushTaskRepository` 唯一索引 `(idempotency_key)` 存在；重复插入抛 DataIntegrityViolation

---

### 5.S7 · 前端拍题/分析/错题本（2 d）

**目标**：H5 + 小程序双端 5 张核心页面（P02 / P03 / P04 / P05 / P06）+ 全壳骨架。

**依赖**：S3（AI SSE/WS 端点）+ S4（wrongbook OpenAPI gen）

**并行 Agent 表**

| Agent | 子任务 |
|---|---|
| `FE-01-shells-bootstrap` | AnonymousShell / TabShell / ObserverShell + bootstrap/resolve-entry（PRD §2A.3.1 决策树）+ deeplink-router（wb:// scheme） |
| `FE-02-capture-flow` | P02 Capture + P03 Analyzing（接 SSE）+ P04 Result + useEventSource hook + presign 上传 |
| `FE-03-wrongbook-pages` | P05 List（修 S7 契约对齐）+ P06 Detail（tags string[] · mastery 0-100 · image_url） |
| `FE-08-miniapp` | 小程序对齐：camera/{capture,analyzing,result} + wrongbook/{list,detail} + WebSocket /ws/analyze |
| `Reviewer` | testid 注册表完整性 + i18n key 一致性（zh-CN ⊆ en-US） |

**Files** 关键

```
frontend/apps/h5/src/
  ├── shells/{AnonymousShell,TabShell,ObserverShell}.tsx
  ├── bootstrap/{resolve-entry,deeplink-router}.ts
  ├── pages/Capture/                  # 替换现状空壳
  ├── pages/Analyzing/                # 新建
  ├── pages/Result/                   # 新建
  ├── pages/WrongbookList/            # 替换现 List
  ├── pages/WrongbookDetail/          # 替换现 Detail
  └── hooks/useEventSource.ts

frontend/apps/miniapp/                # 整个 app 新建
  ├── pages/camera/{capture,analyzing,result}/
  └── pages/wrongbook/{list,detail}/
```

**风险硬化点**：§20.6 D6（双协议）

**自测命令**

```bash
cd ~/build/longfeng-wrongbook-worktrees/fe-02-capture-flow
pnpm --filter h5 dev    # 端口偏移到 5183
# 浏览器手测：拍照 → 上传 → SSE 流 → P04 → 保存

pnpm --filter h5 test           # 单测
pnpm --filter h5 lint           # ESLint testid-required PASS
pnpm --filter h5 build          # 构建无 error

cd ~/build/longfeng-wrongbook-worktrees/fe-08-miniapp
npm run build  # 微信开发者工具兼容
```

**出口门禁**

- [ ] H5 5 页面 + 3 shell + 2 bootstrap 全部 build PASS
- [ ] axe-core jest-axe 0 serious
- [ ] testid 注册表 0 缺失
- [ ] i18n CI（zh-CN ⊆ en-US ⊆ ja-JP）PASS
- [ ] 小程序 5 页面在微信开发者工具内可加载（npm run build 成功 + 真机扫码无白屏）
- 🔒 **§3.4 FE 铁律全部 PASS（每页面）**：
  - [ ] commit 0 为 `ui-plan: <page>` · `reports/<agent-id>/ui-plan.md` 含视觉/交互/状态三类清单
  - [ ] Reviewer 硬编码扫描：颜色 / 间距 / 字号 全 0 命中（必须用 `--tkn-*` token）
  - [ ] **C 轨 pixel diff ≤ 1%**：5 个 H5 页面 + 5 个小程序页面，全部产出 `reports/<agent-id>/c-track-diff/<Page>.png`
  - [ ] **B 轨 Playwright + MSW 全绿**：每页面 `test-plan.json` 100% PASS（testid 可见 + 点击 / SSE / loading / error 状态正确）
  - [ ] 逐元素清单 `element-checklist.md` 每行 ✅；缺一不可
  - [ ] 任意一项 fail 触发 `/fe-repair` 循环，连续 3 次失败必须升级 User，禁止 "已知 gap 继续"

---

### 5.S8 · 前端复习闭环 + 学情看板（1.5 d）

**目标**：P07-P11 + P12 + P13 双端落地 + SC-16 VIP 模型选择子区 + 4 图同屏 LCP ≤ 2.5s。

**依赖**：S5（review-plan API）+ S6（推送状态查询）+ S7（shells 已建）

**并行 Agent 表**

| Agent | 子任务 |
|---|---|
| `FE-04-review-pages` | P07 ReviewToday + P08 ReviewExec + P09 ReviewDone + 自评 3 档 + Cancel race（D-Cancel-Race） |
| `FE-05-calendar-pages` | P10 CalendarMonth + P11 EventDetail（学习版 / 通用版 / 分享脱敏 三形态同壳） |
| `FE-06-anon-pages` | P-LANDING + P-GUEST-CAPTURE + P-SHARED + useDeviceFingerprint + useObserverGuard |
| `FE-07-misc-pages` | P00 Auth + P-HOME + P12 Notifications + P13 Settings · **含 SC-16 VIP AI 模型选择子区**（NORMAL 显示 hint / VIP 显示选择器 / VIP_PLUS 显示实验池） |
| `FE-08-miniapp` | 复习/日历/推送/我 7 页对齐 + 微信订阅消息申请 |
| `Reviewer` | SC-16 静默忽略 NORMAL 用户 `aiModelHint` 不报 403（防 tier 信号泄露） |

**Files** 关键（参 TDD §3.2 全清单）

**风险硬化点**：§20.7 D7（OBSERVER 撤销实时性）；SC-16 §16.8 三层防护

**自测命令**

```bash
cd ~/build/longfeng-wrongbook-worktrees/fe-04-review-pages
pnpm --filter h5 test
pnpm --filter h5 dev    # 浏览器手测：P07→P08 自评 MASTERED→T2

# Lighthouse 4 图同屏（含 P10 Calendar）
pnpm --filter h5 lighthouse:p10
# 期望：LCP ≤ 2.5s · perf ≥ 85
```

**出口门禁**

- [ ] H5 19 页面全部建齐 + build PASS
- [ ] 小程序 14 页面 build PASS
- [ ] Lighthouse perf ≥ 85 / a11y ≥ 95
- [ ] `aiModelHint` NORMAL 用户传入静默忽略不报 403（断言 200 + 走默认模型）
- [ ] testid 扩展 ≥ 50 项进 packages/testids
- 🔒 **§3.4 FE 铁律全部 PASS（每个新增 / 修订页面）**：
  - [ ] 每页面（P07/P08/P09/P10/P11/P12/P13/P00/P-HOME/P-LANDING/P-GUEST/P-SHARED）都有 `reports/<agent-id>/ui-plan.md` 三类清单
  - [ ] Reviewer 硬编码扫描全 0 命中
  - [ ] **C 轨 pixel diff ≤ 1%**：14 个 H5 页面 + 14 个小程序页面（P-OBSERVER/P-WELCOMEBACK 骨架级允许 ≤ 3%）
  - [ ] **B 轨 Playwright + MSW 全绿**：含 P08 自评 3 档 + P09 复习完成 + P10 月历切换 + P13 SC-16 VIP 选择器交互
  - [ ] 逐元素清单 `element-checklist.md` 每行 ✅
  - [ ] **特别加严**：P10 / P11 双形态同壳必须 3 形态各跑一遍 C 轨（学习版 / 通用版 / 分享脱敏版）
  - [ ] 任意一项 fail 触发 `/fe-repair` 循环；连续 3 次失败升级 User

---

### 5.S9 · E2E 联调（1 d）· **QA Agent 主战场**

**目标**：16 SC × 平均 5 TC = 78 用例 100% 跑通；smoke 6 份 ≤ 8 min；视觉回归 baseline 落库。

**依赖**：S7 + S8 全部出口门禁

**并行 Agent 表**

| Agent | 子任务 |
|---|---|
| `QA Agent`（主） | 19 个 POM + 5 fixtures + 16 SC spec · Playwright H5 + miniprogram-automator 小程序 · VRT baseline · 真实浏览器交互验证 |
| `DevOps` | staging 跨仓服务（auth / user / calendar / notification）拉起 + 数据 seed + 测试账号准备（NORMAL / VIP / VIP_PLUS / OBSERVER 各一） |
| `Reviewer` | 跨 SC 红线巡检（C1..C10 在 E2E 实际请求里的合规） |

**Files** 关键

```
e2e/
  ├── playwright.config.ts                   # H5 + miniapp 双 project
  ├── fixtures/
  │   ├── student.ts                         # loginAsStudent / loginAsVip / loginAsVipPlus
  │   ├── userTier.ts                        # 新建（SC-16 用）
  │   ├── clock.ts                           # freezeClock / advanceTo
  │   ├── guest.ts                           # newDeviceFingerprint
  │   ├── share.ts                           # issueShareToken
  │   └── push.ts                            # simulatePush
  ├── pages/                                 # 19 POM
  │   ├── LandingPage.ts ... SettingsAiModelSection.ts
  └── specs/
      ├── sc-01.spec.ts ... sc-15.spec.ts
      └── sc-16.spec.ts                      # VIP AI 模型选择（TDD 附录 G）
```

**风险硬化点**：所有 D1-D8（在 E2E 真实交互中验证）

**自测命令**

```bash
cd ~/build/longfeng-wrongbook-worktrees/qa-e2e

# Smoke（每 PR 必跑）
pnpm e2e:smoke
# 期望：≤ 8 min · 0 失败 · 含 SC-01/02/05/11/12/13/16 共 14 用例

# 全量（夜间）
pnpm e2e:full
# 期望：78 用例 100% PASS · flaky < 0.5%

# VRT
pnpm e2e:vrt
# 期望：所有 baseline 截图落地（首次跑允许全量生成）
```

**出口门禁**

- [ ] Smoke ≤ 8 min · 0 失败
- [ ] 全量 78 用例 100% PASS
- [ ] **QA Agent 必须真实通过 Web UI 操作**（不允许跳过页面用 cookie 直跳）
- [ ] axe-core 各 SC 页 0 serious
- [ ] VRT baseline 提交到 `e2e/vrt-snapshots/`
- [ ] 16 SC 红线断言（C1..C10）在 spec 内显式断言（如 OBSERVER spec 必含 "POST /xxx → 403" 断言）

---

### 5.S10 · 可观测 + 部署（1 d）

**目标**：6 张 Grafana dashboard + 9 P0/P1 alert + Helm chart + Sentry release + 上线 Checklist 全绿。

**依赖**：S9

**并行 Agent 表**

| Agent | 子任务 |
|---|---|
| `DevOps`（主） | helm/ chart × 7 服务 + Grafana dashboard JSON × 6 + Prometheus alert rules × 9 + Sentry release CI + GitOps 配置 |
| `BE-01-common`（协助） | Micrometer 50+ metric 全埋点（参 TDD §15.1 完整列表） |
| `Reviewer` | TDD §17.5 上线 Checklist 18 项逐项核对 |

**Files** 关键

```
helm/
  ├── wrongbook-service/Chart.yaml + templates/
  ├── ai-analysis-service/...
  ├── review-plan-service/...
  ├── file-service/...
  ├── anonymous-service/...
  ├── gateway/...
  └── values-{dev,staging,prod-cn,prod-overseas}.yaml

infra/grafana/dashboards/
  ├── 01-ai-overview.json
  ├── 02-review-engine.json
  ├── 03-anon-funnel.json
  ├── 04-observer.json
  ├── 05-student-retention.json
  └── 06-infrastructure.json

infra/prometheus/alerts/
  ├── ai-availability.yml
  ├── review-due.yml
  ├── outbox-dead.yml
  ├── observer-revoke.yml
  └── ... (9 个)

.github/workflows/
  ├── ci.yml          # 13 D-CI-Gate 全部
  ├── release.yml     # Sentry release + Helm push
  └── nightly-e2e.yml # 全量 E2E
```

**出口门禁**

- [ ] `helm template` 7 服务全部成功
- [ ] 6 dashboard JSON 在测试 Grafana 可加载
- [ ] 13 D-CI-Gate 在 CI 全绿（覆盖率 / IT / Smoke / Lighthouse / axe / i18n / openapi-diff / testid / 架构一致 / Flyway / Jib）
- [ ] Sentry release 含 sourcemap + git rev-parse HEAD
- [ ] §17.5 18 项 Checklist 全绿（DBA 主从延迟 / 微信模板审核 / AI 余额 / OSS bucket / WAF / 法务签字 / P0 值班）
- [ ] **Phase 2 准入**：k6 压测脚本就绪（不阻塞本计划，但作为 v1.1 入门票）

---

## 6. QA Agent 验收测试轨（独立轨道 · 严格全程对抗）

> 本计划要求 QA Agent **完全模拟真实用户**，通过 Web UI / 小程序界面一步步交互；**禁止**跳过页面用 cookie 直跳、跳过 captcha、用 stub 短路真实流程。

### 6.0 🚨 QA Agent 也必先读 CLAUDE.md

CLAUDE.md「设计实施铁律」段对 QA Agent 同样强制：

```
接到 "测 / 验 / accept / e2e / a11y" 任务 → 立即跳 GUIDANCE.md §3 三轨：
  - B 轨 mock 必过（Playwright + MSW · 每 PR）
  - C 轨 像素参考（pixel diff vs archive mockup · 每 commit）
  - A 轨 E2E（真后端 · sprint 末 / 上线前）
任一 VERIFY 步骤 fail → 转 GUIDANCE.md §4 五类 fail 场景排查
```

QA Agent 写 sc-XX.spec.ts 时的视觉断言、testid 选择器、AC 校验**全部**以 `design/system/pages/<page-id>.spec.md §8 AC` 为锚点；与本计划 §6 描述冲突时以 CLAUDE.md / spec.md 为准。

### 6.1 QA Agent 启动条件

- 进入条件：S7 出口门禁通过（不等 S8 全完，可先建 SC-01..03 spec 配 P02-P06 真实页面跑）
- 持续模式：QA Agent worktree 长期保持，每个 Phase 出口门禁后跑相关 SC
- 独立模型实例：QA Agent 与 Builder Agent 不共享 Claude session

### 6.2 16 SC 用例分配与轨道映射

| SC | 名称 | 主 Phase | 测试轨 | 交互必含 |
|---|---|---|---|---|
| SC-01 | 拍题 → 入库 → 首节点 | S7 | A 轨 e2e（真后端） | 拍照 → presign 上传 → SSE 流式分析 → P04 保存 → 列表大卡 +1 |
| SC-02 | 推送 → 执行 → 下一节点 | S8 | A 轨 e2e | 模拟推送（fixture）→ 点开 P08 → 自评 MASTERED → P09 → 列表 mastery 进展 |
| SC-03 | 全部开始 → 中途退出 | S8 | A 轨 e2e | P07 → P08 → 退出弹窗 → 二次确认 → session=PAUSED · 节点 SCHEDULED 不变 |
| SC-04 | FORGOT 重排 | S8 | A 轨 e2e | T3 FORGOT → 旧 T4-T6 CANCELLED · 新 T0-T6 SCHEDULED · 日历重排 |
| SC-05 | 视图融合 | S8 | A 轨 e2e | P-HOME 条带 → P10 月历 → P11 事件详情 → 立即复习 → P08 |
| SC-06 | 通用事件 | S8 | A 轨 e2e | 非 STUDY relation_type 事件在 P11 显示通用版 |
| SC-07 | AI 降级 | S7 | B 轨 mock + A 轨抽样 | 主供应商挂 → 备用 → 手填降级；金标 PASS |
| SC-08 | 跨时区 | S8 | B 轨 mock | 学生 SH→LA 切换 → DND 按 LA 重算 → 推送时间 |
| SC-09 | 家长分享考试日 | S8 | A 轨 e2e | 学生分享 → 家长打开 → P-SHARED 脱敏 → 推送送达 |
| SC-10 | 归档级联 | S7 | A 轨 e2e | 错题归档 → 节点 CASCADE CANCELLED → 5s 内 undo 恢复 |
| SC-11 | P-LANDING | S7 | A 轨 e2e | TTI ≤ 1s · samples 500 降级 · 30/min IP 限流 |
| SC-12 | 游客 + Claim | S7 | A 轨 e2e | 游客拍题 → analyze → 注册 → claim → 错题进入正式账号 |
| SC-13 | 分享接收 | S8 | A 轨 e2e | 分享深链篡改 → 403 · EXAM_DAY 脱敏 · 匿名写 → 403 |
| SC-14 | Welcomeback | P1 | B 轨 mock（骨架）| 设备指纹 → 多账号歧义 → 降级 P00 |
| SC-15 | Observer 三重防护 | P1 | A 轨 e2e（关键安全）| 撤销 ≤ 1s 后家长任意 API 403 · ARIA aria-disabled · 写按钮置灰 |
| SC-16 | VIP AI 模型选择（本 TDD 新立） | S8 | A 轨 e2e | P13 VIP 显示选择器 → 选 OpenAI → 持久化 → 下次分析使用；NORMAL 传 hint 静默忽略不报 403 |

### 6.3 三轨的独立运行

| 轨道 | 频次 | 触发 | 工具 |
|---|---|---|---|
| **A 轨 真实 e2e** | 每 Sprint / 上线前 | DevOps 起 staging 全栈（含跨仓） | Playwright + miniprogram-automator + 真实浏览器 |
| **B 轨 Mock e2e** | 每 PR | MSW 拦截 API + WireMock 跨仓 stub | Playwright + MSW |
| **C 轨 视觉 diff** | 每 commit | Vite dev server 截图 vs mockup HTML | Pixel diff（≤ 1%） |

QA Agent 必须**写不同 npm script** 跑三轨：`pnpm e2e:e2e-a` / `pnpm e2e:mock-b` / `pnpm e2e:vrt-c`。

### 6.4 QA Agent 报告产出

每次 SC 跑完，QA Agent 输出 `reports/<sprint>/sc-<id>-acceptance.md`，包含：

- 用例 ID / TC 编号 / PASS or FAIL
- 截图链：步骤 step-1.png ... step-N.png
- 失败用例：屏幕录像 + 浏览器 console + 网络 har
- 与 mockup 视觉差异：diff 截图（C 轨）
- 最终 gap report → User 决策（接受 / 打回 fe-repair）

### 6.5 QA Agent 不允许的行为（红线）

- ❌ 用 `localStorage.setItem('token', '...')` 跳过登录
- ❌ 直接 fetch API 验证（不通过 UI 操作）
- ❌ 关闭 SSE / WebSocket 连接验证（必须真实建联）
- ❌ 跳过浏览器渲染等待（`waitForLoadState('networkidle')` 必加）
- ❌ 使用 Playwright `route.fulfill` 短路真实接口（除非 spec 显式标注 B 轨）
- ❌ 测试通过判据仅靠 DOM 文本 contains（必须断言 testid + 数据 + 视觉）

---

## 7. 全栈测试矩阵（每个开发 Agent 必须交付）

> 本计划承接 TDD §14 测试金字塔；以下是对每个 Builder Agent 的"开发级测试交付"刚性约束。

### 7.1 单元测试（每 Builder Agent 自交付）

| 类型 | 后端 | 前端 |
|---|---|---|
| 工具 | JUnit 5 + Mockito + AssertJ | Vitest + Testing Library |
| 覆盖率门禁 | 该模块 ≥ 60%（JaCoCo report） | 该 app/package ≥ 60%（Vitest c8） |
| 必含场景 | 主路径 / 边界 / 异常 / 状态机所有跃迁 | 组件 props 矩阵 / hook 边界 / store 状态切换 |
| 提交时机 | 与代码同 commit · 不允许"先代码后补测试" | 同上 |
| 命令 | `mvn -pl <module> test` | `pnpm --filter <pkg> test` |

### 7.2 集成测试（每后端 Agent 交付到 `integration-test` 模块）

| 范围 | 内容 |
|---|---|
| 容器栈 | Testcontainers（PG 16 + Redis 7 + RocketMQ 5 + MinIO 8 + WireMock）|
| 必含 IT | 见 TDD §14.3 8 个 IT（EbbinghausEndToEnd / GuestClaimE2E / ObserverRevoke / SsePushOrchestration / ForgotReset / MultiPodSweep / TimezoneReschedule / OutboxRelayIdempotency） |
| 提交时机 | 该 Phase 出口门禁前必交 |
| 命令 | `mvn -pl integration-test -am test -Dtest=<itName>` |

### 7.3 开发级 E2E（开发 Agent 自跑 · 独立于 QA 轨道）

| 类型 | 内容 |
|---|---|
| 后端 Agent | 在 worktree 内启 dev profile + 自己写 `dev-smoke/<phase>.sh` 脚本，curl 真实接口验主路径 |
| 前端 Agent | 在 worktree 内启 vite dev + 用 Playwright 跑该 Agent 范围内的 spec 子集（不依赖跨 Agent） |
| 提交要求 | 输出 `reports/<agent-id>/dev-smoke.log` + 截图（前端） |

### 7.4 QA Agent 终极对抗 E2E（§6 详）

> 这是 PR 合并到 main 之前的**最后一道闸**；QA Agent 必须给 Orchestrator 显式 PASS 才允许进入下一 Phase。

### 7.5 测试金字塔总验

| 层 | 占比目标 | 工具 | 跑频 |
|---|---|---|---|
| 单元 | 60% | JUnit + Vitest | 每 commit + CI |
| IT | 25% | Testcontainers | 每 Phase 出口门禁 + CI |
| E2E A 轨 | 15% | Playwright + miniprogram-automator | 每 Sprint + 上线前 |
| E2E B 轨 | （不计入主金字塔） | Playwright + MSW | 每 PR |
| VRT C 轨 | （独立） | Pixel diff | 每 commit |

---

## 8. 跨 Phase 依赖图

```
S0 (common + gateway + IT module + DevOps dev compose)
   │
   ├─→ S1 (Flyway 21 表)
   │     │
   │     ├─→ S2 (file-service + anon-session) ──┐
   │     │                                       │
   │     ├─→ S3 (AI 分析) ──────────┐            │
   │     │                           │            │
   │     │                           ▼            ▼
   │     │                          S4 (wrongbook + S7 七项契约)
   │     │                           │
   │     │                           ▼
   │     │                          S5 (review-plan + 三端点)
   │     │                           │
   │     │                           ▼
   │     │                          S6 (推送 fanout)
   │     │                           │
   │     │                           ▼
   │     └─→ S7 (前端拍题/错题 + miniapp 半) ────┐
   │                                               │
   │                                               ▼
   │                                              S8 (前端复习/学情 + miniapp 全)
   │                                               │
   │                                               ▼
   │                                              S9 (E2E 联调 · QA 主战场)
   │                                               │
   │                                               ▼
   └────────────────────────────────────────────→ S10 (Helm + Grafana + 上线)
```

### 8.1 关键并行机会（缩短交付周期）

| 并行段 | 同时跑 |
|---|---|
| S1 之后 | S2 + S3 同时启动（不互依赖；S3 仅需 common DTO） |
| S4 之后 | S5 + S6 几乎并行（S6 仅需 review-plan 的 review.due topic schema，可先用 stub） |
| S7 内部 | FE-01 + FE-02 + FE-03 + FE-08-miniapp 全部并行（4 个 worktree 同步） |
| S7 / S8 跨 Phase | FE-06-anon-pages 可以从 S7 中段就启动（依赖 anon-session backend 已在 S2 给出） |
| QA Agent | S7 出口门禁后立即启动 SC-01..03 / 11..13；不等 S8 |

### 8.2 关键串行约束

- S5 必须等 S4 出口门禁（`question.created` topic schema 稳定）
- S6 必须等 S5 出口门禁（`review.due` topic schema 稳定）
- S8 必须等 S5 + S6 出口门禁（前端绑 review-plan + notification API）
- S9 必须等 S7 + S8 全部出口门禁（不允许半成品做完整 E2E）
- S10 必须等 S9（不允许测试还没绿就推 Helm 到 staging）

---

## 9. 风险与回滚预案

### 9.1 高频风险（来自 TDD §14 / §20 + 本计划新增）

| 风险 ID | 描述 | 缓解 | 升级条件 |
|---|---|---|---|
| R-01 | AI 供应商挂导致 S3 阻塞 | FallbackOrchestrator 三段降级 + 金标抽离 mock provider | 24h 不恢复 → 切默认 mock 继续推进，标注 P0 |
| R-02 | S7 旧 contract 修复影响 frontend FE-03 已写代码 | OpenAPI diff CI 拦截 + Orval 自动 gen + FE-03 启动延后到 S4 出口门禁 | gen 失败 → Orchestrator 暂停 FE-03，等 BE-08 fix |
| R-03 | anonymous-service 35 文件分摊到 BE-05 + BE-06 后仍延期 | 增第 3 个 anon Agent（BE-05.5）或推迟 P-LANDING 到 S8 | 工期晚 1d → User 介入决策 |
| R-04 | QA Agent 真实 E2E 在 staging 跨仓服务依赖不就绪 | DevOps 提前在 S5 准备 staging stub；QA Agent S9 启动前 day -1 验环境 | 跨仓挂 → A 轨降级到 B 轨（mock）+ User 风险接受 |
| R-05 | Worktree 数量过多导致本机磁盘 / 内存溢出 | DevOps 监控；超过 16 个并发 worktree 暂停低优先级 Agent | Mac 内存 < 4G → 暂停 BE-10 / FE-08 |
| R-06 | Multi-agent 同改一个文件 | Orchestrator 锁表（每个文件最多一个活跃 worktree 引用） | 撞 → 后到 Agent rebase + 重跑 |
| R-07 | Reviewer Agent 红线扫漏过 | C1..C10 静态扫脚本独立 CI job · 不依赖 Reviewer 主观判断 | 上线后发现 → 紧急 hotfix Phase |
| R-08 | E2E flaky > 0.5% | freezeClock + Testcontainers + 重试 ≤ 1 | flaky > 1% → 触发 P0 工单暂停 S10 |

### 9.2 回滚预案（参 TDD §17.3）

| 阶段 | 故障 | 回滚方式 | RTO |
|---|---|---|---|
| S0 | gateway Filter 链跑不起来 | 回退该 worktree 到 base commit | < 5 min |
| S1 | Flyway 迁移失败 | 不回滚 schema；只 patch 数据；Orchestrator 暂停所有依赖 Phase | < 30 min |
| S3 | AI 金标失败率 > 50% | 默认 provider 切到 mock；标 R-01；继续推进非 AI Phase | < 30s |
| S4 | OpenAPI diff 破坏现有契约 | BE-08 worktree 强制 git reset；FE-03 暂停 | < 10 min |
| S5 | review-plan IT 失败 > 47/47 | 立即触发 review；保留主体 47 个；新增的 12 个失败先跳过 | < 1h |
| S9 | Smoke E2E 失败率 > 10% | DevOps 起 baseline 环境复跑；若仍失败 → 触发 R-08 | < 30 min |
| S10 | Helm dry-run 失败 | DevOps worktree 直接 fix；不影响主流程 | < 30 min |

---

## 10. 强制等待 / Review Gate（本计划闭环）

### 10.1 当前 Gate · 等待用户 Review

> 本计划文档生成完毕。Orchestrator 现在**冻结**。
>
> 请 User 在以下三种回复中任选其一：
>
> 1. **`Review 通过 · 全量 kick off`** → Orchestrator 开始 §4 Worktree 创建 + S0 三个 Agent 并行派发
> 2. **`Review 通过 · 仅 kick off Sx`**（指定 Phase）→ Orchestrator 仅创建该 Phase 涉及的 worktree
> 3. **`修订: <具体改动>`**（局部修订）→ Orchestrator 在本文档原地修改后再次等待 Review

### 10.2 后续 Gate（每 Phase 出口门禁）

每个 Phase 走完，Orchestrator 出 `reports/phase-<id>-acceptance.md` 给 User，含：

- 该 Phase 出口门禁逐项核对（✅ / ❌）
- 失败项的处置建议（继续 / 阻断 / 降级）
- 下一 Phase 启动建议
- 风险登记

User 必须明确回 `Phase Sx 通过 · 启动 Sy` 才允许进入下游。

### 10.3 上线 Gate（S10 终极）

- TDD §17.5 18 项 Checklist 100% 绿
- QA Agent 16 SC × 78 用例 100% PASS
- DevOps Helm staging 灰度 5%→25%→100% 演练完成
- 法务 / DBA / 微信审核三方签字
- User 显式 `Approved for production rollout`

---

## 附录 A · Agent 派发模板（仅供 User Review 通过后参考）

### A.1 后端 Agent 派发模板

```markdown
[Orchestrator → Agent BE-XX-NAME]

Worktree: ~/build/longfeng-wrongbook-worktrees/be-XX-name/
Branch:   agent/be-XX-name (base: feature/s7-frontend-core)
Phase:    S<N>
RACI:     R=BE-XX, A=Orchestrator, C=Reviewer + DevOps, I=All

任务范围：
  - <对照 §5.S<N> 中该 Agent 的子任务>

必读文档：
  - design/arch/wrongbook-mvp-tech-design.md §<N>
  - design/落地计划/2026-05-02-MVP-AI-Agent-全链路落地计划.md §5.S<N>
  - design/tasks/preflight/s<N>-* (若有)

Files:
  - <对照 §5.S<N> Files 表>

测试交付（不可豁免）:
  - 单元测试覆盖 ≥ 60%
  - 集成测试 <itName1>, <itName2>
  - 开发级 E2E：reports/be-XX-name/dev-smoke.log

红线（Reviewer 静态扫）：
  - <对照 §1.3 与 §0.9 D-* 决策中相关条款>

出口门禁：
  - <对照 §5.S<N> 出口门禁表>

完成判据：
  1. push agent/be-XX-name 到远端
  2. 出 reports/be-XX-name/exit-gate.md（自核 ✅/❌ 表）
  3. 通知 Orchestrator request review
```

### A.2 前端 Agent 派发模板（**铁律内嵌 · §3.4**）

```markdown
[Orchestrator → Agent FE-XX-NAME]

Worktree: ~/build/longfeng-wrongbook-worktrees/fe-XX-name/
Branch:   agent/fe-XX-name (base: feature/s7-frontend-core)
Phase:    S<N>
RACI:     R=FE-XX, A=Orchestrator, C=Reviewer + QA, I=All

🚨 第 0 步（绝对不可跳过 · 早于一切其他动作）：
  1. 读项目根 CLAUDE.md「设计实施铁律」段（所有规则的唯一入口）
  2. 按 CLAUDE.md 路由进 design/system/GUIDANCE.md，照 §2 走 10 步流程
  3. 同步读 design/system/STYLE-TRUTH.md + DESIGN.md + pages/<ID>.spec.md
  4. 本派发单的所有具体规则（token / hex / 路径）若与 CLAUDE.md 冲突，以 CLAUDE.md 为准

🔒 第一步（先 mockup 后代码）：
  1. archive 已有页面（除 P00 外的 19 张）：
       优先 cp design/mockups/wrongbook/_archive/<NN>_*.html 作 1:1 参考
       不要凭空重画
  2. archive 缺失页面（目前仅 P00 login）：
       跳 GUIDANCE.md §4 场景 E + STYLE-TRUTH.md §6 缺失页指引
  3. 写 reports/fe-XX-name/ui-plan.md，含三类清单：
       - 视觉区块（按 DOM 顺序，B-01..B-NN）
       - 交互流程（F-01..F-NN）
       - 状态切换（S-01..S-NN，含默认/加载/错误/空/离线）
  4. commit 0 必须为 "ui-plan: <page>"（Reviewer 扫 git log 验证）
  5. 若已有 design/tasks/preflight/<Page>-build-spec.json：
       - 调用 /fe-preflight 校验或更新
       - token 找不到 → 触发 token-mapping-review，禁止硬编码

任务范围：
  - 实现页面：<page-id>（archive mockup: _archive/<NN>_*.html）
  - 严格 1:1 对齐 archive mockup + STYLE-TRUTH.md token（颜色 / 间距 / 字号 / 排版 / icon / 文案 / 点击 / 转场）
  - 差一个元素就不行（参 §3.4 IRON-LAW-2 判定表 + GUIDANCE.md §4 五类 fail 场景）
  - 严禁用已废 v1.0 token（如 --tkn-color-warm-* / --tkn-gradient-aurora）—— 完整迁移表见 GUIDANCE.md §4 场景 C

必读文档（严格按此顺序）：
  1. CLAUDE.md（项目根 · 设计实施铁律段）  ← 唯一入口
  2. design/system/GUIDANCE.md  ← 实施 runbook
  3. design/system/STYLE-TRUTH.md  ← token / hex 唯一真相
  4. design/system/DESIGN.md  ← 设计宪法 v2.0
  5. design/system/pages/<page-id>.spec.md  ← 单页规格（数据/状态/API/AC/testid）
  6. design/落地计划/2026-05-02-MVP-AI-Agent-全链路落地计划.md §3.4 + §5.S<N>  ← 编排约束
  7. design/mockups/wrongbook/_archive/<NN>_*.html  ← 视觉参考
  8. design/tasks/preflight/<Page>-build-spec.json（若已生成）
  9. packages/ui-kit/src/* + packages/design-tokens/src/tokens.ts
 10. design/analysis/s<N>-business-analysis.yml（AC + testid 锚点）

Files:
  - <对照 §5.S<N> Files 表>

测试交付（不可豁免 · 铁律 §3.4）:
  - 单元测试 ≥ 60%（Vitest）
  - 🔒 C 轨 视觉 diff：/fe-accept-diff <page> · pixel diff ≤ 1%
        产出 reports/fe-XX-name/c-track-diff/<page>.png（expected/actual/diff 三联）
  - 🔒 B 轨 Mock e2e：/fe-accept-mock <page> · 全部 TC PASS
        产出 reports/fe-XX-name/b-track-mock.log + screenshots/
  - 🔒 元素清单核对：reports/fe-XX-name/element-checklist.md（每行必 ✅）

红线（Reviewer 静态扫 + 出口门禁 §5.S<N> + §3.4 IRON-LAW 五项）：
  - 无硬编码颜色 / 间距 / 字号（rg 扫 0 命中）
  - 无 inline SVG（必须走 ui-kit Icon 注册）
  - 全 testid 进 packages/testids/src/index.ts 注册表
  - i18n key 全部 ⊆ zh-CN base
  - aria 属性齐全（axe-core 0 serious）

🔒 循环不停规则（§3.4 IRON-LAW-4）：
  - C 轨 / B 轨 / 元素清单任意一项 fail → 立即进入 /fe-repair 修复循环
  - 修完重跑 C/B 两轨，仍 fail 再次循环
  - 连续 3 次循环仍 fail → 升级 User，禁止 "已知 gap 继续"
  - 自测未全绿 → 禁止通知 Orchestrator 完成

完成判据：
  1. ui-plan.md / element-checklist.md / c-track-diff/ / b-track-mock.log 全产出
  2. push agent/fe-XX-name 到远端
  3. 出 reports/fe-XX-name/exit-gate.md（自核 ✅/❌ 表 · 含 §3.4 五项）
  4. 通知 Orchestrator request review；Orchestrator 复核 §3.4 五项后才召 Reviewer
```

### A.3 改 BUG 派发模板（FE 适用 · 铁律同样生效）

```markdown
[Orchestrator → Agent FE-XX-NAME · BUGFIX]

Worktree: ~/build/longfeng-wrongbook-worktrees/fe-XX-bugfix-<bugid>/
Bug: <bug-id> · <一句话描述>
Affected page(s): <page-id> (mockup: <NN>_<page>.html)

🚨 改 BUG 第 0 步（不可跳过）：
  1. 读项目根 CLAUDE.md「设计实施铁律」段
  2. 按 CLAUDE.md 路由：
       - 视觉 / token / 设计 bug → GUIDANCE.md §4 五类 fail 场景排查
       - 行为 / 数据 / 交互 bug → pages/<ID>.spec.md §8 AC + testid 验证
  3. 任何修复方案与 CLAUDE.md / STYLE-TRUTH 冲突，以 CLAUDE.md 为准

🔒 改 BUG 流程（§3.4 铁律同样适用）：
  1. 打开 archive mockup design/mockups/wrongbook/_archive/<NN>_*.html，
     对照当前实现 + STYLE-TRUTH.md token 定位 gap 类型：
       - M = Mockup 偏差（DOM 结构 / 交互流程缺）
       - V = Visual token 偏差（颜色 hex / 间距 px / 字号偏离 STYLE-TRUTH）
       - B = Business 行为偏差（点击响应 / 状态切换 / 数据展示）
  2. 写 reports/fe-XX-bugfix-<bugid>/repro.md：
       - 复现步骤（含具体测试数据 / API mock 状态）
       - 截图 expected (archive mockup) vs actual (current build)
       - 分类（M/V/B）+ 影响范围
       - 引用的 STYLE-TRUTH.md / spec.md 章节锚点
  3. 先写失败测试（Vitest 单测 或 Playwright e2e），证明 bug 存在
  4. 修复实现（如涉及 token 替换，参 GUIDANCE.md §4 场景 C 迁移表）
  5. 必跑 /fe-accept-diff + /fe-accept-mock，必须 PASS
  6. 全页回归（不只改动区域）：再跑一遍当前页面所有 TC

不允许：
  - ❌ "目测对了" 替代 pixel diff
  - ❌ 只改 visual 不补测试
  - ❌ 修一个 BUG 引入另一个 BUG（B 轨全绿才算修完）
  - ❌ 跳过 mockup 对比直接 patch CSS
  - ❌ 用已废 v1.0 token 修 BUG（必须迁到 v2.0 五类 mood + STYLE-TRUTH token）
  - ❌ 不读 CLAUDE.md 直接动手改

完成判据：
  - C 轨 pixel diff 该页面 ≤ 1%（修前 fail · 修后 PASS · 截图对比留档）
  - B 轨 该页面全部 TC PASS（含修复用例 + 回归用例）
  - 出 reports/fe-XX-bugfix-<bugid>/exit-gate.md
```

---

## 附录 B · 文档位置约定

| 文件 | 路径 | 作用 |
|---|---|---|
| 本计划 | `design/落地计划/2026-05-02-MVP-AI-Agent-全链路落地计划.md` | 总编排文档 |
| 决策注册表 | `0_shared_context/decisions-register.md` | 从 TDD §0.9 抽取，每 Agent 必读 |
| 红线清单 | `0_shared_context/red-lines.md` | C1..C10，Reviewer 静态扫脚本输入 |
| Phase 验收报告 | `reports/phase-<id>-acceptance.md` | Orchestrator 每 Phase 末尾产出 |
| Agent 出口报告 | `reports/<agent-id>/exit-gate.md` | Builder Agent 完成后自核 |
| QA SC 报告 | `reports/<sprint>/sc-<id>-acceptance.md` | QA Agent 每 SC 跑完产出 |
| 上线 Checklist | `reports/release-r1-checklist.md` | S10 终极 18 项 |

---

**🛑 本文档输出完毕。Orchestrator 现在冻结，等待 User Review 指令。**
