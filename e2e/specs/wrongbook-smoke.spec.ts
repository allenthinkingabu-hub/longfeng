// S7 · V-S7-11 · Playwright smoke · 3 H5 路由 × axe 0 violations
// 跑前：pnpm -C frontend/apps/h5 run build && pnpm -C frontend/apps/h5 run preview -- --port 4173
import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

const BASE = process.env.BASE_URL || 'http://localhost:4173';

const ROUTES: Array<{ path: string; name: string }> = [
  { path: '/capture', name: 'SC-01 + SC-07 录入' },
  { path: '/wrongbook', name: 'SC-08 列表' },
  { path: '/wrongbook/demo-id', name: 'SC-02 + SC-03 详情' },
];

for (const r of ROUTES) {
  test(`${r.name} · ${r.path} · axe 0 violations`, async ({ page }) => {
    await page.goto(`${BASE}${r.path}`);
    // 等根节点
    await page.waitForLoadState('networkidle', { timeout: 10_000 });

    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .analyze();

    if (results.violations.length) {
      console.log('axe violations:', JSON.stringify(results.violations, null, 2));
    }
    expect(results.violations).toEqual([]);
  });
}
