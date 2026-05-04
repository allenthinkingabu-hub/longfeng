/**
 * Phase D1 · A 机制 · 多 viewport VRT 扩展
 *
 * 跑法:
 *   pnpm e2e:vrt-multi --update-snapshots   # 首次: 19 × 4 = 76 baseline
 *   pnpm e2e:vrt-multi-check                 # 之后: 与 baseline diff
 *
 * 与 vrt-baseline.spec.ts 区别:
 *   - vrt-baseline: 19 页 · iPhone 15 Pro 单 viewport (从 playwright.config 继承)
 *   - vrt-multi-viewport (本 spec): 19 页 × 4 viewport · catch 响应式破洞
 *
 * Tag: @vrt-multi · 与 @vrt 分离 · 互不干扰
 *
 * Plan: docs/DESIGN-AUDIT-SYSTEM-PLAN.md §Layer1 A
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
  { id: 'P-OBSERVER',    route: '/observer',        tolerance: 0.03 },
  { id: 'P-WELCOMEBACK', route: '/welcomeback',     tolerance: 0.03 },
];

const VIEWPORTS: Array<{ name: string; width: number; height: number }> = [
  { name: 'iphone-15-pro', width: 393,  height: 852  },
  { name: 'ipad-pro-11',   width: 834,  height: 1194 },
  { name: 'ipad-pro-12',   width: 1024, height: 1366 },
  { name: 'desktop-1440',  width: 1440, height: 900  },
];

test.describe('@vrt-multi · 19 页 × 4 viewport baseline', () => {
  for (const p of VRT_PAGES) {
    for (const vp of VIEWPORTS) {
      test(`@vrt-multi ${p.id} @ ${vp.name} · ${p.route}`, async ({ page }) => {
        await page.setViewportSize({ width: vp.width, height: vp.height });
        await page.goto(p.route);
        await page.waitForLoadState('networkidle', { timeout: 15_000 });
        await expect(page).toHaveScreenshot(`${p.id}-${vp.name}.png`, {
          maxDiffPixelRatio: p.tolerance,
          fullPage: true,
          animations: 'disabled',
        });
      });
    }
  }
});
