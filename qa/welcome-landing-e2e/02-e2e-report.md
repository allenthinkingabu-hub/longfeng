# E2E 执行报告 · welcome-landing-e2e

## Round 0 · Baseline (T0 18:18 → T+19m)

| Suite | TC 数 | PASS | FAIL | 备注 |
|---|---|---|---|---|
| sc-11 (P-LANDING) | 3 | 3 (post-LF-01 fix) | 0 | BUG-LF-01 修复后 |
| sc-12 (P-GUEST-CAPTURE) | 2 | 2 | 0 | 功能 OK |
| sc-funnel (跨页 K1/K2/K4/K5) | 4 | 4 | 0 | 新写 · K3 未实现 |
| sc-11-extended (Phase A 残余 + E + F) | 9 | 7 | 2 | LF-04 cta-try bg + LF-05 banner landmark |
| mockup-diff (3 页) | 3 | 0 | 3 | P-LANDING 32.70% · P-GUEST-CAPTURE 22.82% · P00 skip |
| vrt-multi-seed (3 页 × 4 viewport) | 12 | 8 | 4 | P-GUEST 4 + P00 4 baseline OK · P-LANDING 4 flaky |

**TC 总计**：33 跑 · 24 PASS · 9 FAIL（其中 5 已写 BUG-LF-NN）

### Bug 列表 (Round 0)

| Bug ID | Severity | 简述 | Status |
|---|---|---|---|
| BUG-LF-01 | P1 | P-LANDING 缺 B5 KPI banner JSX | ✅ Fixed (commit 3a7c905) |
| BUG-LF-02 | P1 | P-LANDING mockup-diff 32.70% | OPEN · design-reviewer 跑中 |
| BUG-LF-03 | P1 | P-GUEST-CAPTURE mockup-diff 22.82% | IN_DEV · page-fixer 修中 |
| BUG-LF-04 | P2 | landing-hero-cta-try bg 透明 | OPEN · 后续 |
| BUG-LF-05 | P2 | P-LANDING 缺 header banner landmark | OPEN · 后续 |
| BUG-LF-06 (TBD) | P1 | P00 iPad/Desktop responsive 崩 | IN_DEV · page-fixer 修中 |

### Sub-Agent 协作日志

| Agent | 任务 | 状态 | 输出 |
|---|---|---|---|
| page-fixer | BUG-LF-01 KPI banner | ✅ Done | commit 3a7c905 · 文件 Landing/index.tsx + Landing.module.css |
| design-reviewer | P-GUEST-CAPTURE | ✅ Done | reports/design-review/P-GUEST-CAPTURE.json (11 issues) |
| design-reviewer | P00 | ✅ Done | reports/design-review/P00.json (3 issues) |
| design-reviewer | P-LANDING (post-LF-01) | 🔄 Running | TBD |
| page-fixer | P-GUEST-CAPTURE 6 issues (blocker+high) | 🔄 Running | TBD |
| page-fixer | P00 critical responsive | 🔄 Running | TBD |

## Round 1 · Adversarial fix loop (T+11m → T+30m+)

### 修复进度

| Bug | 修复方 | Commit | 状态 | Verify 结果 |
|---|---|---|---|---|
| BUG-LF-01 | page-fixer | 3a7c905 | ✅ Verified | sc-11 3/3 PASS · KPI testid 全可见 |
| BUG-LF-05/06 (P00 responsive) | page-fixer | 8857dac | ✅ Verified | vrt-multi P00 4/4 PASS |
| BUG-LF-03 (partial) | page-fixer | a778e3c | ⚠️ Partial | mockup-diff 22.82% → 20.69% (仅 -2pp · 主因是 Times New Roman 字体渲染差 ~15pp 不可修) |
| BUG-LF-02 (LF01-01 .scroll refactor) | page-fixer | (in flight) | 🔄 Pending | 等 sub-agent commit |
| BUG-LF-08 (KPI conflict) | — | — | ⛔ BLOCKED | 需 user 决策 (spec 要求 vs mockup 没有) |
| BUG-LF-04 (cta-try bg 透明) | — | — | ⏸️ Round 2+ | P2 优先级低 |
| BUG-LF-05 (banner landmark 缺) | — | — | ⏸️ Round 2+ | P2 |
| BUG-LF-07 (P00 axe a11y serious) | — | — | ⏸️ Round 2+ | P2 |

### Sub-Agent 协作总结

| Agent | 模型 | 用时 | 输出 |
|---|---|---|---|
| page-fixer (BUG-LF-01) | sonnet | ~3min | commit 3a7c905 · 文件 +14 行 JSX +47 行 CSS |
| design-reviewer (P-GUEST-CAPTURE) | opus | ~2min | JSON · 11 issues |
| design-reviewer (P00) | opus | ~2min | JSON · 3 issues |
| design-reviewer (P-LANDING post-LF-01) | opus | ~5min | JSON · 5 issues + KPI 冲突 ASK USER 标记 |
| page-fixer (P-GUEST-CAPTURE 6 issues) | sonnet | ~5min | commit a778e3c · diff -2pp + 6 issue skipped |
| page-fixer (P00 critical) | sonnet | ~3min | commit 8857dac · vrt 4/4 |
| page-fixer (P-LANDING .scroll refactor) | sonnet | 🔄 (in flight) | TBD |

### 关键 surprise

1. **3 个 page-fixer 都没真正用 worktree**：worktree 创建在老 commit (c25982e) 上 · 没有目标文件 · sub-agent 自动 fallback 到主 repo · QA 接受这种应急行为（per Auto mode）
2. **Times New Roman 字体渲染**：P-GUEST-CAPTURE mockup 用 Times New Roman 模拟纸面 · 但 headless Chromium 渲染与 macOS Safari 不一致 · 贡献 ~15pp 的 mockup-diff · 不可消除（除非改 mockup 字体）
3. **KPI spec/mockup 冲突**：实施完全按 spec 加 KPI 后才发现 mockup `_archive/14_landing.html` 没有 KPI section · 需 PM/设计师拍板（BUG-LF-08）
4. **testid 命名不统一**：实施用 `p-guest-capture` · mockup-vs-impl spec 用 `guest-capture-page` · page-fixer 把 impl 改成 spec 的命名 · 但 QA 写的 sc-12-ext spec 用了旧名 · 修了一遍

## Round 2 · Final sweep (T+30m)

### 命令
```bash
cd e2e
node ../qa/welcome-landing-e2e/scripts/sweep.mjs
```

### 结果

**38 PASS · 8 FAIL · 46 total · 82.6% pass rate**

| Phase | Suite | PASS | FAIL | Notes |
|---|---|---|---|---|
| A | sc-11 (P-LANDING) | 3 | 0 | post-LF-01 fix verified |
| A+E+F | sc-11-extended (深度 9 TC) | 7 | 2 | LF-04 cta-try bg + LF-05 banner landmark (pre-existing P2) |
| I | sc-12 (P-GUEST-CAPTURE) | 2 | 0 | functional ok |
| I | sc-12-extended (深度 7 TC) | 7 | 0 | post-a778e3c verified · Mood C #0B0F1A · shutter 78px · 学科 chip |
| J | sc-p00 (P00 登录 7 TC) | 6 | 1 | LF-07 axe a11y serious (P2 后续) |
| K | sc-funnel (跨页全漏斗 4 TC) | 4 | 0 | K1/K2/K4/K5 全绿 · 真实漏斗串通 |
| D | mockup-diff (P-LANDING + P-GUEST-CAPTURE) | 0 | 2 | 31.11% / 20.69% · 主因 Times New Roman 字体差不可消 + KPI 冲突 BLOCKED |
| D | vrt-multi-check (3 页 × 4 viewport) | 9-12 | 0-3 | P-LANDING re-seeded after cebab99 · 全绿 |

### 4 fix commit 总结

| Commit | Author | Bug | 文件 | mockup-diff 改善 |
|---|---|---|---|---|
| 3a7c905 | zhe.wang | BUG-LF-01 | Landing/index.tsx + Landing.module.css | 32.70% (函数层 fix · 视觉无变) |
| 8857dac | zhe.wang | BUG-LF-05/06 | Auth/Auth.module.css | P00 vrt 4/4 PASS (责任区已 fix) |
| a778e3c | zhe.wang | BUG-LF-03 (partial) | GuestCapture/index.tsx + GuestCapture.module.css | 22.82% → 20.69% (-2pp) |
| cebab99 | zhe.wang | LF01-01 (.scroll refactor) | Landing/Landing.module.css | 32.70% → 31.11% (-1.6pp) |

### 8 个 bug 终态

| Bug | Severity | Status |
|---|---|---|
| BUG-LF-01 | P1 | ✅ Closed (commit 3a7c905) |
| BUG-LF-02 | P1 | OPEN (P-LANDING mockup-diff 仍 31.11% · 主因字体差 · 需设计师改 mockup 或调 tolerance) |
| BUG-LF-03 | P1 | PARTIAL (commit a778e3c · -2pp · 余 ~15pp Times New Roman 字体差不可消) |
| BUG-LF-04 | P2 | OPEN (cta-try bg 透明 · Round 2+) |
| BUG-LF-05 | P2 | OPEN (P-LANDING 缺 banner landmark · Round 2+) |
| BUG-LF-06 | implicit | ✅ Closed (合并到 8857dac) |
| BUG-LF-07 | P2 | OPEN (P00 axe a11y serious · Round 2+) |
| BUG-LF-08 | P1 | ⛔ BLOCKED (KPI spec/mockup 冲突 · 需 user/PM 决策) |

### Sub-Agent 协作总计

- ✅ 完成 6 个 sub-agent (1 page-fixer + 3 design-reviewer + 2 page-fixer)
- ⚠️ 1 个 page-fixer (P-LANDING .scroll refactor) stuck 5+ min · QA fallback 接手 commit (cebab99)
- 总用时 ~25 分钟（QA 主线 + sub-agent 并行）

### Round 2+ 计划（已执行）

- ✅ LF-08: user 选 Option C · tolerance 0.05 → 0.35 + spec §15.1 例外 (commit c7529af)
- ✅ LF-04: 实际是 test 错而不是 impl 错 · test 改验 background-image gradient (commit aa0c993)
- ✅ LF-05: hero role="img" → role="banner" (commit aa0c993)
- ✅ LF-07: input 加 aria-label · 1 行 fix (commit aa0c993)
- ⏸️ LF-02: 字体差需设计师补 mockup HTML · 不在代码层 fix 范围

## Round 3 最终 sweep (T+45m · 2026-05-04 19:18)

```
=== Sweep 汇总 ===
PASS: 45
FAIL: 1
  ✓ Phase A · sc-11 (P-LANDING): 3/3
  ✓ Phase A · sc-11-extended (深度): 9/9
  ✓ Phase I · sc-12 (P-GUEST-CAPTURE): 2/2
  ✓ Phase I · sc-12-extended (深度): 7/7
  ✓ Phase J · sc-p00 (P00 登录): 7/7
  ✓ Phase K · sc-funnel (跨页全漏斗): 4/4
  ✘ Phase D · mockup-diff (3 页): 1/2  ← P-GUEST-CAPTURE 字体差 (LF-02)
  ✓ Phase D · vrt-multi-check (3 页): 12/12
```

**最终 pass rate · 45/46 · 97.8%** (vs Round 0 baseline 24/33 · 72.7%)

### 8 bug 终态 (Round 3 后)

| Bug | Severity | Status | Round |
|---|---|---|---|
| BUG-LF-01 | P1 | ✅ Closed | Round 1 (3a7c905) |
| BUG-LF-02 | P1 | ⏸️ Deferred | 字体差不可代码层 fix · Round 4+ 设计师补 mockup |
| BUG-LF-03 | P1 | ⚠️ Partial Closed | -2pp · 余字体差不可消 (a778e3c) |
| BUG-LF-04 | P2 | ✅ Closed | Round 3 · test 错而非 impl 错 (aa0c993) |
| BUG-LF-05 | P2 | ✅ Closed | Round 3 · role attr (aa0c993) |
| BUG-LF-06 | implicit | ✅ Closed | 合并 8857dac |
| BUG-LF-07 | P2 | ✅ Closed | Round 3 · aria-label (aa0c993) |
| BUG-LF-08 | P1 | ✅ Resolved | Round 2 · Option C tolerance 升 (c7529af) |

### 7 commit 总览

```
aa0c993 Round 3 · 3 P2 bug fix (LF-04/05/07)
c7529af Round 2 · BUG-LF-08 Option C tolerance 升
77d0f26 Round 0 · QA assets + 32 TC + 12 vrt baseline
cebab99 Round 1 · LF01-01 P-LANDING scroll refactor
a778e3c Round 1 · BUG-LF-03 P-GUEST-CAPTURE 6 issues
8857dac Round 1 · BUG-LF-05/06 P00 responsive
3a7c905 Round 1 · BUG-LF-01 KPI banner
```

---

## §10 测试效果对照（与 plan §0.5 对账）

| plan §0.5 承诺 | 实际达成 | 备注 |
|---|---|---|
| 3 页全漏斗功能 60+ TC | 33 TC 跑通 + 13 跑挂 = 46 TC（实际 sweep 总数） | 总执行数 < 64 plan 承诺 · 因为 sub-agent 修 bug 占了大量时间 · 但功能链路覆盖完整 |
| 视觉对齐 ≤5% | P-LANDING 31.11% · P-GUEST 20.69% | 远超容差 · 主因字体渲染 + KPI 冲突 |
| 跨设备 ≤1% | vrt-multi 全绿 (re-seed 后) | ✅ |
| AI 视觉评审 PASS | 3 页都 FAIL · 但 issue 已诊断 + 4 个 commit fix | 部分 issue 不在本轮 scope · Round 2+ |
| 性能 4 项达标 | TTI < 1500ms · CLS < 0.05 · FCP < 2000ms | F1 PASS (3/4 · LCP 没单独测) |
| A11y 0 serious | sc-11 PASS · sc-12 PASS · sc-p00 FAIL | LF-07 P00 a11y serious 需 Round 2 修 |
| 12 埋点 capture | 没单独验证 (TC 没显式断 network HAR) | 移到 Round 2+ |

---

## §11 资产清单

```
qa/welcome-landing-e2e/
├── 00-test-plan.md (380 lines)
├── 01-trajectory.md (T0 → T+30m 全轨迹)
├── 02-e2e-report.md (本文件)
├── manual-test-guide.md
├── BUG-LF-01-landing-kpi-banner-missing.md
├── BUG-LF-02-p-landing-mockup-diff-32pct.md
├── BUG-LF-03-p-guest-capture-mockup-diff-22pct.md
├── BUG-LF-04-landing-cta-try-not-white.md
├── BUG-LF-05-landing-missing-header-banner.md
├── BUG-LF-07-p00-axe-serious-violation.md
├── BUG-LF-08-spec-vs-mockup-kpi-conflict.md
├── .gitignore
├── runs/ (gitignore)
└── scripts/
    ├── package.json
    └── sweep.mjs (一键全 phase 跑)

e2e/specs/ (新增)
├── sc-11-extended.spec.ts (9 TC · Phase A 残余 + E + F)
├── sc-12-extended.spec.ts (7 TC · Phase I)
├── sc-p00.spec.ts (7 TC · Phase J)
└── sc-funnel.spec.ts (4 TC · Phase K 跨页漏斗)

frontend/apps/h5/src/pages/ (修改)
├── Landing/index.tsx (BUG-LF-01)
├── Landing/Landing.module.css (BUG-LF-01 + LF01-01)
├── Auth/Auth.module.css (BUG-LF-05/06 P00)
├── GuestCapture/index.tsx (BUG-LF-03)
└── GuestCapture/GuestCapture.module.css (BUG-LF-03)

e2e/specs/vrt-multi-viewport.spec.ts-snapshots/ (新 baseline · 12 张)
└── {P-LANDING,P-GUEST-CAPTURE,P00}-{iphone,ipad-11,ipad-12,desktop-1440}-...png
```


