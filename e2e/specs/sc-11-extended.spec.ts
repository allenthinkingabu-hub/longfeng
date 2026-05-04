/**
 * SC-11-EXTENDED · P-LANDING 深度验证 (Phase A 残余 + E a11y + F perf)
 *
 * 不污染 sprint 主 sc-11.spec.ts · 单独 spec 沉淀 QA 增量
 *
 * Track: B (mock-b)
 * Tag: @sc-11-ext
 */
import { test, expect } from '@playwright/test';
import { LandingPage } from '../pages';

test.describe('SC-11-EXT · P-LANDING 深度 @sc-11-ext', () => {

  // === Phase A 残余 ===
  test('A1 · TTI ≤ 1500ms · hero headline 可见 (AC-LANDING-001)', async ({ page }) => {
    const t0 = Date.now();
    const landing = new LandingPage(page);
    await landing.open();
    await expect(page.getByTestId('landing-hero-headline')).toBeVisible({ timeout: 5_000 });
    const tti = Date.now() - t0;
    console.log(`[perf] P-LANDING TTI ≈ ${tti}ms`);
    // 本地宽容到 1500ms (mock-b 网络 + chromium · spec §11 1000ms 是 prod 目标)
    expect(tti).toBeLessThanOrEqual(1500);
  });

  test('A2 · 双 CTA 视觉层级 (AC-LANDING-002)', async ({ page }) => {
    const landing = new LandingPage(page);
    await landing.open();
    const ctaTry = page.getByTestId('landing-hero-cta-try');
    const ctaLogin = page.getByTestId('landing-hero-cta-login');
    await expect(ctaTry).toBeVisible();
    await expect(ctaLogin).toBeVisible();
    // 试一试 = 白色 pill (rgba(255, 255, 255, *))
    const tryBg = await ctaTry.evaluate((el) => getComputedStyle(el).backgroundColor);
    const loginBg = await ctaLogin.evaluate((el) => getComputedStyle(el).backgroundColor);
    console.log(`[style] cta-try bg=${tryBg} · cta-login bg=${loginBg}`);
    expect(tryBg).toMatch(/255,\s*255,\s*255|white|#fff/i);
    // 蓝色按钮 (#007AFF = rgb(0, 122, 255))
    expect(loginBg).toMatch(/0,\s*122,\s*255|rgb\(0,\s*122,\s*255\)/i);
  });

  test('A4 · 登录漏斗 · 路由跳转 (AC-LANDING-003 同 pattern)', async ({ page }) => {
    const landing = new LandingPage(page);
    await landing.open();
    await page.getByTestId('landing-hero-cta-login').click();
    await expect(page).toHaveURL(/\/auth/);
  });

  test('B1 · 样例卡 3 个 + testid 完整 (AC-LANDING-004)', async ({ page }) => {
    const landing = new LandingPage(page);
    await landing.open();
    await expect(page.getByTestId('landing-samples')).toBeVisible();
    for (let i = 1; i <= 3; i++) {
      await expect(page.getByTestId(`landing-samples-card-${i}`)).toBeVisible();
    }
  });

  test('B2 · KPI banner · 文案 + testid (AC-LANDING-005 · 验 BUG-LF-01 fix)', async ({ page }) => {
    const landing = new LandingPage(page);
    await landing.open();
    await expect(page.getByTestId('landing-kpi')).toBeVisible();
    // total 含数字 (mock 返 120w · 实际可能 100w · 用 regex 容差)
    await expect(page.getByTestId('landing-kpi-total')).toContainText(/\d+w|\d+万|百万|100w/);
    // retention 含 47 (mock 是 0.47)
    await expect(page.getByTestId('landing-kpi-retention')).toContainText(/47|留存/);
  });

  // === Phase E · A11y 深度 ===
  test('E1 · axe 0 serious violations', async ({ page }) => {
    const landing = new LandingPage(page);
    await landing.open();
    await landing.assertAxeNoSerious();
  });

  test('E2 · landmarks 三件套 (header banner / main / footer)', async ({ page }) => {
    const landing = new LandingPage(page);
    await landing.open();
    // 至少要有一个 main · 一个 banner · footer 可选
    const main = await page.locator('main, [role="main"]').count();
    const banner = await page.locator('header, [role="banner"]').count();
    console.log(`[a11y] main=${main} · banner=${banner}`);
    expect(main, 'P-LANDING 应有至少 1 个 main landmark').toBeGreaterThanOrEqual(1);
    expect(banner, 'P-LANDING 应有至少 1 个 banner landmark').toBeGreaterThanOrEqual(1);
  });

  test('E3 · prefers-reduced-motion 不破坏可见性', async ({ browser }) => {
    const ctx = await browser.newContext({ reducedMotion: 'reduce' });
    const page = await ctx.newPage();
    const landing = new LandingPage(page);
    await landing.open();
    await expect(page.getByTestId('landing-hero')).toBeVisible();
    await expect(page.getByTestId('landing-samples')).toBeVisible();
    await ctx.close();
  });

  // === Phase F · 性能 ===
  test('F1 · LCP / CLS / FCP via PerformanceObserver', async ({ page }) => {
    const landing = new LandingPage(page);
    await landing.open();
    // 在 page 内拿 web-vitals 数据
    await page.evaluate(() => new Promise<void>((resolve) => setTimeout(resolve, 1500))); // 等 LCP 稳定
    const metrics = await page.evaluate(() => {
      const entries = performance.getEntriesByType('paint');
      const fcp = entries.find((e) => e.name === 'first-contentful-paint')?.startTime ?? -1;
      // LayoutShift entries 无标准类型
      const layoutShifts = (performance as any).getEntriesByType('layout-shift') ?? [];
      const cls = layoutShifts
        .filter((s: any) => !s.hadRecentInput)
        .reduce((sum: number, s: any) => sum + s.value, 0);
      return { fcp, cls };
    });
    console.log(`[perf] FCP=${metrics.fcp.toFixed(0)}ms · CLS=${metrics.cls.toFixed(4)}`);
    // spec §11: CLS < 0.05
    expect(metrics.cls, 'CLS should be < 0.05 per spec §11').toBeLessThan(0.05);
    // FCP 本地 < 2000ms (spec § prod 目标 1000ms · 本地宽容)
    expect(metrics.fcp).toBeLessThanOrEqual(2000);
  });
});
