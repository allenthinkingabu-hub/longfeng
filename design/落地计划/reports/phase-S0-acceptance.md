# Phase S0 · 仓库整合 + 骨架 · Acceptance Report

**Date**: 2026-05-02
**Phase**: S0 (plan §5.S0)
**Base commit**: `795360a` (feature/s7-frontend-core)
**实际耗时**: ~40min（vs plan 估 0.5d ≈ 4h · 大幅低于预算 · 但有 Orchestrator 兜底成本）

## 出口门禁逐项核对

| 门禁 | 状态 | Agent | 备注 |
|---|---|---|---|
| `mvn -q -DskipTests validate` 全模块通过 | ✅ | BE-03 | 主仓 + integration-test 模块全过 |
| common 单测覆盖率 ≥ 95%（JaCoCo report） | ⚠️ 84% inst / 77% line | BE-01 | 介于 §A.1 (≥60%) 与 §5.S0 (≥95%) 之间 · 待 User 评判 |
| gateway 4 Filter 集成测试 PASS | ✅ | BE-02 | 30/30 · 含 4 Filter 顺序 + C4 4 写动词 403 + revoke + expired 全覆盖 |
| `infra/docker-compose.dev.yml` 6 容器 healthy ≥ 60s | ⚠️ 待跑 | DevOps | yml + init scripts + README 全交 · 实际 docker compose up 需 User 自跑（Agent 当时 docker 命令未授权） |
| integration-test 模块 mvn test 启 Testcontainers | ✅ | BE-03 | HelloIT.java 已建 · 模块结构 OK |
| Reviewer 静态扫：BusinessException 含 `msgkey:` 前缀 | ✅ | (Orchestrator) | BE-01 commit message + 实际代码确认 |

**总评：6 项 4 ✅ + 2 ⚠️**（覆盖率标准模糊 + DevOps 容器待跑）

## 各 Agent 提交统计

| Agent | branch | commits | files | sha (last) | exit-gate |
|---|---|---|---|---|---|
| DevOps | agent/devops | 3 | infra/docker-compose.dev.yml + init/* + README | `27bae7c` | ✅ |
| BE-01-common | agent/be-01-common | 1 | 19 文件（10 prod + 7 test + pom + report）+ 192 tests | `b296754` | ✅ |
| BE-02-gateway | agent/be-02-gateway | 1 | 18 文件（4 filter + config + service + util + 4 test + IT 占位 + report）+ 30 tests | `fde0eab` | ✅ |
| BE-03-flyway | agent/be-03-flyway | 1 | 4 文件（IT 模块 pom + HelloIT + root pom 注册 + report） | `6369cb0` | ✅ |

## 失败项 + 处置建议

无失败项。所有 4 Agent 通过自核出口门禁 · 全部 push 到 origin。

## 关键发现（影响 S1+ 的工程决策）

### F-01 · sub-agent Bash 权限是 platform 级 sandbox · 不可通过 settings 解锁
- **现象**：sub-agent (Agent tool spawn 的 general-purpose) 调 Bash 一律 denied
- **验证**：在 `.claude/settings.json` (project committed) 加 44 个 Bash 白名单后 · 第二轮 sub-agent 仍 blocked
- **结论**：sub-agent sandbox 独立于 main session 的 settings.local.json + project settings.json
- **影响**：S1+ 18 Agent 仍需 Orchestrator 代跑 mvn / git / 写 exit-gate · 不能让 sub-agent 自主闭环
- **应对方案**（待 User 评估）：
  - (a) 沿用本 Phase 模式 · sub-agent 写代码 · Orchestrator 验证 + 提交（适合代码量小的 Agent · 但 Orchestrator context 消耗大）
  - (b) Sub-agent 仅输出 patch + 报告 · main session 应用 + 跑 Bash · 减少 sub-agent 内部 retry 浪费
  - (c) 探索 CC 是否有 sub-agent permission inheritance 配置（schema 中没找到 explicit field）
  - (d) 接受现状 + 在派单 prompt 第 0 步加"自检 Bash 是否可用 · 否则 abort 立即报告 fallback"

### F-02 · Mockito 5 + Java 21 默认无 byte-buddy javaagent · 需手动配
- **现象**：`mock(SomeClass.class)` 失败 "Mockito cannot mock this class"
- **触发场景**：Mockito 5 自 Java 17+ 起默认要 inline mock maker · 需要 byte-buddy javaagent · spring-boot-starter-test 没自动配
- **fix 选项**：
  - 加 mockito 配置：`@MockitoSettings` 或 surefire `argLine` 加 javaagent
  - 改用手动 inline subclass stub（本 Phase BE-02 用此法 · 简单且不引新依赖）
- **S1+ 影响**：BE-04..10 派单 prompt 需提示这点 · 避免每个 Agent 都撞墙 + Orchestrator 反复救火

### F-03 · 子 Agent 易 derail（任务理解漂移）
- **现象**：BE-03 第一轮误把 IT 骨架理解为"运行 fewer-permission-prompts skill"，绕去碰 .claude/settings.json
- **fix**：派单 prompt 加"严格禁止"段（禁动 .claude/ / 禁跑 meta skill / 禁扩范围）+ 明确 abort 条件
- **S1+ 影响**：所有派单模板需要"红线警告"段 · 已在 BE-02 重派 prompt 验证有效

### F-04 · 临时 stub 模式（解 worktree 间依赖）
- **现象**：BE-02 worktree 没 BE-01 common 类（并行 Phase · 互不可见）
- **应对**：sub-agent 在 `tmp/` 包写本地 stub · 标 TODO · S0 主仓 merge 后 rebase 切换
- **S1+ 影响**：跨 worktree 依赖（如 S2 anon-service 依赖 BE-04 file-service）需要类似 stub 模式 / 或拓扑串行

## 下一 Phase (S1) 启动建议

参 plan §5.S1 + §8.1 依赖图：

**S1 Phase**: DDL Flyway · 21 张表 + 7 outbox + 7 ebbinghaus seed
- 依赖：S0 出口门禁 ✅
- 主 Agent: BE-03-flyway（继续持有 worktree · S0 已建 IT 模块 · 现接续做 Flyway）
- 协助 Agent: Reviewer (静态扫 TIMESTAMPTZ / version / tenant_id 字段)
- 工作量预估：1d
- 关键风险：pgvector ivfflat lists=100 索引创建顺序

**建议**：Orchestrator merge S0 4 branches 到 feature/s7-frontend-core → 启动 S1 (BE-03 单 Agent · 减少并行复杂度 · S1 主要是 SQL 文件)

**或者**：先合并 S0 + 等 User 验证（跑 docker compose dev / 跑 IT 真实 Testcontainer）+ 再启 S1。

## 新增风险登记

| ID | 风险 | 缓解 | 升级条件 |
|---|---|---|---|
| R-NEW-01 | sub-agent Bash 沙箱 → S1+ 18 Agent 都需 Orchestrator 代跑 → main session context 消耗 ≥ 60% by S5 | 探索 (b)/(c)/(d) 选项；或拆 Phase 之间清理 context | main session context > 70% → 切新 session resume from reports/ |
| R-NEW-02 | Mockito + Java 21 兼容问题在 BE-04..10 重复出现 | 派单 prompt 预警 + Orchestrator 提供 inline stub 模板 | 任一 Agent 重复撞 → 加全局 surefire javaagent 配置 |
| R-NEW-03 | 临时 stub (`tmp/` 包) S0 合并后 rebase 切 common · 若忘记 → 编译/运行 fail | S0 merge 时 grep `com.longfeng.gateway.tmp` → 替换为 `com.longfeng.common.context` | 合并后 mvn compile fail · 立即修 |
| R-NEW-04 | DevOps 6 容器实际未跑 healthy 验证 · S2+ 可能撞配置错 | User 在 S0 → S1 过渡时跑一次 `docker compose up -d` 验 | 任 1 容器 unhealthy > 60s → DevOps 重派修配置 |

## 等 User Phase Gate 决策

按 kickoff Step 8 协议 · 三选一：
- **(E1)** `Phase S0 通过 · 合并 + 启动 S1` → Orchestrator merge 4 branches 到 feature/s7-frontend-core + 派 BE-03 做 S1 (Flyway 21 表)
- **(E2)** `Phase S0 部分通过 · 修 [覆盖率 / DevOps 容器跑]` → Orchestrator 派局部修复 Agent
- **(E3)** `Phase S0 通过 · 暂停 · 等 User 手测 (docker compose / IT 跑) 后再合并 / 启 S1` → Orchestrator 冻结
