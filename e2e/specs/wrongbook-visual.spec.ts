// S7 · V-S7-20d · 视觉回归 · Playwright screenshot snapshot · 1440×900 desktop + 375×667 H5
// 基线策略：首次运行写 baseline · 后续运行 toMatchSnapshot 对比 · 阈值通过 maxDiffPixels 控制
// 注：与 design/system/screenshots/baseline/P-XX-*.png 不直接对比（那是 Sd.3 HTML 高保真）·
//     此处对 S7 H5 自身渲染做回归（防 ui-kit 升级 / token 改动 偏移）
import { test, expect } from '@playwright/test';

const BASE = process.env.BASE_URL || 'http://localhost:4173';

const ROUTES: Array<{ path: string; name: string; threshold: number }> = [
  { path: '/capture', name: 'capture', threshold: 800 },          // 富交互页 · 阈值松
  { path: '/wrongbook', name: 'wrongbook-list', threshold: 300 }, // 列表页 · 阈值紧
  { path: '/wrongbook/demo-id', name: 'wrongbook-detail', threshold: 800 },
];

for (const r of ROUTES) {
  test(`visual · ${r.name} · 375×667 H5`, async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 });
    await page.goto(`${BASE}${r.path}`);
    await page.waitForLoadState('networkidle', { timeout: 5_000 }).catch(() => {});
    // 隐藏可能的动画跳变（loading spinner 等）
    await page.evaluate(() => {
      const style = document.createElement('style');
      style.textContent = '*, *::before, *::after { animation: none !important; transition: none !important; }';
      document.head.appendChild(style);
    });
    await expect(page).toHaveScreenshot(`${r.name}-h5.png`, { maxDiffPixels: r.threshold });
  });
}
