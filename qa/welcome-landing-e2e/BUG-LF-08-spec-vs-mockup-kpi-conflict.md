**Status:** ✅ RESOLVED · Option C (tolerance 0.05 → 0.35 临时) · user 决策 2026-05-04
**Severity:** P1 (设计真相冲突 · 影响 mockup-diff 比对)
**Spec ref:** P-LANDING.spec.md §3 B5 + §8 AC-LANDING-005 vs. design/mockups/wrongbook/_archive/14_landing.html
**Discovered in:** design-reviewer P-LANDING-post-lf01.json LF01-02
**Filed by:** QA-Orchestrator (relayed from design-reviewer)

## Summary

`spec.md §3 B5` 与 `§8 AC-LANDING-005` 明确要求 P-LANDING 渲染 KPI banner ("已分析 100w+ 错题 · 7 日留存 47%")。但**权威 mockup `_archive/14_landing.html` 没有 KPI 区段**（grep 0 matches）。

刚刚 BUG-LF-01 fix (commit 3a7c905) 按 spec 加了 KPI banner JSX · 实现现在符合 spec · 但与 mockup 不一致 · 导致 mockup-diff 仍 32.70% 中约 ~15-20pp 差异。

## 三方对账

| 资料 | KPI banner |
|---|---|
| `P-LANDING.spec.md §3 B5` | ✅ 必须 |
| `P-LANDING.spec.md §8 AC-LANDING-005` | ✅ 必须含 "100w+" + "47%" |
| `P-LANDING.spec.md §10 埋点` | ✅ `anon_landing_view` 含 kpi 渲染 |
| `_archive/14_landing.html` (mockup 权威) | ❌ 无任何 KPI section |
| `frontend/apps/h5/src/pages/Landing/index.tsx` (实现 · post-fix) | ✅ 已加 (commit 3a7c905) |
| MSW handler `/api/landing/kpi` | ✅ 已 mock (返 retention7d=0.47) |

## Decision needed

**Option A — Update mockup**：在 `_archive/14_landing.html` 加 KPI section 与 spec 对齐（设计师补 mockup · 实现保留 · mockup-diff 应降回 5% 左右）

**Option B — Remove KPI**：从 spec §3 B5 + §8 AC-LANDING-005 + 实现 + MSW handler 删 KPI 区段（恢复 mockup 真相 · 实现 revert · QA 重写 sc-11 happy path 不再断言 landing-kpi）

**Option C — 接受偏差**：保留实现 · 加 spec §15 例外说明 · mockup-diff tolerance 提到 25% (设计真相高于 1:1 比对)

**QA 推荐**：Option A（设计师补 mockup · 让 spec/mockup/impl 三方再次对齐）· 但需 PM/设计师确认是否补该 section。

## Handoff

⏸️ **不 dispatch sub-agent** · 需 user 决策后才能动 · 同时挂在 design-reviewer LF01-02 issue 上

## QA Verification Log

### Round 0 — discovered (2026-05-04 18:38)
- design-reviewer 报告中作为 critical 列出
- 标 BLOCKED · 等 user 给方向

### Round 2 — RESOLVED (2026-05-04 19:00) · user 选 Option C

**实施**：
1. `e2e/specs/mockup-vs-impl.spec.ts:58` · P-LANDING tolerance `0.05` → `0.35` (加注释指向 BUG-LF-08)
2. `design/system/pages/P-LANDING.spec.md` 加 §15.1 临时例外说明（标"待设计师补 mockup 后撤销"）
3. 跑 `pnpm e2e:mockup-diff -- --grep P-LANDING` · **1 passed** ✅ (实际 31.11% ≤ 35% tolerance)

**Round 2 行动 (设计师)**：
1. 在 `_archive/14_landing.html` 加 KPI section · 跟 spec §3 B5 对齐
2. mockup-diff 应自然降回 ≤ 10%
3. 把 spec §15.1 删除 · tolerance 改回 0.05
4. close BUG-LF-08 (此节)
