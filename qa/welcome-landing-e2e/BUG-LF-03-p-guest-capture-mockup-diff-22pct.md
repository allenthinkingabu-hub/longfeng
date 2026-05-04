**Status:** OPEN (待 design-reviewer 给具体 issue list)
**Severity:** P1
**Spec ref:** P-GUEST-CAPTURE.spec.md §13 + §15
**Discovered in:** Round 0 baseline · `mockup-vs-impl.spec.ts:75` P-GUEST-CAPTURE grep
**Filed by:** QA-Orchestrator

## Summary

P-GUEST-CAPTURE (`/guest/capture`) 视觉 vs `_archive/15_guest_capture.html` mockup 像素差 **22.82%**，远超 5% 容差。

## Reproduction

```bash
cd /Users/allenwang/build/longfeng-wrongbook/e2e
E2E_TRACK=design npx playwright test --grep "@mockup-diff P-GUEST-CAPTURE" --reporter=list
```

## Evidence

- `e2e/reports/mockup-diff/P-GUEST-CAPTURE-mockup.png`
- `e2e/reports/mockup-diff/P-GUEST-CAPTURE-impl.png`
- `e2e/reports/mockup-diff/P-GUEST-CAPTURE-diff.png`

## Suspected root cause

P-GUEST-CAPTURE 功能层 sc-12 全 PASS（拍题流程正常）· 但视觉偏差大。可能：
- Mood C 全屏 #0B0F1A 实色实现颜色错（spec §15 声明）
- 取景器纸面 viewfinder 旋转角度 / 尺寸偏离 mockup
- 黄色检测框 corners (#FFD166) 渲染缺失或位置不对
- 配额 banner 位置偏移
- 学科 chip 视觉样式偏差

⏸️ **等 design-reviewer (background subagent) 出 JSON 报告后填**

## Handoff to Dev Agent

待 design-reviewer 报告出后，按 issue list 顺序 dispatch page-fixer。

## QA Verification Log

### Round 0 — discovered (2026-05-04 18:21)
- 等 design-reviewer 给精准 issue
