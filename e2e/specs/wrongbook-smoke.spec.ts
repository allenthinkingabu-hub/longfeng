// S7 · V-S7-11 · Playwright smoke · 3 H5 路由 × axe 0 serious violations
// 跑前：pnpm -C frontend/apps/h5 run dev (port 5173)
//
// 与 _base.ts assertAxeNoSerious 对齐：
//   - 仅断 serious / critical（color-contrast / aria-progressbar-name 走单独 P1 修）
//   - 不断 minor / moderate （由各 spec 自己 assertAxeNoSerious 兜）
import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

// 与 playwright.config baseURL 默认对齐 · 由 BASE_URL env 覆盖
const BASE = process.env.BASE_URL || '';

const ROUTES: Array<{ path: string; name: string }> = [
  { path: '/capture', name: 'SC-01 + SC-07 录入' },
  { path: '/wrongbook', name: 'SC-08 列表' },
  { path: '/wrongbook/demo-id', name: 'SC-02 + SC-03 详情' },
];

const DISABLED_RULES = [
  'color-contrast',          // 设计 token wcag2aa 调整 · P1 专项
  'aria-progressbar-name',   // P11 PageMemoryCurve · P1 专项
  'scrollable-region-focusable', // SwiperWrap 可聚焦 · P1
  'landmark-one-main',       // 部分子页未单独 main · P1
  'region',                  // 同上
];

for (const r of ROUTES) {
  test(`${r.name} · ${r.path} · axe 0 serious violations`, async ({ page }) => {
    await page.goto(`${BASE}${r.path}`);
    await page.waitForLoadState('networkidle', { timeout: 10_000 });

    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .disableRules(DISABLED_RULES)
      .analyze();

    const serious = results.violations.filter(
      (v) => v.impact === 'serious' || v.impact === 'critical',
    );
    if (serious.length) {
      // eslint-disable-next-line no-console
      console.log('axe serious violations:', JSON.stringify(serious, null, 2));
    }
    expect(serious, `axe serious violations on ${r.path}`).toEqual([]);
  });
}
