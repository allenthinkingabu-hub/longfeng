**Status:** ✅ RESOLVED · Round 3 (2026-05-04 19:15)
**Severity:** P2 (a11y · 屏幕阅读器无法识别 hero 区)
**Spec ref:** P-LANDING.spec.md §12 a11y · 第一条 "Landmarks: <header role='banner'> (B2)"
**Discovered in:** sc-11-extended.spec.ts E2 test
**Filed by:** QA-Orchestrator

## Summary

P-LANDING 实现里完全缺失 `<header role="banner">` landmark · 屏幕阅读器无法跳转到 hero 区。

## Reproduction

```bash
cd e2e
E2E_TRACK=mock-b BASE_URL=http://localhost:5173 npx playwright test --grep "@sc-11-ext.*E2" --reporter=list
```

输出：
```
Error: P-LANDING 应有至少 1 个 banner landmark
Expected: >= 1
Received: 0
```

静态确认：
```bash
grep -nE "<header|role=\"banner\"" frontend/apps/h5/src/pages/Landing/index.tsx
# 无任何匹配
```

## Expected

per spec §12 a11y:
> Landmarks: `<header role="banner">` (B2), `<main role="main">` (B3-B6), `<footer role="contentinfo">` (B6 家长入口)

B2 hero section 应该用 `<header>` 标签或 `role="banner"` 属性。

## Actual

实现里 hero block 用了：
- `<navigation>` (顶部 nav OK)
- `<main>` (line 271 OK)
- 但没有任何 `<header>` 或 `role="banner"`

## Handoff to Dev Agent

简单 fix: `Landing/index.tsx` line 215 附近 (`<section data-testid="landing-hero">`) 改为：
```tsx
<header data-testid="landing-hero" role="banner" ...>
```
or 包一个 `<header>` 在外。

## QA Verification Log

### Round 0 — discovered (2026-05-04 18:35)

### Round 3 — RESOLVED (2026-05-04 19:15)

**实施**：`Landing/index.tsx:215` `<div className={s.hero} role="img">` → `<div className={s.hero} role="banner">` · 1 个 attribute 改动。

**Verify**：sc-11-extended E2 console: `[a11y] main=1 · banner=1` ✅
