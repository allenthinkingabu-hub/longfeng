**Status:** OPEN
**Severity:** P2 (a11y serious · 屏幕阅读器/keyboard 用户体验)
**Spec ref:** P00.spec.md §12 a11y · "axe 0 serious"
**Discovered in:** sc-p00.spec.ts J12 test
**Filed by:** QA-Orchestrator

## Summary

P00 (登录页 `/auth`) 经 axe-core 扫描存在 **serious 级别** a11y 违规 (with rules `color-contrast` + `aria-progressbar-name` 已 disable)。

## Reproduction

```bash
cd e2e
E2E_TRACK=mock-b BASE_URL=http://localhost:5173 npx playwright test specs/sc-p00.spec.ts --reporter=list
```

Output (J12):
```
Error: expect(serious...).toEqual([])
axe serious violations: [...]
at AuthPage.assertAxeNoSerious (.../e2e/pages/_base.ts:52:85)
```

## Expected

per P00.spec.md §12 a11y + plan §8 红线:
- axe-core scan with wcag2a + wcag2aa tags returns 0 serious or critical violations
- 现有 BasePage `assertAxeNoSerious()` 跑过 SC-11 (P-LANDING) 通过 · 但 SC-P00 fail

## Suspected violations

待 sub-agent 复跑取详情。可能原因（spec §12 提到的几条）：
- consent checkbox label 关联缺失 (label[for] 不匹配 checkbox id)
- 微信按钮 aria-label 缺失（只有可视图标）
- ToS / Privacy 链接 aria-label 不够 descriptive
- form input 缺 label 关联

## Handoff

⏸️ Round 1+ · 优先级低于 P-LANDING/P-GUEST-CAPTURE mockup-diff 修复 · 留 Round 2

## QA Verification Log

### Round 0 — discovered (2026-05-04 18:42)
