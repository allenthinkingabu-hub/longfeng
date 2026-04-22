# AI 上下文与连贯性设计（Context & Continuity Contract）

> 版本：**v1.0 · 2026-04-22**
> 定位：**《落地实施计划 v1.3》的补丁提案** — 补齐 v1.3 未处理的"Agent 怎么活、怎么死、怎么接班"这一维度。
> 适用范围：本设计拟作为 v1.4 的 §1.8 章节并入 `落地实施计划_v1.0_AI自动执行.md`；在并入之前，可作为独立设计稿单独评审。
> 上游依赖：v1.3 的 §1.5 Phase 结构契约 · §1.6 工具白名单 · §1.7 Design Gate。

---

## 0. 动机：三个正交轴纠缠导致上下文污染

v1.3 已经通过 Design Gate 把"AI 建什么、为什么这么建"固化到 Git（`design/arch/<phase-id>.md`），但仍未回答：

- 一个 Phase 有 10+ 子任务、关键路径 10 级、跨 Phase 依赖最长 10 级；
- 单个 Agent 会话连续跑完必然出现 context 污染、连贯性丢失；
- 更糟的是："任务状态"与"跨阶段依赖"通常被隐式塞在 context 里，一旦 context 被截断或污染，状态就**不可逆地丢失**。

问题的根源是把三件不同的事纠缠在一起：

| 轴 | 性质 | 正确归宿 | 错误归宿 |
|---|---|---|---|
| **State（状态）** | 任务做到哪一步 —— 是**事实** | Git 下的 `state/*.yml`（机器可读） | context window（会丢） |
| **Dependency（依赖）** | 我需要上游交付了什么 —— 是**契约** | OpenAPI / JSON Schema / `design/arch/*.md` + `state/interfaces.yml` | "你记得上个 Phase 说过 XX 吗" |
| **Working Context（推理上下文）** | 我现在在想什么 —— 是**临时的** | Agent 当前会话的 scratch | 不该塞前两个 |

把状态和依赖放进 context 就是现在"上下文污染 + 任务连贯性破坏"的病灶。

---

## 1. 核心设计原则："文件是真相，上下文是缓存"

**一句话**：任何 Agent 随时可被 kill 重启，重启后仅从 Git 读 state 就能接着干，**不需要记住**任何之前聊过的东西。

该原则强制四件事：

1. 三层上下文职责隔离（L0 只读 / L1 Phase 契约 / L2 Task 工作区）
2. 任务状态完全外化到 Git（`state/phase-<id>.yml`）
3. 跨 Phase 依赖走契约文件（`state/interfaces.yml`）而非对话记忆
4. Agent 显式生命周期管理（冷启动协议 + reset 协议 + handoff 文件）

---

## 2. 分层上下文（三层不混用）

| 层 | 生命周期 | 存储位置 | 可变性 | Agent 使用规则 |
|---|---|---|---|---|
| **L0 · 项目不变量** | 贯穿全生命周期 | `design/业务与技术解决方案.md` · `落地实施计划.md` · `docs/adr/*` | 冻结（改必走 ADR） | **只读锚点**，不整段塞 context，按章节号按需 Read |
| **L1 · Phase 契约** | 当前 Phase | Phase 章节（11 字段） · `design/arch/<phase-id>.md` · `state/phase-<id>.yml` | 本 Phase 内可增不可删 | Agent 启动必读；本 Phase 内稳定存在 |
| **L2 · Task 工作区** | 当前 task 的一次 Agent 调用 | `state/task-<phase>-<task>.yml` · 临时 scratch | 频繁变更 | 超预算即丢弃并 reset |

**硬约束**：

- L2 过期即丢；
- L1 永远存盘；
- L0 只读不写；
- **禁止把其他 Phase 的 L1 拉进当前 context**。跨 Phase 的一切必须走"契约文件"而非"对话记忆"。

---

## 3. 任务状态外化（每个 Phase 一个 state.yml）

### 3.1 Schema

`state/phase-<id>.yml`：

```yaml
phase_id: s4
run_id: 20260422-093011
updated_at: 2026-04-22T09:45:12Z
upstream_tags_verified: [s0-done, s1-done, s2-done, s3-done]
design_gate:
  biz_gate: approved          # §1.7 G-Biz
  arch_gate: approved         # §1.7 G-Arch
tasks:
  - id: s4-t01-scaffold-module
    status: done               # pending | in_progress | blocked | done | failed
    inputs_hash: sha256:ab12...
    outputs:
      - services/ai-analysis/pom.xml
      - services/ai-analysis/src/main/java/.../AnalysisApplication.java
    outputs_hash: sha256:cd34...
    finished_at: 2026-04-22T09:12:33Z
  - id: s4-t02-dashscope-client
    status: in_progress
    started_at: 2026-04-22T09:40:01Z
    attempt: 2
    last_error: "RateLimiter not configured (Step 9)"
    next_step: step-09-resilience-config
  - id: s4-t03-openai-fallback
    status: blocked
    blocked_by: [s4-t02]
  - id: s4-t04-chatclient-integration
    status: pending
    blocked_by: [s4-t02, s4-t03]
```

### 3.2 硬约束

- 每个 Phase 的 Step 1 必须是 `state-init.sh <phase-id>` 从章节生成初始 `state/phase-<id>.yml`；
- 任何 Agent 启动的**第一件事**是 `cat state/phase-<id>.yml`，而非"回忆上次聊到哪了"；
- `state/` 目录 **必须 commit 进 Git**；不是 `.gitignore`；
- 更新只能走 `state-advance.sh`（原子写 + 时间戳 + run_id 追加），禁止 Agent 直接 `echo >>`。

---

## 4. 跨阶段依赖契约化（`state/interfaces.yml`）

v1.3 已经有 `design/arch/<phase-id>.md` 作为本 Phase 符号真源。现在再加一层：

### 4.1 上游以"文件名 + 哈希 + 符号"发布交付

每个 Phase 完成时，除了 `git tag sX-done`，还要 append `state/interfaces.yml`：

```yaml
interfaces:
  - provided_by: s3
    kind: openapi
    path: services/wrongbook/src/main/resources/openapi.yaml
    sha256: 0x7f1c...
    symbols: [WrongItem, WrongItemCreateReq, "POST /api/v1/wrong-items"]
    frozen_at: 2026-04-21T18:03:22Z

  - provided_by: s3
    kind: arch-doc
    path: design/arch/s3.md
    sha256: 0x9ab2...
    gate_status: approved

  - provided_by: s1
    kind: ddl
    path: services/common/src/main/resources/db/migration/V1__core.sql
    sha256: 0x8e3d...
    symbols: [wrong_item, wrong_item_tag, tag_taxonomy]
```

### 4.2 下游 DoR 必须列出依赖 ID

下游 Phase 的 **DoR（§1.5 字段 3）** 必须显式列出：

```yaml
dor_depends_on:
  - id: interface://s3/openapi#WrongItem
    sha256: 0x7f1c...
  - id: interface://s1/ddl#wrong_item
    sha256: 0x8e3d...
```

### 4.3 Planner 启动时强制校验

- `sha256sum` 实际文件 == `interfaces.yml` 声明值 → 不一致即停机；
- 所有声明依赖对应的 interface 条目必须存在；
- **Agent 永远不应该凭记忆"我记得 S3 那个字段叫 errorType"**——凭记忆即漂移起点，必须 grep Git 才算数。

---

## 5. Agent 生命周期管理

### 5.1 Reset 触发边界（任一命中即强制 handoff + 重启）

| 触发条件 | 动作 |
|---|---|
| tool_call 次数 > 80 **或** token 使用 > 60% 上下文窗口 | **Compact**：写 `state/task-XX.yml` 的 `next_step` + `scratch_summary.md`，关闭当前 Agent |
| 同一 task 重试 ≥ 3 次仍失败 | **Escalate**：写 `logs/phase-sX-<run>.md` 失败现场，升级 Planner；禁止继续乱试 |
| 跨过 Phase 边界 | **Handoff**：当前 Agent 只打 tag、不进下一 Phase；下一 Phase 由全新 Agent 从 `state.yml` 冷启动 |

### 5.2 冷启动协议（新 Agent 开工的前 5 个动作，顺序不得颠倒）

1. `Read` 本 Phase 章节 11 字段（自包含配方）
2. `Read` `design/arch/<phase-id>.md`（本 Phase 符号真源）
3. `Read` `state/phase-<id>.yml`（进度）
4. `Read` `state/interfaces.yml` 里**本 Phase DoR 声明依赖**的条目
5. 若存在 `state/scratch_summary_<phase>_<task>.md`（上次被 reset 写下的）→ 读入当前 task 的 `next_step`，继续

其余一律不读。这样无论聊多久，L1 + L2 加起来都是有界的。

### 5.3 handoff 文件 schema

`state/scratch_summary_<phase>_<task>.md`：

```markdown
---
phase_id: s4
task_id: s4-t02-dashscope-client
run_id: 20260422-093011
written_at: 2026-04-22T09:58:11Z
reason: tool_call_quota_exceeded  # | token_budget_exceeded | manual_handoff
next_step: step-09-resilience-config
---

## 我做到哪了

- Step 1–8 已完成 · DashscopeChatClient 已落地 · 单测 DashscopeChatClientTest 全绿（sha256:...）
- Step 9 进行中：Resilience4j CircuitBreaker 配置写了一半，卡在 RateLimiter 的 limitForPeriod 单位问题

## 关键临时发现（需下一位 Agent 感知）

- pom 里 resilience4j-spring-boot3 必须与 spring-boot 3.2.5 对齐，试过 2.3.0 跑不起来
- application.yml 的 resilience4j.ratelimiter.instances.llm 命名必须与 @CircuitBreaker 注解 name 一致

## 下一步从这里继续

打开 services/ai-analysis/src/main/java/com/longfeng/.../ResilienceConfig.java，
按 §4.Step 9 的配方补齐 RateLimiter（QPS 10 / provider），
跑 `mvn -q test -Dtest=ResilienceConfigTest` 验证。
```

**关键**：

- 只写"下一步从哪里继续"所需的最小集合；
- 禁止把已经完成的 Step 1–8 内容粘进来（那是 L2 残渣）；
- 禁止把其他 Phase 的背景拉进来（那是越权）。

---

## 6. 父子 Agent 分工（把"记状态"和"做事"解耦）

v1.3 已经有 Planner / Builder / Reviewer / Verifier。记忆职责需要更明确：

| Agent | 持有什么 | 不持有什么 | 生命周期 |
|---|---|---|---|
| **Planner** | DAG 状态 · Phase 转换 · 跨 Phase 依赖图 | 具体代码细节 | 长（跨多 Phase） |
| **Builder** | 本 task 的 11 字段 + L2 scratch | 其他 Phase 的任何东西 | 单 task（一次调用即销毁） |
| **Reviewer** | 待审 diff + Phase 契约 + allowlist | Builder 的推理过程 | 单 PR |
| **Verifier** | DoD 断言脚本 + 产出路径 | Builder 的推理过程 | 单次验收跑 |

### 关键语义

**Builder 的每次调用都是"一次性"的**：
`读 state → 做下一步 → 写 state → 退出`

即使同一 Phase 有 15 个 task，也**不共享同一个 Builder 会话**。这样即使某次会话污染严重，污染也**不会跨 task 传递**——因为下一个 task 根本不在同一个 context 里。

---

## 7. 两个实操技巧（与 v1.3 低侵入叠加）

### 技巧 A · 把"任务依赖"也做成 DAG 写进 state，别靠 Agent 在 prompt 里推

Planner 只做一件事：

```
loop:
  读 state/phase-<id>.yml
  找到所有 task where status==pending AND blocked_by.all(status==done)
  逐个发给空白 Builder 实例（每个 task 一个新会话）
  收到对应 Builder 打的子 tag / state 更新
  更新 state → 重新进入 loop
  直到所有 task done → 打 phase tag sX-done
```

这样 **Planner 自己也不需要长记忆**：它每轮循环都能从 `state.yml` 读出全部所需信息。Planner 被 kill 重启也不影响 —— 重启后下一轮 loop 依然能找到同一批可执行 task。

### 技巧 B · 日志严格分两种，对 Agent 可见性不同

| 日志 | 路径 | 写入频率 | 是否读回 context | 用途 |
|---|---|---|---|---|
| **原始审计** | `docs/agent-audit/SX-YYYY-MM-DD.jsonl` | 每次 tool call | ❌ **永远不读回** | 事后人类审计 / 离线分析 |
| **失败现场** | `logs/phase-sX-<run>.md` | 仅在 task 失败时 | ✅ 下次冷启动时读 | 下一次 Agent 能看到"上次为什么卡住" |

区分这两类可以避免把巨大的 audit log 喂回 context 污染 L2。**Agent 看到"日志"这个词时必须追问是哪一种**。

---

## 8. 可以直接作为 v1.4 §1.8 章节并入的骨架

> 下面这段建议原样插入《落地实施计划 v1.0_AI 自动执行.md》的 §1.7 之后，成为 §1.8。

**§1.8 Context & Continuity Contract（上下文与连贯性契约）**

动机：v1.3 的 §1.5 / §1.6 / §1.7 解决了"Agent 建什么 / 用什么工具 / 签不签字"，但未处理"Agent 怎么活、怎么死、怎么接班"。Phase 执行步骤常含 10+ 子任务，跨 Phase 依赖链最长 10 级，在单一 Agent 会话里连续跑完必然出现 context 污染与连贯性丢失。本节把**状态 / 依赖 / 推理**三者显式解耦，把状态外化到 Git，使任意 Agent 实例可随时冷启动接班。

- **规则 A · 三层上下文职责隔离**（L0 只读 / L1 Phase 契约 / L2 Task 工作区）
- **规则 B · 每个 Phase 必须维护** `state/phase-<id>.yml`，记录 `upstream_tags_verified` / `design_gate` / `tasks[*].status`
- **规则 C · 跨 Phase 依赖必须落地为** `state/interfaces.yml` 条目（`path` + `sha256` + `symbols`），下游 DoR 列出依赖 ID
- **规则 D · Agent 冷启动协议**（5 步读入序，§5.2）
- **规则 E · Reset 触发边界**（tool_call / token / 重试次数）与 handoff 文件 `scratch_summary_<phase>_<task>.md`
- **规则 F · Builder 无跨 task 记忆**；所有"我刚才做过 X"必须 grep Git 才算数

配套脚本（S0 落地）：

| 脚本 | 用途 |
|---|---|
| `ops/scripts/state-init.sh <phase-id>` | 从 Phase 章节生成初始 `state/phase-<id>.yml` |
| `ops/scripts/state-advance.sh <phase-id> <task-id> <status>` | 原子更新 task 状态 |
| `ops/scripts/check-continuity.sh <phase-id>` | 校验：done 任务的 outputs 存在且哈希匹配 · `blocked_by` 指向存在的 task_id · `upstream_tags` 确实已打 |
| `ops/scripts/handoff.sh <phase-id> <task-id> <reason>` | Agent 自觉 reset 时调用，写 `scratch_summary` + 关会话 |
| `ops/scripts/cold-start.sh <phase-id>` | 新 Agent 冷启动 5 步读入的包装器，打印出应读文件清单 |

硬门禁：

- S0 DoR 必须新增："`state/`、`docs/agent-audit/`、`logs/` 三个目录已建、已 commit、已写 `.gitattributes` 确保 YAML 不被 CRLF 污染"
- 每个后续 Phase DoD 必须新增：`bash ops/scripts/check-continuity.sh <phase-id>` 返回 0
- Playbook（§25）第 3 条"逐阶段执行 S0→S11"必须在每阶段开始前追加**冷启动 5 步读入**

---

## 9. 为什么这样设计有效

一句话：**让"Agent 能不能记住"变成一个不重要的问题——因为它不需要记住**。

具体地：

- **状态污染被隔离**：单次 Builder 会话即使污染，下一个 task 换新会话，污染不跨 task 传递；
- **依赖漂移被切断**：跨 Phase 用契约文件 + sha256 校验，Agent 无法凭记忆"编一个"；
- **失败可恢复**：任何中断都能通过 `state.yml` + `scratch_summary.md` 在新 Agent 上精确续接；
- **对 v1.3 低侵入**：不改 §1.5 的 11 字段，只在 DoR / DoD 各加一条断言；不改 §1.6 / §1.7；仅在 §1 末追加 §1.8 和 5 个脚本。

---

## 10. 落地顺序建议

1. **先并入文档**：把本文件 §8 骨架写进主计划 §1.8 · 主计划版本号 v1.3 → v1.4
2. **再落地脚本**：在 S0 Phase 的执行步骤里补 5 个脚本的生成 · 把 `state/` 目录与 `.gitattributes` 加入 S0 产出清单
3. **然后改 DoR/DoD**：给 S1..S11 + Sd 每个 Phase 追加两条硬门禁（`state-init.sh` 作为 Step 1 · `check-continuity.sh` 作为 DoD 最后一条）
4. **最后改 §25 Playbook**：在第 3 条"逐阶段执行"前插入"冷启动 5 步读入"

---

> **文档版本**：v1.0 · 2026-04-22
> **作者**：AI Agent 协作起草 · User (Allen) 签署
> **下一步**：评审通过后，将 §8 骨架并入 `落地实施计划_v1.0_AI自动执行.md` 成为 v1.4 的 §1.8。
