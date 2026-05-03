/**
 * C 轨 · VRT baseline · 19 页一次性截图（plan §3.2 · plan §6.3）
 *
 * 跑法：
 *   pnpm e2e:vrt          # 首次：生成 baseline（vrt/<spec>/<name>.png）
 *   pnpm e2e:vrt-check    # CI：与 baseline diff · ≤ 1% 通过
 *
 * Tag：@vrt · 默认 mock-b 不跑 · 仅 vrt 轨触发
 */
import { test, expect } from '@playwright/test';

const VRT_PAGES: Array<{ id: string; route: string; tolerance: number }> = [
  { id: 'P00',           route: '/auth',            tolerance: 0.01 },
  { id: 'P-HOME',        route: '/home',            tolerance: 0.01 },
  { id: 'P02',           route: '/capture',         tolerance: 0.01 },
  { id: 'P03',           route: '/analyzing/demo',  tolerance: 0.01 },
  { id: 'P04',           route: '/result/demo',     tolerance: 0.01 },
  { id: 'P05',           route: '/wrongbook',       tolerance: 0.01 },
  { id: 'P06',           route: '/wrongbook/demo',  tolerance: 0.01 },
  { id: 'P07',           route: '/review',          tolerance: 0.01 },
  { id: 'P08',           route: '/review/demo/exec', tolerance: 0.01 },
  { id: 'P09',           route: '/review/demo/done', tolerance: 0.01 },
  { id: 'P10',           route: '/calendar',        tolerance: 0.01 },
  { id: 'P11',           route: '/event/demo',      tolerance: 0.01 },
  { id: 'P12',           route: '/notifications',   tolerance: 0.01 },
  { id: 'P13',           route: '/me/settings',     tolerance: 0.01 },
  { id: 'P-LANDING',     route: '/welcome',         tolerance: 0.01 },
  { id: 'P-GUEST-CAPTURE', route: '/guest/capture', tolerance: 0.01 },
  { id: 'P-SHARED',      route: '/s/demo-token',    tolerance: 0.01 },
  { id: 'P-OBSERVER',    route: '/observer',        tolerance: 0.03 }, // P1 skeleton
  { id: 'P-WELCOMEBACK', route: '/welcomeback',     tolerance: 0.03 }, // P1 skeleton
];

test.describe('@vrt · 19 页 baseline', () => {
  for (const p of VRT_PAGES) {
    test(`@vrt ${p.id} · ${p.route}`, async ({ page }) => {
      await page.goto(p.route);
      await page.waitForLoadState('networkidle', { timeout: 15_000 });
      await expect(page).toHaveScreenshot(`${p.id}.png`, {
        maxDiffPixelRatio: p.tolerance,
        fullPage: true,
        animations: 'disabled',
      });
    });
  }
});
