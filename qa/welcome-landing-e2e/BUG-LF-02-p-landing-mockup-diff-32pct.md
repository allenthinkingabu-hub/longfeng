**Status:** OPEN (likely caused by BUG-LF-01 · 待 fix 后 re-measure)
**Severity:** P1
**Spec ref:** P-LANDING.spec.md §13 mockup 锚定 + §15 实现边界 + CLAUDE.md §2.11 自检
**Discovered in:** Round 0 baseline · `mockup-vs-impl.spec.ts:75` P-LANDING grep
**Filed by:** QA-Orchestrator

## Summary

P-LANDING (`/welcome`) 视觉 vs `_archive/14_landing.html` mockup 像素差 **32.70%**，远超 5% 容差。

## Reproduction

```bash
cd /Users/allenwang/build/longfeng-wrongbook/e2e
E2E_TRACK=design npx playwright test --grep "@mockup-diff P-LANDING" --reporter=list
```

输出：
```
Error: P-LANDING pixel diff 32.70% > 5%
```

## Evidence

- mockup screenshot: `e2e/reports/mockup-diff/P-LANDING-mockup.png`
- impl screenshot: `e2e/reports/mockup-diff/P-LANDING-impl.png`
- diff overlay: `e2e/reports/mockup-diff/P-LANDING-diff.png`

## Suspected root cause

主要是 BUG-LF-01 (整个 B5 KPI banner 缺失) 的视觉副作用 —— 缺一个 banner 大约会贡献 5-10pp 的像素差。

剩余的 ~22pp 可能来源（需 BUG-LF-01 fix 后 re-measure 才能确定）：
- 三步漫画的 4 色梯度 icon 实施偏差
- 样例卡 conic 彩虹色与 mockup 不完全一致
- 字体字号 / 行距偏差
- 章节间距 spacing 与 mockup 不一致

## Handoff to Dev Agent

⏸️ **暂不 dispatch · 等 BUG-LF-01 fix 后 QA 重 measure**：
- 如果 fix 后 diff% 降到 5% 以内 → close as DUP-OF-LF-01
- 如果 fix 后还 >5% → dispatch design-reviewer 给精准 issue list，再 dispatch page-fixer

## QA Verification Log

### Round 0 — discovered (2026-05-04 18:21)
- 等 BUG-LF-01 fix 完后 re-measure
