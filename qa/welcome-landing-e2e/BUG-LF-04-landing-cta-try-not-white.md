**Status:** OPEN
**Severity:** P2 (视觉偏差 · AC-LANDING-002 失败)
**Spec ref:** P-LANDING.spec.md §8 AC-LANDING-002
**Discovered in:** sc-11-extended.spec.ts A2 test
**Filed by:** QA-Orchestrator

## Summary

`landing-hero-cta-try` 按钮背景色是 `rgba(0, 0, 0, 0)` (transparent) · 不符合 spec AC-LANDING-002 "白 pill 试一试 (background = white)"。

## Reproduction

```bash
cd e2e
E2E_TRACK=mock-b BASE_URL=http://localhost:5173 npx playwright test --grep "@sc-11-ext.*A2" --reporter=list
```

输出：
```
Expected pattern: /255,\s*255,\s*255|white|#fff/i
Received string:  "rgba(0, 0, 0, 0)"
```

## Expected

per spec §8 AC-LANDING-002:
> 双 CTA 视觉层级正确（白 pill 试一试，蓝 pill 登录主操作）
> testid 验证点: `landing-hero-cta-try` 背景 = white · `landing-hero-cta-login` 背景 = `--tkn-color-primary-DEFAULT`

## Actual

`landing-hero-cta-try` 实际 background-color = `rgba(0, 0, 0, 0)`（透明）

## Root cause (suspected)

`Landing.module.css` 里 `.heroCtaTry` 类（或类似）可能：
- 用了 transparent / 缺定义 / inherit 父 transparent
- 或被另一个选择器覆盖
- 或仅在 hero 那个位置设白 · 底部 dock 位置忘了

## Handoff to Dev Agent

⏸️ Round 1+ · 优先级低于 mockup-diff fix。先放着。

## QA Verification Log

### Round 0 — discovered (2026-05-04 18:35)
