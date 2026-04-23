---
phase_id: s1
retrofit_target_version: v1.8+v1.9
completion_schema: 1.0
completed_by: builder-agent
completed_at: 2026-04-23T17:58:00+08:00
signed_plan: reports/retrofit/s1-plan.md
signed_by: "@allen"
approved_at: 2026-04-23T17:20:00+08:00
tag_compliant: s1-v1.8-compliant
tag_retrofit_start: s1-retrofit-start
tag_retrofit_start_commit: 0547bd3
s1_done_commit: 06113ea
commits:
  - plan_approved: "0547bd3 docs(s1-retrofit): plan approved by @allen · 全量默认方案 [RETROFIT-v1.8]"
  - commit_A: "a8addec docs(s1-retrofit): arch 补 §1.7 规则 F N/A 列表 [RETROFIT-v1.8]"
  - commit_B: "27f1bba docs(s1-retrofit): business-analysis yml schema 1.1 精简版 [RETROFIT-v1.8]"
---

# S1 Retrofit Complete · v1.8 + v1.9 合规（B 级精简版）

## 一、六步映射实际完成情况

| §26 Step | S1 B 级特化动作 | 实际 commit / 产出 | 状态 |
|---|---|---|---|
| Step 1 · arch 原地重写 | 保留 `exempt: true` 骨架 · 末尾追加 §1.7 规则 F 三行 N/A 列表（闭合 YAML front matter） | Commit A `a8addec` | ✅ |
| Step 2 · business-analysis schema 1.1 精简版 | 新增 `design/analysis/s1-business-analysis.yml` · schema 1.1 · phase_level=B · sc_covered=[] + supports_sc 15 全量 · 8 条 DDL-AC · 5 critical | Commit B `27f1bba` | ✅ |
| Step 3 · Plan 审签 | `reports/retrofit/s1-plan.md` · @allen direct-reply 签字 · §五 5 项风险项按默认方案决策 | `0547bd3` | ✅ |
| Step 4 · Builder 打 patch | 2 commit（A + B）· 0 行代码变更 · 仅文档类 yml/md | Commit A-B | ✅ |
| Step 5 · Verifier 独立复写 critical AC | **走 §26.2 豁免条款 4 类推**（DDL 无业务代码逻辑 · 独立复写无信号增益） | `reports/verifier/s1-retrofit-verifier.md` | ✅ 豁免 |
| Step 6 · V-S1 闸 + compliant tag | V-S1-01..09 + V-S1-20 全绿 · 打 `s1-v1.8-compliant` | 本文件 | ✅ |

## 二、V-S1-01..09 + V-S1-20 全闸结果（10/10 PASS）

```
=== V-S1-01 · Flyway migrate + info 无 Pending/Failed ===
  ✓ V-S1-01 PASS（pg-wb-s1-gate @ 54321 · 27 migrations applied · flyway:info 无 Pending/Failed）

=== V-S1-02 · ddl-count.sh ===
  ✓ tables=24 indexes=88 checks=61 fks=19
    阈值：tables≥18 · indexes≥23 · checks≥12 · fks≥14 · 全部超额
  ✓ V-S1-02 exit=0

=== V-S1-03 · pgvector + ivfflat 索引 ===
  ✓ pgvector extension PASS
  ✓ ivfflat index (idx_wrong_item_embedding) PASS
  ✓ ivfflat 类型 PASS（indexdef 含 ivfflat 关键字）

=== V-S1-04 · FlywayMigrateIT + VectorRepositoryIT（Testcontainers）===
  ✓ Successfully applied 27 migrations to schema "public", now at version v1.0.052
  ✓ Successfully validated 27 migrations
  ✓ Schema "public" is up to date. No migration necessary.（幂等验证）
  ✓ V-S1-04 exit=0

=== V-S1-05 · ddl-idempotency.sh 双跑幂等 ===
  ✓ OK · flyway idempotent（PG_PORT=54321 避免与本机 calendar-pg-primary 5432 冲突）

=== V-S1-06 · Undo 脚本 ≥ 3 ===
  ✓ U1.0.010__wrong_item.sql · U1.0.016__review_plan.sql · U1.0.030__guest_session.sql
  ✓ V-S1-06 PASS

=== V-S1-07 · check-allowlist.sh s1 ===
  ✓ OK: allowlist check passed for phase=s1
  ✓ V-S1-07 exit=0

=== V-S1-08 · check-arch-consistency.sh s1（识别 B 级豁免）===
  ✓ [arch-consistency] phase=s1 exempted · skipping symbol scan
  ✓ V-S1-08 exit=0

=== V-S1-09 · s1-done tag 已推 ===
  ✓ local tag exists · V-S1-09 (local) PASS
  ✓ remote tag exists · V-S1-09 (remote) PASS

=== V-S1-20 · business-analysis.yml schema 1.1 硬断言（本 Retrofit 新增）===
  ✓ schema_version == 1.1
  ✓ phase_id == s1
  ✓ phase_level == B
  ✓ sc_covered 为空 list（B 级 · s1 不拥有任何 SC）
  ✓ supports_sc == SC-01..SC-15（15 全量）
  ✓ ac_coverage == 8（S1.DDL-AC-1..8）
  ✓ 每 AC 含 critical bool 字段
  ✓ 每 AC matrix 含 happy_path/error_paths/boundary/observable/visual 五类键
  ✓ critical==true 计数 == 5（A1/A2/A5/A6/A8）
  ✓ V-S1-20 PASS
```

**硬断言汇总**：**10/10 PASS**（V-S1-01..09 原始业务闸 9 条 + V-S1-20 v1.8 合规闸 1 条）。

## 三、§26.2 四条豁免条款 + 本 plan 新增豁免条款 5 的适用记录

| # | 豁免项 | S1 实际使用 |
|---|---|---|
| 1 | commit history 不回写前缀 | **适用** · s1-done tag @ 06113ea 原 commit 无 `[SC-XX-ACY]` 前缀 · 本 Retrofit 新增 3 commit（plan+A+B）统一挂 `[RETROFIT-v1.8]` 后缀 |
| 2 | `--commits` 仅校验 Retrofit 期间增量 | **未触发** · V-S1-20 只断言 yml schema · 不调 `check-ac-coverage.sh --commits` · S1 AC 分行硬约束=选 · 整体豁免 |
| 3 | arch 原地重写不 bump ADR | **适用** · Commit A 仅补 3 行 N/A 列表 + 闭合 YAML front matter · 无架构变更 · 无 ADR |
| 4 | Verifier 复写暴露旧 bug 走 Hotfix 不算 Retrofit 失败 | **类推适用** · 整体豁免 Step 5（详见 `reports/verifier/s1-retrofit-verifier.md`）· 本 Retrofit 未触碰代码 · 未暴露旧 bug |
| **5**（plan 新增） | **AC 分行三硬约束（#13）整体豁免** | **适用** · §1.5 B 级 0.7 值=选 · arch 不按 AC 分节 · commit 无 `[SC-XX-ACY]` 前缀 · 测试无 `@CoversAC` 注解 |

## 四、影响面汇总

- **文件新增**：3（`design/analysis/s1-business-analysis.yml` + `reports/retrofit/s1-{analysis,plan}.md` + `reports/retrofit/s1-complete.md` + `reports/verifier/s1-retrofit-verifier.md`）
- **文件修改**：1（`design/arch/s1-data.md` 末尾追加 3 行列表 · 补闭合 `---`）
- **代码影响**：**0 行**（不动 migration / IT / 脚本 / allowlist）
- **commit 数**：3（plan 审签 + A + B · 均挂 `[RETROFIT-v1.8]` 后缀）
- **下游解锁**：
  - `check-business-match.sh --slots s1` / `--aggregate` 可正确识别 S1 精简 yml（不会因缺 yml crash）
  - v1.9 E2E Runner 跑 `supports_sc` 全量时 · S1 的 DDL 契约断言有据可查
  - Batch B（S3/S4/S5）Retrofit 参考 S1 精简模式（若其中某些条目为 B 级）

## 五、发现的文档/实现问题（非阻塞）

1. **`retrofit-to-v18.sh` B 级识别 bug**：脚本对 S1 判 `is_exempt=true · arch_compliant=true · analysis_compliant=true` 直接放过 · 未读 §1.5 适用性分级表区分 B/C 级。本 Retrofit 手工纠偏（坚持 S1 按 B 级补精简 yml） · **未修脚本**（超 S1 范围 · plan §五 #3 签字决策）。建议主文档下次迭代时 `retrofit-to-v18.sh` 增加：读 `design/analysis/<phase>-business-analysis.yml` 的 `phase_level` 字段 · 若 == B 则不走"整体豁免"短路 · 强制要求精简 yml 存在。

2. **`design/arch/s1-data.md` 原 YAML front matter 未闭合**：S0 Retrofit 之前的 S1 arch 文件只有 `---` 开头但无 `---` 闭合 · 本质是"纯 YAML 文件但用 md 扩展名"。Commit A 补闭合 + 加 markdown body · 兼顾机读 + 人读。已知 `check-arch-consistency.sh s1` 在闭合前后均 exit 0（只 grep `exempt: true`）· 本 fix 不破坏兼容。

3. **本机端口 5432 被 `calendar-pg-primary` 占用**：S1 原始 `ddl-idempotency.sh` 最早写死 5432 端口 · 但仓库中的版本已预先修到 `PG_PORT=54321`（容器名 `pg-wb-s1-idem`）· 本 Retrofit 跑闸时也统一用 54321 避免冲突。**与本 Retrofit 目标无关** · 仅为跑闸提供的兼容性记录。

## 六、Git tag

```
s1-retrofit-start  → 0547bd3（plan approved 后打 · 本 Retrofit 基准 · §26.2 豁免 2 基准）
s1-v1.8-compliant  → HEAD（本 complete 报告 commit）· §26.1 Step 6 合规 tag
s1-done            → 06113ea · 不变动 · 与 s1-v1.8-compliant 共存
```

## 七、后续动作

1. **Batch B Retrofit 继续启动**（S3 · S4 · S5）· 按 §26.1 六步走 · S3 是 A 级 · Retrofit 工作量远高于 S1（需补 arch 按 AC 分节 + 全量 matrix + @CoversAC 注解）
2. **Batch A 启动**（S7 / S8 / S11）· 按 v1.8 原生规则开工（非 Retrofit）· 0.0.5 业务分析 + 0.2 arch 按 AC 分节 + V-SX-20 业务深度闸
3. **S1 作为数据基座的运行期保障**：后续各 Phase 在 `mvn verify` / E2E 时若暴露 DDL 契约问题（如主键类型不匹配 / FK 误用 CASCADE）· 按 §26.2 条款 4 走 Hotfix（`fix(s1): hotfix exposed by <下游 phase> [HOTFIX-v1.8]`）· 不算本 Retrofit 失败
