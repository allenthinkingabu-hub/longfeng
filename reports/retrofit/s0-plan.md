---
phase_id: s0
retrofit_target_version: v1.8+v1.9
plan_schema: 1.0
generated_by: builder-agent
generated_at: 2026-04-23T10:00:00+08:00
signed_by: "@allen"              # v1.8_v1.9 升级计划 §八同款短 handle · 与 2026-04-23 升级签批一致
approved_at: "2026-04-23T14:52:49+08:00"
approval_method: "direct-reply"  # User 2026-04-23 直接回复"方式1"授权 · 附录 A 全采纳 · 脚本按骨架落 · s0.yml 同步扩容
approval_decisions:
  - { item: "附录A sc-phase-mapping 15 SC 初值", decision: "全量采纳",   reason: "User 选方式1 默认路径" }
  - { item: "脚本 TODO 边界",                   decision: "骨架+占位",   reason: "按 v1.8_v1.9 升级计划 §四原则" }
  - { item: "docs/allowlist/s0.yml 扩容",        decision: "Commit A 同步扩", reason: "V-S0-09 不红" }
---

# S0 Retrofit Plan · 按 §26 六步壳 + §4.6/§4.8 实质内容

> **本 plan 是 §26.1 Step 3 产物。** User 审签后（填顶部 `signed_by` + `approved_at`）Step 4 才可开工；未签字 Builder Agent 禁止动任何仓库文件。

## 一、S0 特殊性：为何 §26 六步多数 N/A · 实质补产出物

§26 Retrofit 六步的本意是把**有业务 AC 的已完成 Phase**（S3/S4/S5 是 Batch B 明确目标，见 v1.8_v1.9 升级计划 §十.2）的 arch/测试/注解升到 v1.8 合规。

S0 的性质：

| 维度 | S0 现状 | §26 步骤是否适用 |
|---|---|---|
| 业务语义 | **无**（§1.5 C 级豁免 · §4.1-4.2 明确） | Step 1 arch 按 AC 分节 → **N/A**（arch 保持 `exempt: true` 骨架） |
| 业务 AC | **0 条**（`sc-phase-mapping.yml` 里 S0 不在任何 SC 的 `owner_phases`） | Step 2 matrix/critical → **N/A** |
| 业务测试 | **0 个** @CoversAC 测试 | Step 4 @CoversAC 补注解 → **N/A** |
| critical AC | **0 条** | Step 5 Verifier 100% 复写 → 走豁免条款 4（无可复写） |

S0 在 v1.7/v1.8/v1.9 视角下**真正不合规的点**不是 AC 追溯，而是**主执行文档 §4.6 产出物清单扩容后物理产物缺失**——v1.8_v1.9 升级计划 §九声称 "✅ S0 已落"，**实际仓库里一个都没落**。

**S0 Retrofit = 按 §4.6 清单 + §4.8 V-S0-11/14/16/17/18/19 验证闸，把 11 个脚本骨架 + 3 个新目录 + 2 份 yml 占位 + 1 个 yml 模板物理落地，借 §26 形式壳（retrofit-start tag → plan → 实施 → 闸绿 → compliant tag）完成治理。**

## 二、六步映射（S0 特化版）

### Step 1 · `design/arch/s0-bootstrap.md` diff 摘要

**保留现状 · 微调补全 §1.7 规则 F 模板的 3 个显式列表项**：

当前（`design/arch/s0-bootstrap.md` · 8 行）：
```markdown
---
phase_id: s0
exempt: true
exempt_reason: "脚手架 Phase · 无业务语义 · 见 §1.5 适用性分级 C"
---

本 Phase 豁免 Design Gate · 相关技术选型见 §1.3.1 后端技术栈决策 + §3 代码仓库与分支策略 + §1.6 工具白名单。
```

目标（对齐 §1.7 规则 F 末尾模板 · +3 行列表）：
```markdown
---
phase_id: s0
exempt: true
exempt_reason: "脚手架 Phase · 无业务语义 · 见 §1.5 适用性分级 C"
---

本 Phase 豁免 Design Gate。

- 业务理解：N/A
- 架构设计：N/A · 相关技术选型见 §1.3.1 后端技术栈决策 + §3 代码仓库与分支策略 + §1.6 工具白名单
- 符号一致性：check-arch-consistency.sh 识别 `exempt: true` 后直接放行
```

**理由**：§1.7 规则 F 是 S0 / S2 / S9 / Sd 豁免 Phase 的统一模板，三项 N/A 列表让 Reviewer Agent 能机读"业务理解/架构设计/符号一致性"三维豁免状态。当前文件只有一段散文，机读不了。

**不做**：拆分 AC 节、补 6 节（OpenAPI / JSON Schema / ADR）——因为 `exempt: true` 下 `check-arch-consistency.sh` 立即 `exit 0`，没有 AC 要追溯。

### Step 2' · 产 `design/analysis/_template.yml`（schema_version 1.1）+ `design/arch/_template.md`

S0 **本身**没有 `s0-business-analysis.yml`（不在任何 SC 的 owner_phases）。

S0 作为 **模板提供者**，要落地两份模板文件给下游所有 Phase 复制基准：

| 文件 | Schema | 关键字段 |
|---|---|---|
| `design/analysis/_template.yml` | 1.1 | `schema_version: 1.1` · `ac_coverage[].critical: bool` · `ac_coverage[].verification_matrix.{happy_path,error_paths,boundary,observable,visual}` 五类键 |
| `design/arch/_template.md` | — | v1.8 · AC 分节五行齐全模板（`## AC: SC-XX-ACY` · `- API:` · `- Domain:` · `- Event:` · `- Error:` · `- NFR:`） |

**来源**：v1.8_v1.9 升级计划 §七（`design/analysis/_template.yml` schema 1.0 → 1.1）+ §四改动 4（0.2 架构模板按 AC 分节）。

### Step 3 · 本 plan（当前文件）· 等 User 审签

— 无产出物 · 仅流程关卡。

### Step 4 · 实施：11 脚本骨架 + 3 目录 + 2 yml 占位

**预计分 5 个 commit 落地**（按逻辑分组 · 均挂 `[RETROFIT-v1.8]` 后缀）：

#### Commit A · v1.5/§1.7/§1.9 基座脚本骨架（对应 V-S0-11）

> 注：`check-arch-consistency.sh` **已在仓库中且合规**（6.2KB · 支持 `--trace-biz` / `--dry-run` / 多命名 glob），不动。

补 3 个：

| 脚本 | 签名 | 主文档条款 | TODO 边界（S0 骨架允许的占位行为） |
|---|---|---|---|
| `ops/scripts/check-oracle-source.sh` | `check-oracle-source.sh <phase-id> [--phase-ac\|--assertions]` | §1.9 规则 C | S0 占位：校验 `acceptance-criteria-signed.yml` 存在 + `signed_by` 合法性；`--phase-ac` 路径统计 Phase AC 数；`--assertions` 路径完整阈值扫描留 TODO 给各 Phase DoD 首次调用时补 |
| `ops/scripts/check-test-effectiveness.sh` | `check-test-effectiveness.sh <phase-id>` | §1.5 通用约束 #9（Mutation Kill Rate ≥ 60%） | S0 骨架：识别 pom.xml 是否含 pitest 插件 · 无则提示"Phase 内首次调用时补"；输出 `reports/phase-<phase>-mutation.md` TODO |
| `ops/scripts/backend-integration-gate.sh` | `backend-integration-gate.sh [--dry-run]` | §10.5 S5.5 联调闸 | S0 骨架：exit 0 + echo "S5.5 节点执行时补完整主链路 3×3 + 降级 3 场景编排" |

#### Commit B · v1.7 业务匹配脚本 + sc-phase-mapping 占位（对应 V-S0-14/15/16）

| 产物 | 签名/Schema | 主文档条款 | TODO 边界 |
|---|---|---|---|
| `ops/scripts/check-business-match.sh` | `[--ownership\|--slots\|--match\|--aggregate]` | §1.5 通用约束 #12 | S0 骨架：`--ownership` 校验 sc-phase-mapping 15 SC 全覆盖；`--slots` 占位；`--match` 占位；`--aggregate` 识别"无 phase 报告"正确返回而非 crash（V-S0-16 硬约束） |
| `design/sc-phase-mapping.yml` | YAML | §4.6 · §1.5 约束 #11 | 15 SC（SC-01..SC-15）全覆盖 · 每条含 `owner_phases` 键（按方案 §2B + v1.7 规划填 · 见 plan 附录 A） |

#### Commit C · §1.9 Oracle 签字模板（对应 V-S0-12/13）

| 产物 | 条款 | 关键字段 |
|---|---|---|
| `design/acceptance-criteria-signed.yml` | §1.9 规则 A/B | `schema_version: 1.0` · `signed_by: ""`（硬约束 V-S0-13：S0 **禁止预签**）· `criteria[]` 覆盖 SC-01..SC-15 · 每 SC 含 `phase_owner` + `acceptance: []` 空骨架 + `cross_cutting[]` CC-01/CC-02 占位 |

#### Commit D · v1.8 四脚本骨架 + 模板扩展（对应 V-S0-17/19）

| 产物 | 签名 | 条款 | TODO 边界 |
|---|---|---|---|
| `ops/scripts/check-ac-coverage.sh` | `<phase> [--arch\|--commits\|--tests\|--visual]` | §1.5 约束 #13 | S0 骨架：四子命令路径齐 · 实际扫描逻辑占位 TODO（各 Phase V-SX-20 首次调用时补）· 豁免 Phase（arch exempt: true）直接 exit 0 |
| `ops/scripts/run-verifier.sh` | `<phase> [--sample-strategy default\|critical-only]` | §26 Step 5 · §1.5 约束 #14 | S0 骨架：读 business-analysis.yml 筛 critical=true AC · 找不到 analysis 文件（S0/S2/Sd 豁免）→ exit 0；非豁免 Phase 找不到 → 报错 |
| `ops/scripts/retrofit-to-v18.sh` | `<phase> [--dry-run]` | §26.1 Step 1 | S0 骨架：打 `<phase>-retrofit-start` tag · 扫 arch 是否按 AC 分节 + analysis schema_version · 产 `reports/retrofit/<phase>-plan.md` 草稿（Analyst 提示 TODO · 各 Phase Retrofit 时补真实语义扫描） |
| `ops/scripts/gen-visual-baseline.sh` | `[<screen_id>\|--all]` | §4.6 · Sd.10 · 决策 C | S0 骨架：echo "Playwright headless 1440×900 固定字体 · 阈值列表 3% / 富交互 8% · 首次执行在 Sd.10 节点" · exit 0 |
| `design/analysis/_template.yml` | YAML | §四改动 6 · 升级计划 §七 | `schema_version: 1.1` · `ac_coverage[0].critical: false` · `ac_coverage[0].verification_matrix` 五类齐全（V-S0-19 Python 断言逐字段校验） |
| `design/arch/_template.md` | Markdown | 升级计划 §四改动 4 | AC 分节五行齐全模板注释版 |

#### Commit E · v1.9 三脚本骨架 + 新目录 .gitkeep（对应 V-S0-18/19）

| 产物 | 签名 | 条款 | TODO 边界 |
|---|---|---|---|
| `ops/scripts/e2e-runner.sh` | `[--strategy balanced\|critical-only\|smoke]` | §27.2 | S0 骨架：echo 策略窗口 [120,180] · exit 0 · S9 首次调用时补按 SC/AC/matrix 编排 |
| `ops/scripts/triage-agent.sh` | `<failure_id>` | §27.3 | S0 骨架：echo 六类清单 · 读 `reports/e2e/<run_id>/failures/<fid>.yml`（S0 不存在则报错）· 产 `reports/triage/<fid>.yml` TODO |
| `ops/scripts/fix-agent.sh` | `<failure_id>` | §27.4 | S0 骨架：echo 写权限限定机制 · 读 triage yml related_files · fix_attempts ≤ 3 · exit 0 |
| `design/tasks/.gitkeep` | — | §4.6 · 升级计划 §七 | Planner 切卡产物承载目录 |
| `design/system/screenshots/baseline/.gitkeep` | — | §4.6 · 升级计划 §七 | Sd 视觉 baseline 承载目录 |
| `reports/e2e/.gitkeep` | — | §4.6 v1.9 | E2E Runner 产物 |
| `reports/triage/.gitkeep` | — | §4.6 v1.9 | Triage Agent 产物 |

### Step 5 · Verifier 独立复写 · **走豁免条款 4**

- **critical AC 数量**：0（S0 无业务 AC · `sc-phase-mapping.yml` 里 S0 不在任何 owner_phases）
- **Verifier 复写清单**：∅（无可复写对象）
- **预计产出**：`reports/verifier/s0-retrofit-verifier.md` 一行记录 `critical_ac_count: 0 · verifier_rewrite: N/A · exemption: §26.2 条款 4 类推（无业务 AC → 无复写必要）`

### Step 6 · V-S0-11..V-S0-19 全绿 + 打 `s0-v1.8-compliant` tag

**闸门清单**（全部走 §4.8 现有脚本 · 不再另写）：

| 闸 | 命令 | 预期 |
|---|---|---|
| V-S0-00 | `check-arch-consistency.sh s0` | 0（已合规） |
| V-S0-11 | `for f in check-arch-consistency check-oracle-source check-test-effectiveness backend-integration-gate; do [ -x ops/scripts/$f.sh ]; done` | 0 |
| V-S0-12 | `yq '.criteria \| length' design/acceptance-criteria-signed.yml` → 15 | 0 |
| V-S0-13 | `yq '.signed_by' ...` → `""` | 0（S0 禁止预签 · User 在 S1 前 DoR 独立签） |
| V-S0-14 | 三产物（`check-business-match.sh` · `_template.yml` · `sc-phase-mapping.yml`）齐备 | 0 |
| V-S0-15 | Python 校验 `sc-phase-mapping.yml` 15 SC + 每条含 `owner_phases` | 0 |
| V-S0-16 | `check-business-match.sh --aggregate` 识别"无 phase 报告"而非 crash | 0 |
| V-S0-17 | 4 v1.8 脚本可执行 | 0 |
| V-S0-18 | 3 v1.9 脚本可执行 | 0 |
| V-S0-19 | Python 校验 `_template.yml` schema 1.1 + verification_matrix 五类 + critical + 4 目录存在 | 0 |

**打 tag**：
```bash
git tag s0-v1.8-compliant
git push origin s0-v1.8-compliant
# s0-done tag 保持不动 · 新 tag 与之共存（§26.1 Step 6 硬规）
```

**产 complete 报告**：`reports/retrofit/s0-complete.md` 含各闸返回码 + `s0-v1.8-compliant` 指向 commit hash。

## 三、§26.2 豁免条款适用情况

| # | 豁免项 | S0 是否适用 | 备注 |
|---|---|---|---|
| 1 | commit history 不回写前缀 | **适用**（但 S0 原 commit 里本无 `[SC-XX-ACY]` 前缀 · 因为无业务 AC） | Retrofit 期间新 commit 统一加 `[RETROFIT-v1.8]` 后缀即可 |
| 2 | `--commits` 只校验 Retrofit 期间增量 | **适用**（以 `s0-retrofit-start` tag 为 `--since` 基准） | S0 本身无 `[SC-XX-ACY]` 前缀硬约束 · V-S0-20 也未在 §4.8 出现 · 本 Retrofit 不跑 `--commits` |
| 3 | arch.md 原地重写不 bump ADR | **部分适用**（S0 arch 微调 3 行仅补 §1.7 规则 F 模板列表项 · 无架构变更） | 不走 ADR 流程 |
| 4 | Verifier 暴露旧 bug 走 Hotfix | **类推适用** | S0 无 critical AC → 无 Verifier 复写 → 类推豁免 |

## 四、预计工作量与影响面

- **文件新增**：11 个脚本 + 2 个 yml + 1 个 yml 模板 + 1 个 md 模板 + 4 个 `.gitkeep` = **≈19 个文件**
- **文件修改**：1 个（`design/arch/s0-bootstrap.md` 补 3 行）
- **目录新增**：`design/analysis/` · `design/tasks/` · `design/system/screenshots/baseline/` · `reports/e2e/` · `reports/triage/` · `reports/retrofit/`（本 plan 所在 · 已落）
- **代码影响**：0 行业务代码变更
- **下游解锁**：S1 DoR（需 `acceptance-criteria-signed.yml` 存在 + User 签字）· S3/S4/S5 Batch B Retrofit 启动（需 `retrofit-to-v18.sh`）· S7/S8/S11 V-SX-20 闸（需 `check-ac-coverage.sh` + `run-verifier.sh`）· Sd.10 baseline（需 `gen-visual-baseline.sh`）· S9 E2E 闭环（需三 agent 脚本）

## 五、风险与 User 关注点

1. **`sc-phase-mapping.yml` 15 SC 的 `owner_phases` 如何填**？本 plan 附录 A 给出基于方案 §2B + v1.7 规划的推断初值，**需 User 确认**（或在 S1 DoR 前独立签 `acceptance-criteria-signed.yml` 时一并覆核）。若 User 暂不确认，Commit B 可先落 15 SC 骨架但 `owner_phases` 填 `["TBD"]`，V-S0-15 校验会过（只校验"含 owner_phases 键"不校验内容），但 S9 `--aggregate` 时会红。
2. **脚本 TODO 边界**：本 plan 对每个脚本列了 "S0 骨架允许的占位行为"。若 User 觉得某脚本在 S0 就应把核心逻辑写满（不只是 exit 0），请在签字时标注 "需全量实现: <script>"。默认按 v1.8_v1.9 升级计划 §四"所有脚本以骨架 + TODO 形式落"执行。
3. **Retrofit 过程中发现旧 S0 产物有残缺**：例如 `docs/allowlist/s0.yml` 是否覆盖了 v1.8 扩容后的新脚本（`check-ac-coverage.sh` 等）？若未覆盖，V-S0-09（`check-allowlist.sh s0`）可能在本 Retrofit commit 时变红 · 需同步扩 s0.yml。计入 Commit A 兜底（按 §1.6 规则 B 格式追加 7 个新脚本到 `tools[].name`）。

## 六、签字位

- [ ] User 审阅本 plan · 确认 Step 1-6 映射与五个 Commit 分组
- [ ] User 标注 §五 关注点的回复（附录 A `owner_phases` 初值 / 脚本 TODO 边界是否放宽 / 其他）
- [ ] 在顶部 front matter 填 `signed_by: @<github-handle>` + `approved_at: <ISO 8601>`
- [ ] PR description 打 `/retrofit-s0-ok`（或直接本地 git commit 推 plan 更新）

签字后 Builder Agent 开 Step 4：先打 `s0-retrofit-start` tag · 再按 Commit A-E 顺序执行 · 每 commit 跑对应 V-S0-XX 验证闸 · 全绿后进 Step 6 打 `s0-v1.8-compliant` tag。

---

## 附录 A · `design/sc-phase-mapping.yml` 初值推断（Commit B 草案 · 待 User 覆核）

基于 `业务与技术解决方案_AI错题本_基于日历系统.md` §2B + v1.7 规划：

```yaml
schema_version: 1.0
# 15 SC × owner_phases（主产出 Phase · 次序按影响深度）
SC-01:
  title: "拍照录入错题"
  owner_phases: [s7, s3]           # 前端录入 UI（s7）+ 后端错题主域保存（s3）
SC-02:
  title: "错题列表分组筛选"
  owner_phases: [s7, s3]
SC-03:
  title: "错题详情查看与标签管理"
  owner_phases: [s7, s3]
SC-04:
  title: "错题软删除与恢复"
  owner_phases: [s7, s3]
SC-05:
  title: "AI 错题解析（文字+图像）"
  owner_phases: [s4, s3]           # ai-analysis-service（s4）消费 + 错题主域关联（s3）
SC-06:
  title: "AI 解析失败降级与重试"
  owner_phases: [s4]
SC-07:
  title: "艾宾浩斯复习计划生成"
  owner_phases: [s5]               # review-plan-service
SC-08:
  title: "复习任务执行与打分"
  owner_phases: [s8, s5]           # 前端复习界面（s8）+ 后端评分 + 调度（s5）
SC-09:
  title: "学情分析卡片（TopN 薄弱项）"
  owner_phases: [s8, s5]
SC-10:
  title: "通用日历节点绑定（复习/自定义）"
  owner_phases: [s5, s3]
SC-11:
  title: "错题图片 OSS 上传与压缩"
  owner_phases: [s6, s7]           # file-service（s6）+ 前端上传（s7）
SC-12:
  title: "匿名试用体验（device_fp 无账号）"
  owner_phases: [s11]              # anonymous-service + 匿名前端
SC-13:
  title: "匿名试用升级到正式账号数据迁移"
  owner_phases: [s11, s3]
SC-14:
  title: "家长监督视图（家庭组）"
  owner_phases: [s8, s5, s3]       # 跨域只读聚合
SC-15:
  title: "学科标签层级（subject.tag 两级）"
  owner_phases: [s3, s7]
cross_cutting:
  CC-01: { title: "任意 API P95 < 500ms", applies_to: all_phases }
  CC-02: { title: "PII 字段日志脱敏",     applies_to: all_phases }
```

**User 覆核要点**：(a) owner_phases 的顺序（第一个是主产出 Phase）；(b) SC-09/SC-14 的跨域聚合归属（s8 还是 s5）；(c) SC-15 是否算 SC 而非 cross_cutting（当前把学科层级当作独立 SC）。

---

## 附录 B · Step 4 commit message 模板（Builder 执行时严格照此）

```
<scope>(s0-retrofit): <commit-A..E 摘要> [RETROFIT-v1.8]

- 新增/修改清单（按产出物对应条款）
- 对应验证闸：V-S0-XX..XX

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
```

示例（Commit A）：
```
chore(s0-retrofit): §1.7/§1.9/§1.5 基座脚本骨架落地 [RETROFIT-v1.8]

- 补 ops/scripts/check-oracle-source.sh（§1.9 规则 C · S0 骨架）
- 补 ops/scripts/check-test-effectiveness.sh（§1.5 约束 #9 · S0 骨架）
- 补 ops/scripts/backend-integration-gate.sh（§10.5 · S0 骨架）
- 扩 docs/allowlist/s0.yml 覆盖 v1.7/v1.8/v1.9 新脚本（§1.6 规则 B）
- 对应验证闸：V-S0-11 · V-S0-09

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
```
