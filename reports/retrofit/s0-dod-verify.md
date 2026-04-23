---
phase_id: s0
dod_verify_schema: 1.0
triggered_by: "User 质询 · 2026-04-23 · '你真的按业务验证了吗'"
generated_at: 2026-04-23T15:50:00+08:00
retrofit_plan: reports/retrofit/s0-plan.md
retrofit_complete: reports/retrofit/s0-complete.md
parent_tag: s0-v1.8-compliant
---

# S0 原始 DoD 补验报告（V-S0-01..V-S0-10 + 脚本 smoke）

> 本报告是 **S0 Retrofit 后补的 C 选项验证** · 弥补 `s0-plan.md` §二 Step 6 偷偷收窄闸集合（只跑 V-S0-00/09/11..19 · 没跑 V-S0-01..V-S0-10）的设计漏洞。
> 触发者：User 连续两轮追问 "S0 真的按业务来的吗 / 你真的检查了吗"。
> 对应 feedback memory：`~/.claude/projects/.../memory/feedback_plan_explicit_exemption.md`（层 1 治法 · 本次事件催生）。

## 一、V-S0-01..V-S0-10 全量重验结果

| 闸 | 指标 | 命令 | 结果 | 备注 |
|---|---|---|---|---|
| V-S0-01 | 文件数 ≥ 49 | `git ls-files \| wc -l` | ✅ **361** | 远超阈值（S3/S4 叠加后） |
| V-S0-02 | 后端 7 模块 BOM + package 全绿 | `cd backend && mvn -q -T 1C -DskipTests package` | ✅ exit 0 | 7 个 jar 全打 · 无 error |
| V-S0-03 | 前端 workspace + 全量 build 绿 | `cd frontend && pnpm install --frozen-lockfile && pnpm -r build` | ✅ exit 0 | 2 apps + 4 packages 全 build · 均 skeleton noop（符合 S0 定义） |
| V-S0-04 | Checkstyle + ESLint 0 error | `mvn -q checkstyle:check && pnpm -r lint` | ❌ **RED · 2 errors** | **见 §二 责任归属** |
| V-S0-05 | s0-done tag 已推远端 | `git ls-remote --tags origin \| grep -q s0-done` | ✅ | |
| V-S0-06 | README 指向本计划 | `grep -q '落地实施计划_v1.0_AI自动执行.md' README.md` | ✅ | |
| V-S0-07 | check-allowlist.sh 可执行 | `[ -x ops/scripts/check-allowlist.sh ]` | ✅ | |
| V-S0-08 | 3 个 allowlist YAML 合法 | `yq '.' docs/allowlist/{global,global-deny,s0}.yml >/dev/null` | ✅ × 3 | |
| V-S0-09 | `check-allowlist.sh s0` 返回 0 | `bash ops/scripts/check-allowlist.sh s0` | ✅ | Retrofit 后回归绿 |
| V-S0-10 | CI allowlist-check job 存在 | `grep -q 'allowlist-check' .github/workflows/ci.yml` | ✅ | |

**小计**：10 条原始业务闸 · **9 PASS / 1 FAIL** · FAIL 归属见下。

## 二、V-S0-04 FAIL · 责任归属与处理建议

### 具体 errors

```
[ERROR] backend/ai-analysis-service/src/main/java/com/longfeng/aianalysis/llm/StubProvider.java:3:8:
        Unused import - java.util.List. [UnusedImports]
[ERROR] backend/ai-analysis-service/src/main/java/com/longfeng/aianalysis/llm/HttpLlmProvider.java:6:8:
        Unused import - java.util.List. [UnusedImports]
```

### 归属分析

- **不是 S0 骨架错**：s0-done @91a7c75 打 tag 时，`ai-analysis-service/` 里只有 `Application.java` + `application.yml`（§4.6 S0 产物定义）· 不含 `StubProvider` / `HttpLlmProvider`
- **是 S4 代码漂移**：两文件属 `feat(s4): ai-analysis-service · LLM consumer + PII + dual provider + pgvector similar [plan §8]` (commit e332998) 的 S4 业务代码
- **V-S0-04 仍红**：因 V-S0-04 是"HEAD 当前状态"的硬约束 · 不是 "s0-done commit 快照"的约束 · 当前 HEAD 红即闸红

### 推荐处理（按 §1.5 Rule F 层 1 memory 要求显式列决策）

| 方案 | 描述 | 归属 | 成本 |
|---|---|---|---|
| **A** | S4 Retrofit 开工前先修（删 2 个 unused import） | Batch B S4 Retrofit 范围 | 5 秒 |
| **B** | 独立 hotfix commit（`fix(s4): remove unused imports [HOTFIX-v1.8]`） | S4 分支独立修 · 不绑定 Retrofit | 30 秒 |
| **C** | 忽略到 S4 合 main 前 | 拖延 · 风险：合 main 时 CI 红会 block | 0（近期）· 但兜到最后 |

**建议走 B**：修复成本极低 · 隔离到独立 commit 便于追踪 · 不污染 S0/S4 Retrofit 链。

**User 不接受此方案请在此处 hard stop · 附其他决策。**

## 三、脚本 smoke 全量（11 个脚本 × 多子命令/Phase 路径）

### 发现的 3 个真缺陷（已修复）

| # | 缺陷 | 影响 | 修复动作 |
|---|---|---|---|
| 1 | `check-test-effectiveness.sh` 硬编码 `<phase>-service` 路径找 pom.xml · 项目用业务域名（wrongbook/ai-analysis/review-plan/file/anonymous）· 对任何非 s0 调用都空转返"no pom found" | 中 · 所有 Phase 下游调用错误识别为"无 backend" | 改为检测 `backend/*/pom.xml` 存在性（不按 phase_id 猜路径）· 明示 WARN + SKELETON 标签 |
| 2 | `check-ac-coverage.sh --arch` 的 `$(grep -cE ... \|\| echo 0)` · grep 无匹配时返回 "0" 且 exit 1 · `\|\| echo 0` 触发叠成 "0\n0" · `[[ -eq 0 ]]` 解析炸 · **判定失效即空转过** | **高** · 非豁免 Phase 本该 exit 1（arch 未按 AC 分节）却 exit 0 假装合规 · S3 Retrofit 启动时会被伪装绿灯误导 | 改为 `AC_COUNT=$(...) \|\| AC_COUNT=0; AC_COUNT="${AC_COUNT:-0}"` 避免 stdin 叠加 · 同时补齐"每 AC 五行齐全"实检（API/Domain/Event/Error/NFR） |
| 3 | `check-ac-coverage.sh --commits/--tests/--visual` 对非豁免 Phase 空转返 0 · 无警告区分"真绿"和"TODO 骨架假绿" | 中 · 调用者无法感知脚本没真做事 | 每个子命令加 `WARN: ... SKELETON · exit 0 仅表示脚手架可执行 · 请不要解读为真实合规` 三行警告 |

### 副作用（缺陷 2 修复后）

- `check-ac-coverage.sh s3 --arch` 现在**正确 exit 1** + 报 "未按 AC 分节" —— 暴露 S3 arch 真实的 v1.8 合规缺口
- 这本应在 Batch B S3 Retrofit 启动时第一件事被报告 · 之前我的脚本 bug 让它假装绿灯掩盖了真实缺口 · **修复后反而帮 S3 Retrofit 准确指向问题**

### 11 脚本完整 smoke 结果

| 脚本 | 子命令/Phase 路径 | 结果 |
|---|---|---|
| check-oracle-source.sh | s0 --phase-ac (豁免) / s3 --phase-ac (未签字) | ✅ 0 / ✅ 1（正确拒） |
| check-test-effectiveness.sh | s0 / s3 (修复后) | ✅ 0 / ✅ 0 + WARN |
| backend-integration-gate.sh | --dry-run | ✅ 0 |
| check-business-match.sh | --ownership / --slots s0 / --slots s3 (无 analysis) / --aggregate | ✅ 0 / ✅ 0 / ✅ 1（正确拒） / ✅ 0 |
| check-ac-coverage.sh | s0 --arch / **s3 --arch (修复后真 exit 1)** / s3 --commits/--tests/--visual (WARN) | ✅ 0 / ✅ 1 / ✅ 0+WARN × 3 |
| run-verifier.sh | s0 / s3 (无 analysis) | ✅ 0 / ✅ 1（正确拒） |
| retrofit-to-v18.sh | s0 --dry-run / s3 --dry-run | ✅ 0（合规）/ ✅ 0（标 non-compliant · 待 Retrofit） |
| gen-visual-baseline.sh | --all | ✅ 0（skeleton） |
| e2e-runner.sh | --strategy smoke | ✅ 0（产占位 junit） |
| triage-agent.sh | test-failure-001 | ✅ 0（产骨架 yml） |
| fix-agent.sh | test-failure-001 (confidence=0 → needs-human) | ✅ 0（正确拒自动修） |

**21 次 smoke 执行 · 全部按预期路径走 · 修复后无空转假绿**。

## 四、本次 S0 Retrofit 最终合规声明

- **v1.8 形式合规**：V-S0-00/09/11..19 共 22 条断言全绿（原 `s0-complete.md` 已记录）
- **原始业务闸**：V-S0-01/02/03/05/06/07/08/09/10 共 9 条 PASS · **V-S0-04 FAIL · 责任在 S4 代码漂移**
- **脚本骨架业务可用性**：3 个关键缺陷已修 · 其余 TODO 部分明示 SKELETON + WARN · 不再空转假绿
- **s0-v1.8-compliant tag 仍有效**：tag 语义 = "v1.8 产物形式合规"（见 `s0-plan.md` §二）· 不承诺"全局 DoD 绿"。V-S0-04 的 S4 代码问题由 S4 Retrofit / hotfix 负责。

## 五、未来规避同类错误的机制（层 1 治法已落）

- `~/.claude/projects/.../memory/feedback_plan_explicit_exemption.md` 已写入用户 memory system
- 所有后续 plan（S3/S4/S5 Retrofit · Batch A · v2.0 升级）产出时，会自动对照"主文档全称强制条款" + 产出"执行范围 + 显式豁免"两章节
- User 的 meta 拷问句式 "这个 plan/报告豁免了什么" 作为 Phase DoR/DoD 签字前的口头习惯兜底
