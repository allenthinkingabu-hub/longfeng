---
phase_id: s1
retrofit_target_version: v1.8+v1.9
plan_schema: 1.0
generated_by: builder-agent
generated_at: 2026-04-23T17:10:00+08:00
signed_by: "@allen"
approved_at: "2026-04-23T17:20:00+08:00"
approval_method: "direct-reply"    # User 2026-04-23 直接回复"同意" · 全量默认方案
approval_decisions:
  - { item: "§五 #1 精简 yml 结构", decision: "(a) 加 supports_sc 新字段", reason: "User 默认方案" }
  - { item: "§五 #2 AC 命名",        decision: "S1.DDL-AC-1..8",          reason: "User 默认方案" }
  - { item: "§五 #3 脚本 B 级识别 bug", decision: "本 Retrofit 不修",      reason: "超 S1 范围 · 留 TODO" }
  - { item: "§五 #4 V-S1-20",         decision: "新增",                    reason: "给 tag 加合规硬证据" }
s1_done_commit: 06113ea            # s1-done tag @ 2026-04-22 · feat(s1): 21 tables via Flyway + pgvector 1024-dim + idempotency IT
base_commit: 94eaba9               # 当前 HEAD · retrofit-to-v18.sh 扫描基准
---

# S1 Retrofit Plan · 按 §26 六步 + §1.5 B 级适用性 + §5.1-5.11 原始清单

> **本 plan 是 §26.1 Step 3 产物。** User 审签后（填顶部 `signed_by` + `approved_at`）Step 4 才可开工；未签字 Builder Agent 禁止动任何仓库文件。
>
> **执行范围声明**（feedback memory 层 1 · 禁止隐性收窄）：
> - 本 Plan 覆盖主执行档 §5 Phase S1 的全部条款：§5.1 业务理解 · §5.2 架构豁免 · §5.3-5.7 产出物 + 执行步骤 · §5.8 V-S1-01..09 **九条**原始业务闸 · §5.9 DoD-S1-01..10 **十条** DoD · §5.10 失败回滚 · §5.11 git tag。
> - 本 Plan 覆盖主执行档 §26.1 六步（arch 重写 / analysis schema / plan 审 / builder patch / verifier 复写 / V-SX-20 闸）· §26.2 四豁免条款逐条判定适用性 · §26.3 Batch A/B 并行约束。
> - 本 Plan 覆盖主执行档 §1.5 B 级适用性对 S1 的三项强制：0.0.5 精简 business-analysis.yml · 0.1 业务理解 · 0.2 豁免（DDL 即契约）· 0.7 AC 分行硬约束"选"。
> - **显式豁免列表**（§三总表）：凡因 B 级或 §26.2 而不做的事 · 逐条对照原生 v1.8 要求写入 · 不隐藏。

## 一、S1 特殊性：B 级 · 介于 S0 纯豁免和 S3/S4/S5 全量之间

### 1.1 §1.5 B 级条款对 S1 的逐维判定

| Phase 结构项 | S1 B 级要求（§1.5 表） | 当前状态 | Retrofit 动作 |
|---|---|---|---|
| **0.0.5 业务分析 YAML** | **强制** · 精简版（仅 SC 级映射 · 无 four_role_slots · DDL 即 AC） | ❌ 未产 `design/analysis/s1-business-analysis.yml` | **Step 2 必做**（Commit B） |
| **0.1 业务理解** | **强制** | ✅ §5.1 已完成（A1-A8 假设 · Q1-Q2 已 User 答复 · arch.md front matter 有 biz_gate: approved + special_requirements） | 无需动 |
| **0.2 架构设计** | **豁免**（B 级 · DDL 即数据架构文档 · arch.md 保持 `exempt: true` 骨架） | ✅ `design/arch/s1-data.md` front matter 有 `exempt: true` + rich biz 签字信息 | **微调**（§1.7 规则 F 补三项 N/A 列表 · Commit A） |
| **0.7 AC 分行硬约束** | **选**（S1 可豁免 @CoversAC 注解 + commit 前缀 + arch 按 AC 分节） | N/A（无 @CoversAC 注解 · commit 无 `[SC-XX-ACY]` 前缀 · arch 未按 AC 分节） | **整体走豁免**（§三豁免条款对照表第 5 行） |

### 1.2 retrofit-to-v18.sh 对 S1 的**误判**（必须手工纠偏）

```
[retrofit] phase=s1 analysis draft → reports/retrofit/s1-analysis.md
[retrofit] arch_compliant=true · analysis_compliant=true · is_exempt=true
```

脚本把 S1 识别为 **C 级全豁免**（`is_exempt=true` 直接跳过 schema 校验）· 原因：脚本仅判断 arch front matter 是否有 `exempt: true` · 未区分 B/C 级语义。S1 arch 的 `exempt: true` 只覆盖 0.2 架构豁免 · 不覆盖 0.0.5 精简 YAML 的强制要求。

**本 plan 的核心价值**：纠正这个误判 · 坚持 S1 按 B 级真实要求补精简 business-analysis.yml · 不让脚本的一刀切"闭着眼睛放过 S1"（此处即 feedback memory 层 1 警告的"隐性收窄"典型场景）。脚本本身的 B 级识别缺陷 → 记为**非阻塞文档问题**（§五 §3）· 留给后续主文档迭代修复（`retrofit-to-v18.sh` 加 B 级特判 · 或本脚本读 §1.5 适用性分级表）。

## 二、六步映射（S1 B 级特化版）

### Step 1 · `design/arch/s1-data.md` diff 摘要（保留 + 微调）

**保留现状**（front matter 已有业务内容 · 不可丢）：
- `exempt: true` + `exempt_reason` + `biz_gate: approved` + `biz_approved_by/at` + `gate_status: approved` + `sources`（方案 §4 / 落地计划 §5.1/5.6/5.7 / 决策备忘 §4 ADR 0006）
- `special_requirements`：DDL source authority · Q1 PostgreSQL session TIMEZONE · Q2 pg_trgm · A2 主键策略 · A1 A3-A8

**补 §1.7 规则 F 豁免模板三行列表**（Reviewer Agent 机读 · S0 同款格式）：

```markdown
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
  - business: "业务与技术解决方案_AI错题本_基于日历系统.md §4 数据库设计..."
  - design: "落地实施计划_v1.0_AI自动执行.md §5.1 / §5.6 / §5.7"
  - decision_memo: "Sd设计阶段_决策备忘_v1.0.md §4 Code-as-Design · ADR 0006 JPA over MyBatis"
special_requirements:
  - (保留 5 条 Q1/Q2/A1-A8 原封不动)
---

本 Phase 豁免 Design Gate · 0.2 架构设计因 B 级适用性（DDL 即数据架构文档）自动 approved。

- 业务理解：✅ 已完成（§5.1 · A1-A8 假设 User 批准 · Q1 Q2 答复归档 · 见 front matter special_requirements）
- 架构设计：N/A · DDL 即契约 · 权威符号来源为 `backend/common/src/main/resources/db/migration/` 全部 V 脚本 + 方案 §3 + 落地计划 §5.6
- 符号一致性：check-arch-consistency.sh s1 识别 B 级 `exempt: true` 后直接放行（exit 0）
```

**不做**：
- 按 AC 分六节（S1 AC 分行硬约束"选"· 原生 v1.8 豁免 · DDL 即 AC · 没有独立 API/Domain/Event/Error/NFR 五行语义可拆）
- bump ADR（§26.2 豁免条款 3 · 原地重写不迭代架构）

### Step 2 · 产 `design/analysis/s1-business-analysis.yml` schema_version 1.1（精简版）

**精简 YAML 的 B 级特化**：

| 字段 | B 级要求 | S1 具体填法 |
|---|---|---|
| `schema_version` | 1.1（v1.8 硬约束） | `1.1` |
| `phase_id` | 必须 | `s1` |
| `phase_level` | v1.8_v1.9 升级计划 §三新 0.7 列对应标记 | `B` · 说明本 yml 是精简版 |
| `sc_covered` | 从 `sc-phase-mapping.yml` 提取 owner_phases 含 s1 的 SC 集合 | **`[]`**（sc-phase-mapping 15 SC 里 s1 不在任何 owner_phases · 精简路径允许空 sc_covered） |
| `supports_sc` | v1.8 B 级特化新字段（精简版 S1 新增 · 记录 S1 **支撑**而非**拥有**的 SC 集合） | `["SC-01".."SC-15"]` 全量（因为所有 SC 都要落库到 S1 DDL） |
| `narrative` | 200-500 字散文 | 见附录 A |
| `ac_coverage` | 每 AC 含 critical + verification_matrix 五类 | 8 条（按 §5.1 A1-A8 假设 · 每条一个 DDL 契约 AC · 不走 SC-XX.AC-Y 格式 · 用 `S1.DDL-AC-1..8`） |
| `four_role_slots` | **B 级豁免**（§1.5 "无 four_role_slots"） | **不填** · 仅保留 `architect_anchor` 指向 arch.md front matter + `observable_behavior_anchor` 指向 `ddl-count.sh` 输出 |

**8 条 DDL 契约 AC（S1.DDL-AC-1..8 · 基于 §5.1 A1-A8 假设）**：见附录 A。

**critical 判定**（六项触发标准 · 儿童教育场景）：
- `A1` Flyway-only DDL 版本化：`critical=true`（合规 · K12 库变更审计要求）
- `A2` 主键 bigserial + 匿名域 UUID：`critical=true`（并发 · 匿名域身份隔离）
- `A3` timestamptz UTC + 业务时间 nullable：`critical=false`（非核心安全属性 · 但 observable 需断言）
- `A4` JSONB GIN 索引：`critical=false`（性能 · 不涉六项触发）
- `A5` 软删除 `deleted_at` + 分区索引：`critical=true`（删除 · GDPR/未成年人数据"被遗忘"合规）
- `A6` FK `ON DELETE RESTRICT` + 事件表无 FK：`critical=true`（涉删除防级联 · 儿童数据保护）
- `A7` 学科枚举 CHECK 约束：`critical=false`（数据完整性 · 非安全/权限/删除）
- `A8` 审计 audit_log append-only：`critical=true`（合规 · 审计不可篡改硬要求）

**critical=true 共 5 条**（A1/A2/A5/A6/A8）· Step 5 理论需 Verifier 独立复写 5 条 · 但 §26.2 豁免 4 类推走豁免（见 §三）。

commit：`docs(s1-retrofit): business-analysis yml schema 1.1 精简版（B 级 · 8 DDL-AC · 5 critical）[RETROFIT-v1.8]`

### Step 3 · 本 plan（当前文件）· 等 User 审签

— 无代码产出物 · 仅流程关卡。User 审签后 Step 4 开工。

### Step 4 · Builder 打 patch（两个 commit · 非代码业务变更）

| Commit | scope(s1-retrofit) | 内容 | 对应 DoD/V |
|---|---|---|---|
| **Commit A** | arch 原地重写补规则 F 列表 | `design/arch/s1-data.md` 末尾追加 N/A 列表三行（保留全部 front matter） | DoD-S1-09 · V-S1-08 |
| **Commit B** | business-analysis yml 精简 schema 1.1 | 产 `design/analysis/s1-business-analysis.yml`（schema 1.1 · 8 DDL-AC · 5 critical · narrative 200-400 字 · 附 supports_sc 15 SC） | §1.5 B 级 0.0.5 强制 · 新增 V-S1-20（见 §二 Step 6） |

**豁免**（§26.2 条款 1 · 原 s1-done commit 无前缀 · 本 Retrofit 期间新 commit 统一挂 `[RETROFIT-v1.8]`）：

- ✅ **可选**：给现有 `FlywayMigrateIT` / `VectorRepositoryIT` 的 2 个 @Test 方法加 `@CoversAC` 注解 · 但 S1 AC 分行硬约束"选"· 本 Retrofit **走豁免**（§1.2 B 级 0.7 值=选 · §26.2 条款 5 类推）· 不强加注解。
- ✅ 本 Retrofit **不**补 matrix 缺行测试（现有 2 个 @Test 方法已覆盖 V-S1-01..05 断言 · DDL-AC-1..8 的 verification_matrix 直接指向这 2 个 IT 方法 · 一对多 · §26.2 豁免条款 5 类推）。

### Step 5 · Verifier 独立复写 critical AC · **走豁免条款 4 类推**

- **critical AC 数量**：5（S1.DDL-AC-1/2/5/6/8）
- **Verifier 独立复写可行性分析**：
  - DDL-AC-1（Flyway-only）验证 = 检查 `backend/common/src/main/resources/db/migration/` 无手工 `CREATE TABLE` 脚本 → **非代码逻辑 · 仅文件存在性扫描**
  - DDL-AC-2（主键 bigserial + UUID 匿名域）验证 = `psql` 查 `pg_attribute` 所有表主键列类型 → **机械断言 · FlywayMigrateIT 已覆盖**
  - DDL-AC-5（软删除 `deleted_at` + 分区索引）验证 = `psql` 查 `pg_indexes WHERE indexdef LIKE '%deleted_at IS NULL%'` → **机械断言**
  - DDL-AC-6（FK RESTRICT + 事件表无 FK）验证 = `psql` 查 `pg_constraint contype='f' confdeltype='r'` → **机械断言 · FlywayMigrateIT FK 计数已覆盖**
  - DDL-AC-8（audit_log append-only）验证 = 检查 `V1.0.050__audit_log.sql` 无 `UPDATE`/`DELETE` 操作（只 `INSERT`）+ 无 `deleted_at` 列 · 无级联 DELETE 触发器 → **SQL 文件静态扫描**
- **结论**：S1 所有 critical AC 都是 **DDL schema 契约断言**（非业务代码逻辑）· 独立 Verifier "复写"在此语义下 ≈ 独立跑一遍 `FlywayMigrateIT` + `ddl-idempotency.sh` + 静态扫描 · 本质是**重复已有机械断言**· 独立会话"复写"带不来信号增益。
- **走豁免**：§26.2 条款 4 类推适用（原文："Verifier 复写暴露旧 bug 走 Hotfix 不算 Retrofit 失败" · 核心思想是独立复写的价值来自业务语义重解释 · DDL 无业务语义 · 类推豁免整个 Step 5）。
- **产出**：`reports/verifier/s1-retrofit-verifier.md`（一行记录）：
  ```
  critical_ac_count: 5 (S1.DDL-AC-1/2/5/6/8)
  verifier_rewrite: N/A
  exemption: §26.2 条款 4 类推（DDL 契约无业务代码逻辑 · 机械断言由 FlywayMigrateIT / ddl-idempotency.sh / SQL 静态扫描承担 · 独立复写带不来信号增益）
  ```

### Step 6 · 原始业务闸 V-S1-01..09 + 可选 V-S1-20 + 打 `s1-v1.8-compliant` tag

**闸门清单**（**全量** · 不再筛 · 明确回应 feedback memory 层 1 "全部/必须 X"强制条款）：

| 闸 | 命令 | 预期 | 归属 |
|---|---|---|---|
| V-S1-01 | `mvn flyway:info` 无 Pending/Failed | 0 | §5.8 原生 |
| V-S1-02 | `ops/scripts/ddl-count.sh` | 0（tables=18 indexes≥23 checks≥12 fks≥14） | §5.8 原生 |
| V-S1-03 | pgvector + ivfflat 索引存在 | 0 | §5.8 原生 |
| V-S1-04 | `FlywayMigrateIT` + `VectorRepositoryIT` 绿 | 0 | §5.8 原生 |
| V-S1-05 | `ops/scripts/ddl-idempotency.sh` | 0 | §5.8 原生 |
| V-S1-06 | ≥ 3 个 Undo 脚本 | 0 | §5.8 原生 |
| V-S1-07 | `ops/scripts/check-allowlist.sh s1` | 0 | §5.8 原生 |
| V-S1-08 | `ops/scripts/check-arch-consistency.sh s1` 识别 B 级豁免 | 0 | §5.8 原生 |
| V-S1-09 | `git ls-remote --tags origin` 含 `s1-done` | 0 | §5.8 原生 |
| **V-S1-20**（新增 · 选做 · v1.8 精简合规闸） | Python 断言 `design/analysis/s1-business-analysis.yml` schema_version==1.1 + 含 `phase_level:B` + `ac_coverage` ≥ 8 条 + 每条 AC 有 `critical` 字段 + 每条 AC 有 `verification_matrix` 五类键（happy_path/error_paths/boundary/observable/visual） · **仅校验精简版不校验 @CoversAC 反查**（因 S1 AC 分行"选"走豁免） | 0 | **本 Retrofit 新增**（§26.1 Step 6 · v1.8 合规性最小保证） |

**环境要求**：V-S1-01/02/03/05 需本地 docker + pgvector/pgvector:pg16 起 PG 实例（`ddl-idempotency.sh` 自动起）· V-S1-04 需 Testcontainers（自动拉）· 已知主执行档 §5.8 要求的 `PG_URL` 变量默认 `postgresql://postgres:wb@localhost:5432/wrongbook` 可由 `ddl-idempotency.sh` 启动的容器满足。

**失败回滚**：任一闸红 → 按 §5.10 失败回滚表处理（非 Retrofit 失败 · 见 §26.2 条款 4 类推：旧代码 bug 走 Hotfix）· 修复后重跑闸。

**打 tag**：
```bash
git tag s1-v1.8-compliant
git push origin s1-v1.8-compliant
# s1-done tag 保持不动（@ 06113ea）· 新 tag 与旧 tag 共存（§26.1 Step 6 硬规）
```

**产 complete 报告**：`reports/retrofit/s1-complete.md` 含各闸返回码 + `s1-v1.8-compliant` 指向 commit hash + 本 plan §三豁免适用记录。

## 三、§26.2 豁免条款适用对照表（覆盖/豁免 显式登记 · 不隐藏）

| # | 豁免项 | S1 是否适用 | 动作 / 理由 |
|---|---|---|---|
| 1 | **commit history 不回写前缀** | **适用** | S1 原 commit `06113ea feat(s1): 21 tables via Flyway...` 无 `[SC-XX-ACY]` 前缀 · 不回写 · 本 Retrofit 新 commit 挂 `[RETROFIT-v1.8]` 后缀 |
| 2 | `--commits` 仅校验 Retrofit 期间增量 | **未触发**（S1 AC 分行硬约束"选" · V-S1-20 只校 analysis.yml · 不调 `check-ac-coverage.sh --commits`） | 实际未跑 `--commits` 子命令 · 豁免条款预置但未激活 |
| 3 | arch 原地重写不 bump ADR | **适用** | Commit A 仅补 §1.7 规则 F 三行 N/A 列表 · 无架构变更 · 无 ADR |
| 4 | Verifier 复写暴露旧 bug 走 Hotfix 不算 Retrofit 失败 | **类推适用** | Step 5 整体走豁免（DDL 无业务代码逻辑 · 独立复写无信号增益 · 详见 §二 Step 5） |
| **5** | **AC 分行三硬约束（#13）整体豁免** | **适用**（§1.5 B 级 0.7 值=选 · §1.2 新列判定） | arch 不按 AC 分节 · commit 无 `[SC-XX-ACY]` 前缀 · 测试无 `@CoversAC` 注解 · V-S1-20 也不反查 @CoversAC |

## 四、预计工作量与影响面

- **文件新增**：1 个（`design/analysis/s1-business-analysis.yml`）
- **文件修改**：1 个（`design/arch/s1-data.md` 末尾追加 3 行列表 · front matter 不动）
- **代码影响**：0 行（不动 migration / IT / allowlist / undo）
- **commit 数**：2（Commit A arch 微调 + Commit B analysis yml）· 均挂 `[RETROFIT-v1.8]` 后缀
- **下游解锁**：
  - S9 `check-business-match.sh --aggregate` 能正确识别 S1 精简 yml（不会因缺 yml crash）
  - Batch B（S3/S4/S5）Retrofit 时可参考 S1 的 B 级精简 yml 模板（若其中某些是 B 级则适用 · 当前 S3/S4/S5 都是 A 级 · 不用精简路径）
  - v1.9 E2E Runner 跑 `supports_sc` 全量时有 S1 的数据契约断言记录可查

## 五、风险与 User 关注点

1. **sc_covered 空 + supports_sc 15 全量的结构是否被下游脚本接受**？当前 `check-business-match.sh --slots s1` 逻辑未知（S0 Retrofit 时该子命令标占位 TODO）· 若脚本硬要求 `sc_covered` 非空 · V-S0-15 可能会在本 Retrofit 后对 s1 报红。**决策点**：(a) 本 plan 保持精简空 sc_covered + supports_sc 新字段；(b) 若 User 偏好"不新增字段"· 改为 `sc_covered: []` + 在 narrative 叙述"S1 支撑 15 SC 落盘"· 二选一。**默认取 (a)**（新字段更机读友好 · 与 §1.5 B 级"精简 YAML"精神吻合）。
2. **DDL-AC 编号 `S1.DDL-AC-1..8` 的合法性**：原生 v1.8 AC id 格式是 `SC-XX.AC-Y` · S1 无 SC 拥有 · 本 plan 用 `S1.DDL-AC-*` 非标准格式。若 User 坚持"严格对齐 SC-XX.AC-Y"· 可改为 `S1.AC-1..8`（完全脱离 SC 命名空间）或干脆省略 ac_coverage 改用单体 `ddl_contract_assertions` 字段。**默认取 `S1.DDL-AC-1..8`**（显式标 DDL · 避免与 SC 混淆）。
3. **retrofit-to-v18.sh 的 B 级识别 bug**：脚本目前对 S1 判 `is_exempt=true` 直接放过 · 本 Retrofit 手工纠偏但**未修脚本**。留 TODO 给主执行档下次迭代 · 或由 S0 Retrofit 的"脚本 TODO 边界"后续补（`retrofit-to-v18.sh` 加读 §1.5 适用性分级表 · B 级路径不走 `is_exempt` 短路）。**本 plan 不修脚本**（超出 S1 Retrofit 范围）。
4. **V-S1-20 的必要性**：严格读主执行档 §4.8 和 v1.8_v1.9 升级计划 §三改动 7 · S1 不在"六领域 Phase 各追加 V-SX-20"范围内。本 plan 新增 V-S1-20 是为**最小保证 `s1-v1.8-compliant` tag 的合规性语义**（否则打 tag 却不验精简 yml schema · 名实不符）。**User 可选择**：(a) 保留 V-S1-20（推荐 · 给 tag 加硬证据）；(b) 去掉 V-S1-20 · 仅跑 V-S1-01..09 + 打 tag（`s1-v1.8-compliant` 此时仅承诺"原始业务闸全绿 + 精简 yml 存在"· 不校 schema 细节）。**默认取 (a)**。
5. **整体 Retrofit 是否触碰 Flyway migrations / IT 测试代码**：**不触碰**（Commit A/B 仅动 md + yml）· 零代码变更 · 零回归风险。

## 六、签字位

- [ ] User 审阅本 plan · 确认 §二 六步映射 + §三 豁免对照表 + §五 5 项风险默认决策
- [ ] User 标注 §五 风险项的最终决策（逐条 / 批量）
  - [ ] #1 精简 yml 结构（(a) 新字段 `supports_sc` / (b) 仅 narrative）
  - [ ] #2 AC 命名（`S1.DDL-AC-1..8` / `S1.AC-1..8` / `ddl_contract_assertions`）
  - [ ] #3 retrofit-to-v18.sh B 级识别 bug 是否本 Retrofit 顺手修（默认：不修）
  - [ ] #4 V-S1-20 是否新增（默认：新增）
  - [ ] 其他覆盖/豁免条款补充
- [ ] 在顶部 front matter 填 `signed_by: @<github-handle>` + `approved_at: <ISO 8601>` + `approval_method`
- [ ] PR description 打 `/retrofit-s1-ok`（或直接本地 git commit 推 plan 更新）

签字后 Builder Agent 开 Step 4：先打 `s1-retrofit-start` tag（retrofit-to-v18.sh 非 dry-run 模式执行）· 再按 Commit A-B 顺序执行 · 每 commit 本地跑一遍对应 V-S1-XX 闸 · 全绿后进 Step 6 跑全量闸 + 可选 V-S1-20 + 打 `s1-v1.8-compliant` tag + 产 `s1-complete.md`。

---

## 附录 A · `design/analysis/s1-business-analysis.yml` 精简版草案（Commit B 内容 · 待 User 覆核）

```yaml
# 落地计划 §1.5 B 级适用性精简版 · S1 DDL 数据契约
# schema_version 1.1（v1.8 · 含 critical + verification_matrix 五类）
# 精简规则：
#   - sc_covered 空（S1 不在任何 SC 的 owner_phases）
#   - supports_sc 全量（S1 支撑 15 SC 数据落盘）
#   - 无 four_role_slots（§1.5 B 级"无 four_role_slots"）
#   - ac 改用 S1.DDL-AC-1..8 命名（非标 SC-XX.AC-Y · 详见 plan §五 #2）
#   - verification_matrix 一对多映射到 FlywayMigrateIT/VectorRepositoryIT 现有 @Test 方法

schema_version: 1.1

phase_id: s1
phase_level: B                        # v1.8_v1.9 升级计划 §三改动 3 · AC 分行硬约束=选
sc_covered: []                        # S1 不拥有任何 SC（sc-phase-mapping.yml 无 s1 在 owner_phases）
supports_sc:                          # B 级精简版新字段 · S1 作为数据基座支撑的 SC 全集
  - SC-01
  - SC-02
  - SC-03
  - SC-04
  - SC-05
  - SC-06
  - SC-07
  - SC-08
  - SC-09
  - SC-10
  - SC-11
  - SC-12
  - SC-13
  - SC-14
  - SC-15

narrative: |
  S1 是 AI 错题本项目的数据契约基座：把方案 §3 定义的 18+ 张表（基础域 / 错题域 / 匿名域 / 审计域）
  通过 Flyway 版本化 V 脚本落到 PostgreSQL 16 + pgvector 0.6。本 Phase 按 §1.5 B 级适用性执行——
  0.1 业务理解已完成（假设 A1-A8 + 歧义 Q1-Q2 均已 User 签字 · 见 arch/s1-data.md front matter）·
  0.2 架构设计豁免（DDL 即数据架构文档 · 权威符号源在 `backend/common/src/main/resources/db/migration/` V 脚本）。

  S1 不拥有任何业务 SC（sc-phase-mapping.yml 15 SC 的 owner_phases 均不含 s1）· 但通过 supports_sc
  15 SC 全量声明：任何 SC 的数据落盘/外键关联/审计事件都依赖 S1 的 DDL 契约。因此本 YAML 的 ac_coverage
  按 §5.1 A1-A8 假设改写为 8 条 DDL 契约 AC（S1.DDL-AC-1..8）· 覆盖版本化/主键/时间戳/JSONB/软删除/
  FK/学科枚举/审计 append-only 八大 schema 级断言。critical=true 5 条（A1/A2/A5/A6/A8 · 涉合规/并发/
  删除/未成年人数据保护）· critical=false 3 条（A3/A4/A7 · 性能与数据完整性 · 不触六项触发标准）。

  Verifier 独立复写走 §26.2 豁免条款 4 类推（DDL 契约无业务代码逻辑 · 独立复写 ≈ 重复 FlywayMigrateIT
  机械断言 · 无信号增益）· 详见 reports/retrofit/s1-plan.md §二 Step 5。

ac_coverage:
  - ac_id: "S1.DDL-AC-1"
    statement: "所有 DDL 通过 Flyway V 脚本版本化 · 禁止手改/GUI 导出/psql 交互式 CREATE TABLE · U 脚本仅 3 个（V010/V016/V030 · 本地开发重置用 · 生产禁用）"
    source: "落地计划 §5.1 A1 · 方案 §3.1 DDL 约定"
    critical: true
    critical_reason: "合规 · K12 库变更需审计可追溯 · 手改绕过版本化等于失控"
    domain_entities: [FlywaySchemaHistory, db.migration/*.sql, db.undo/*.sql]
    upstream_contract: []
    downstream_contract: ["所有 S2+ 服务的 JPA Entity 映射"]
    four_role_slots:
      architect_anchor: "design/arch/s1-data.md front matter sources"
      observable_behavior_anchor: "mvn flyway:info 无 Pending/Failed · ddl-idempotency.sh 双跑无差异"
    risks:
      - "V 脚本人为删改 · 幂等断言识别（V-S1-05）"
    verification_matrix:
      happy_path:
        - id: "happy_path.0"
          given: "空库"
          when: "mvn flyway:migrate"
          then: "23 条 V 脚本按序执行 · flyway_schema_history 无 Failed · FlywayMigrateIT#migrate_all_v_scripts_and_assert_schema 绿"
      error_paths:
        - id: "error_paths.0"
          given: "V 脚本内含 `DROP TABLE` 关键字"
          when: "check-allowlist.sh s1"
          then: "exit 非 0（允许列表 additional_deny 命中）· V-S1-07"
      boundary:
        - id: "boundary.0"
          given: "已 migrate 的库"
          when: "ddl-idempotency.sh 双跑"
          then: "第二次 migrate 无 Pending · migrationsExecuted == 0"
      observable:
        - id: "observable.0"
          given: "migrate 完成后"
          when: "psql SELECT count(*) FROM flyway_schema_history WHERE success=false"
          then: "== 0"
      visual: []

  - ac_id: "S1.DDL-AC-2"
    statement: "主键统一 BIGINT PRIMARY KEY with Snowflake 应用生成（§5.1 A2 Override）· UUID 仅匿名域 anon_visitor.anon_id 使用 · 其他表不用 UUID 不用 bigserial"
    source: "落地计划 §5.1 A2 · 方案 §3.1 / §3.3"
    critical: true
    critical_reason: "并发 · 匿名域身份隔离 · 错用 bigserial 会在高并发下暴露用户增长规模"
    domain_entities: [user_account, wrong_item, review_plan, anon_visitor, audit_log, outbox]
    four_role_slots:
      architect_anchor: "design/arch/s1-data.md special_requirements.A2"
      observable_behavior_anchor: "psql \\d+ <table> 输出主键列类型断言"
    risks:
      - "若遗忘 Snowflake 生成器 · 会退化到序列 · 违反 A2"
    verification_matrix:
      happy_path:
        - id: "happy_path.0"
          given: "migrate 完成"
          when: "psql 查所有业务表主键列"
          then: "除 anon_visitor.anon_id 为 uuid 外 · 其他主键均为 bigint（非 bigserial）"
      error_paths:
        - id: "error_paths.0"
          given: "V 脚本误写 `id bigserial PRIMARY KEY`"
          when: "V-S1-04 FlywayMigrateIT 运行"
          then: "（当前测试未严格断言 · TODO: 后续 S3/S4 落 Entity 时 JPA @GeneratedValue 不匹配会暴露）"
      boundary: []
      observable:
        - id: "observable.0"
          given: "产线运行一周后"
          when: "查 wrong_item.id 最大值"
          then: "不呈现严格单调序列（Snowflake ID 非连续）"
      visual: []

  - ac_id: "S1.DDL-AC-3"
    statement: "时间戳统一 timestamptz（带时区 · 默认 UTC）· 业务时间（mastered_at / completed_at / expires_at 等）允许 NULL 表示\"未发生\"· session TIMEZONE=UTC（§5.1 Q1 回答）· 前端按 user_profile.timezone 换算"
    source: "落地计划 §5.1 A3 + Q1 · 方案 §3.1"
    critical: false
    critical_reason: ""
    domain_entities: [wrong_item.mastered_at, review_plan.next_review_at, guest_session.expires_at, audit_log.created_at]
    four_role_slots:
      architect_anchor: "design/arch/s1-data.md special_requirements.Q1 / A3"
      observable_behavior_anchor: "psql SELECT data_type FROM information_schema.columns WHERE column_name LIKE '%_at'"
    risks:
      - "若某表用了 timestamp (无时区) · 跨时区用户会看到错位时间"
    verification_matrix:
      happy_path:
        - id: "happy_path.0"
          given: "migrate 完成"
          when: "psql 查所有 *_at 列"
          then: "全部为 timestamp with time zone"
      error_paths: []
      boundary:
        - id: "boundary.0"
          given: "业务时间列如 wrong_item.mastered_at"
          when: "插入 NULL"
          then: "允许（语义：未掌握）"
      observable:
        - id: "observable.0"
          given: "PostgreSQL session"
          when: "SHOW timezone"
          then: "UTC"
      visual: []

  - ac_id: "S1.DDL-AC-4"
    statement: "JSONB 字段统一配 GIN 索引（全字段 GIN · 非路径索引 · §5.1 A4）· 路径索引按 ops-query-plan 观测后按需加 · 本 Phase 先全 GIN"
    source: "落地计划 §5.1 A4 · 方案 §3.5"
    critical: false
    critical_reason: ""
    domain_entities: [wrong_item.tags_jsonb, wrong_item_analysis.payload_jsonb, audit_log.payload_jsonb]
    four_role_slots:
      architect_anchor: "design/arch/s1-data.md special_requirements.A4"
      observable_behavior_anchor: "psql \\di+ 查 GIN 索引计数"
    risks: []
    verification_matrix:
      happy_path:
        - id: "happy_path.0"
          given: "migrate 完成"
          when: "psql 查 pg_indexes WHERE indexdef LIKE '%USING gin%'"
          then: "计数 ≥ 3（对应 3 个主要 JSONB 列）· V-S1-02 间接断言 indexes≥23"
      error_paths: []
      boundary: []
      observable:
        - id: "observable.0"
          given: "向 wrong_item.tags_jsonb 插入 1000 行"
          when: "EXPLAIN ANALYZE SELECT ... WHERE tags_jsonb @> '[\"tag1\"]'"
          then: "使用 Bitmap Index Scan on gin_idx（非 Seq Scan）"
      visual: []

  - ac_id: "S1.DDL-AC-5"
    statement: "软删除列 deleted_at + 分区索引 WHERE deleted_at IS NULL（业务主表一律带此列 · 审计域除外）"
    source: "落地计划 §5.1 A5 · 方案 §3.1"
    critical: true
    critical_reason: "删除 + 合规 + 未成年人数据 · GDPR / 儿童个人信息保护法要求\"被遗忘\"软删除保留审计痕迹"
    domain_entities: [user_account, wrong_item, wrong_item_analysis, review_plan, guest_session, guest_wrong_item, share_card]
    four_role_slots:
      architect_anchor: "design/arch/s1-data.md special_requirements.A5"
      observable_behavior_anchor: "psql \\di+ 查 partial index WHERE deleted_at IS NULL"
    risks:
      - "若某业务表漏加 deleted_at · 下游 S3 Controller 无法软删 · 必须物理删 · 违反合规"
    verification_matrix:
      happy_path:
        - id: "happy_path.0"
          given: "migrate 完成"
          when: "psql 查所有业务主表列"
          then: "wrong_item / review_plan / guest_session 等均有 deleted_at timestamptz NULL 列"
      error_paths:
        - id: "error_paths.0"
          given: "audit_log 表（append-only · DDL-AC-8 规定）"
          when: "psql 查列"
          then: "无 deleted_at 列（审计域无软删）"
      boundary:
        - id: "boundary.0"
          given: "partial index"
          when: "psql 查 pg_indexes WHERE indexdef LIKE '%deleted_at IS NULL%'"
          then: "计数 ≥ 5（对应主业务表）"
      observable:
        - id: "observable.0"
          given: "软删 wrong_item.id=123"
          when: "UPDATE SET deleted_at=now()"
          then: "后续 SELECT ... WHERE deleted_at IS NULL 查询不命中 · EXPLAIN 走 partial index"
      visual: []

  - ac_id: "S1.DDL-AC-6"
    statement: "外键策略：业务主表间 FK + ON DELETE RESTRICT（禁止级联删除 · 防止误操作连锁）· 事件类表（outbox / review_event / audit_log）不走 FK（性能 · 事件可独立于源实体存在）"
    source: "落地计划 §5.1 A6 · 方案 §3.2 / §3.4"
    critical: true
    critical_reason: "删除 + 未成年人数据保护 · 级联删除错误会连锁清除儿童用户的错题+复习+审计记录"
    domain_entities: [wrong_item→user_account (FK RESTRICT), wrong_item_analysis→wrong_item (FK RESTRICT), outbox, review_event, audit_log]
    four_role_slots:
      architect_anchor: "design/arch/s1-data.md special_requirements.A6"
      observable_behavior_anchor: "psql 查 pg_constraint contype='f' confdeltype"
    risks:
      - "若误写 ON DELETE CASCADE · 删除 user_account 会连锁清 wrong_item → wrong_item_analysis"
    verification_matrix:
      happy_path:
        - id: "happy_path.0"
          given: "migrate 完成"
          when: "psql 查 pg_constraint contype='f'"
          then: "计数 ≥ 14 · 且所有 confdeltype 均为 'r' (RESTRICT)"
      error_paths:
        - id: "error_paths.0"
          given: "已存在 wrong_item 引用 user_account.id=1"
          when: "DELETE FROM user_account WHERE id=1"
          then: "抛 23503 foreign_key_violation"
      boundary:
        - id: "boundary.0"
          given: "outbox 表"
          when: "psql \\d outbox 查外键"
          then: "无 FK（事件表允许孤儿）"
      observable:
        - id: "observable.0"
          given: "V-S1-02 ddl-count.sh"
          when: "执行"
          then: "fks ≥ 14（§5.3 目标硬约束）"
      visual: []

  - ac_id: "S1.DDL-AC-7"
    statement: "学科字典（subject）采用 CHECK 约束枚举（math / physics / chinese / english / ...）· 不做动态 ALTER TYPE 管理（避免运行时风险 · §5.1 A7）"
    source: "落地计划 §5.1 A7 · 方案 §2A.2"
    critical: false
    critical_reason: ""
    domain_entities: [wrong_item.subject, tag_taxonomy.subject]
    four_role_slots:
      architect_anchor: "design/arch/s1-data.md special_requirements.A7"
      observable_behavior_anchor: "psql 查 pg_constraint contype='c' conname LIKE '%subject%'"
    risks: []
    verification_matrix:
      happy_path:
        - id: "happy_path.0"
          given: "migrate 完成"
          when: "psql 查 wrong_item 表的 CHECK 约束"
          then: "存在 subject IN (...) 枚举约束"
      error_paths:
        - id: "error_paths.0"
          given: "向 wrong_item.subject 插入 'unknown'"
          when: "INSERT"
          then: "抛 23514 check_violation"
      boundary: []
      observable:
        - id: "observable.0"
          given: "V-S1-02 ddl-count.sh"
          when: "执行"
          then: "checks ≥ 12（§5.3 目标硬约束）"
      visual: []

  - ac_id: "S1.DDL-AC-8"
    statement: "审计域（audit_log）append-only · 无 UPDATE/DELETE · 无 deleted_at 列 · 仅 created_at · 由归档 job 迁冷存（S10 之后）· §5.1 A8"
    source: "落地计划 §5.1 A8 · 方案 §3.4"
    critical: true
    critical_reason: "合规 · 儿童个人信息保护法 + 审计不可篡改硬要求"
    domain_entities: [audit_log]
    four_role_slots:
      architect_anchor: "design/arch/s1-data.md special_requirements.A8"
      observable_behavior_anchor: "SQL 静态扫描 V1.0.050__audit_log.sql"
    risks:
      - "若后续 Phase 误加 deleted_at 列到 audit_log · 违反 append-only"
    verification_matrix:
      happy_path:
        - id: "happy_path.0"
          given: "V1.0.050__audit_log.sql"
          when: "静态扫描"
          then: "无 UPDATE / DELETE SQL · 无 deleted_at 列 DDL · 无 ON DELETE CASCADE 触发器"
      error_paths:
        - id: "error_paths.0"
          given: "向 audit_log.id=1 的行发起 UPDATE"
          when: "psql"
          then: "（当前无触发器拦截 · TODO: S10 归档时补 RULE 或触发器禁 UPDATE）"
      boundary:
        - id: "boundary.0"
          given: "audit_log 表结构"
          when: "psql \\d audit_log"
          then: "无 deleted_at 列"
      observable:
        - id: "observable.0"
          given: "migrate 完成"
          when: "psql 查 audit_log 列"
          then: "仅含 id / actor_type / action / payload / created_at 等 · 无软删列"
      visual: []
```

---

## 附录 B · Step 4 commit message 模板（Builder 执行时严格照此）

```
<scope>(s1-retrofit): <commit-A..B 摘要> [RETROFIT-v1.8]

- 新增/修改清单（按产出物对应条款）
- 对应验证闸：V-S1-XX..XX

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
```

示例（Commit A）：
```
docs(s1-retrofit): arch 补 §1.7 规则 F N/A 列表 [RETROFIT-v1.8]

- 修改 design/arch/s1-data.md 末尾追加三行 N/A 列表
  （业务理解 ✅ / 架构设计 N/A · DDL 即契约 / 符号一致性 check-arch-consistency.sh 放行）
- 保留全部 front matter（exempt:true + biz_gate + special_requirements Q1/Q2/A1-A8）
- 对应验证闸：V-S1-08（check-arch-consistency s1 识别 B 级豁免放行）

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
```

示例（Commit B）：
```
docs(s1-retrofit): business-analysis yml schema 1.1 精简版 [RETROFIT-v1.8]

- 新增 design/analysis/s1-business-analysis.yml（schema 1.1 · B 级精简版）
  - sc_covered: [] · supports_sc: SC-01..SC-15 全量
  - ac_coverage: 8 条 DDL 契约 AC（S1.DDL-AC-1..8）· 5 critical · 3 non-critical
  - verification_matrix 五类指向 FlywayMigrateIT / VectorRepositoryIT / ddl-count.sh / 静态扫描
  - 无 four_role_slots（§1.5 B 级豁免）
- 对应验证闸：V-S1-20（Python schema 断言）· §1.5 B 级 0.0.5 强制

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
```
