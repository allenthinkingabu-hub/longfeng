# Trajectory · welcome-landing-e2e

> append-only · 跨 session 持久化记忆 · 时间用 ISO 8601 (Asia/Shanghai)

| Time | Bug | Actor | Event |
|---|---|---|---|
| 2026-05-04 18:18 | — | QA-Orchestrator | Plan v2 锁定 · 64 TC · 范围 P-LANDING + P-GUEST-CAPTURE + P00 全漏斗 · headless+trace 模式 |
| 2026-05-04 18:18 | — | QA-Orchestrator | qa/welcome-landing-e2e/ 目录结构创建 · runs/ + scripts/ |
| 2026-05-04 18:18 | — | QA-Orchestrator | dev server 探测：5173 已运行 (PID 42465+78000) · 复用 |
| 2026-05-04 18:19 | BUG-LF-01 | QA-Orchestrator | sc-11 baseline · 1/3 fail · `landing-kpi` testid 缺失 (整个 B5 KPI banner block 未实现) · 写 BUG-LF-01 |
| 2026-05-04 18:20 | — | QA-Orchestrator | sc-12 baseline · 2/2 PASS ✅ · P-GUEST-CAPTURE 功能链路 OK |
| 2026-05-04 18:21 | TBD | QA-Orchestrator | mockup-diff baseline · 16/17 fail · P-LANDING + P-GUEST-CAPTURE 都 >5% · 待 design-reviewer 给具体诊断 |
| 2026-05-04 18:22 | BUG-LF-01 | QA-Orchestrator | dispatch page-fixer sub-agent (background · agentId a1f175...) 在 worktree 修 B5 KPI |
| 2026-05-04 18:23 | — | QA-Orchestrator | vrt-multi 12 baseline 全部 seed (3 页 × 4 viewport · P-LANDING flaky 待 KPI fix 后重 seed) |
| 2026-05-04 18:24 | — | QA-Orchestrator | dispatch design-reviewer × 2 (P-GUEST-CAPTURE + P00) background · P-LANDING 留到 BUG-LF-01 fix 后再派 |
| 2026-05-04 18:27 | BUG-LF-01 | page-fixer (sub-agent) | 完成 commit 3a7c905 · 加 B5 KPI banner JSX (KPI data + MSW 早已存在 · 仅 JSX 漏渲染) · sc-11 验 RED→GREEN |
| 2026-05-04 18:28 | — | QA-Orchestrator | trust-but-verify · QA 自跑 sc-11 3/3 PASS ✅ (commit 3a7c905 真实) |
| 2026-05-04 18:30 | BUG-LF-02 | QA-Orchestrator | re-measure · P-LANDING mockup-diff 仍 32.70% · KPI fix 没降 · 主因不是 KPI |
| 2026-05-04 18:31 | — | QA-Orchestrator | sc-funnel.spec.ts (Phase K1/K2/K4/K5) 4/4 PASS ✅ |
| 2026-05-04 18:32 | — | design-reviewer P-GUEST-CAPTURE | FAIL · 11 issues (2 blocker / 4 high / 3 medium / 2 low) · 主因 .phone width:100% 导致内部 absolute 飘 |
| 2026-05-04 18:33 | — | design-reviewer P00 | FAIL · 1 critical (iPad/Desktop login card 拉成 700-1400px 宽 · hero 仅 1/4 屏) + 1 major + 1 minor |
| 2026-05-04 18:34 | — | QA-Orchestrator | dispatch 3 sub-agent · design-reviewer P-LANDING (post-LF-01) + page-fixer P-GUEST-CAPTURE 11 + page-fixer P00 critical |
| 2026-05-04 18:35 | BUG-LF-04 / 05 | QA-Orchestrator | sc-11-extended.spec.ts 9 TC · 7/9 PASS · 新发现 cta-try bg 透明 (LF-04) + 缺 header banner landmark (LF-05) |
| 2026-05-04 18:36 | — | QA-Orchestrator | 等 3 background sub-agent · 期间不发新依赖 fix 的 TC |
| 2026-05-04 18:31 | BUG-LF-05/06 | page-fixer P00 (sub-agent) | commit 8857dac · .scroll/.loginCard max-width:520px + .hero clamp · vrt-multi P00 4/4 PASS |
| 2026-05-04 18:33 | BUG-LF-03 (partial) | page-fixer P-GUEST-CAPTURE (sub-agent) | commit a778e3c · max-width:393px + consent card + sources×3 + sidebtn×2 + shutter rework · diff 22.82% → 20.69% (剩 ~15pp 是 Times New Roman 字体渲染差不可消) · 6 issue 跳过 (medium/low) |
| 2026-05-04 18:34 | — | design-reviewer P-LANDING (sub-agent) | FAIL · 5 issues · 主因 .scroll absolute inner-scroll 截断 60% 内容 · KPI 冲突 ASK USER (BUG-LF-08) |
| 2026-05-04 18:35 | BUG-LF-08 | QA-Orchestrator | 写 BUG-LF-08 · 标 BLOCKED 等 user 决策 · 同时 dispatch page-fixer P-LANDING .scroll refactor (background) |
| 2026-05-04 18:36 | — | QA-Orchestrator | re-seed P-LANDING vrt-multi baseline 4/4 (post-LF-01 stable state) |
| 2026-05-04 18:38 | — | QA-Orchestrator | 测得 P-LANDING mockup-diff 49.87% (恶化 · sub-agent 半成品 .scroll 写入 dev server HMR · 暂时态) · P-GUEST 20.69% (符合 sub-agent reported) |
| 2026-05-04 18:39 | BUG-LF-04 | QA-Orchestrator | testid 命名同步 · sub-agent 改 p-guest-capture → guest-capture-page · 同步修 sc-12-ext + sc-funnel |
| 2026-05-04 18:40 | BUG-LF-07 | QA-Orchestrator | sc-p00 6/7 PASS · J12 axe a11y serious 写 BUG-LF-07 (P2 后续) · J1/J2/J3/J8/J9 + iPhone responsive 全 PASS |
| 2026-05-04 18:42 | — | QA-Orchestrator | 写 qa/scripts/{package.json,sweep.mjs} · 沉淀一键 sweep 工具 · 等 P-LANDING refactor sub-agent commit |
