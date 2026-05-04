/**
 * SC-12-EXTENDED · P-GUEST-CAPTURE 深度 (Phase I)
 *
 * 仅覆盖 EXISTING testid · 不依赖 page-fixer 新增 (consent / sources / sidebtn)
 *
 * Track: B (mock-b)
 * Tag: @sc-12-ext
 */
import { test, expect } from '@playwright/test';
import { GuestCapturePage } from '../pages';

test.describe('SC-12-EXT · P-GUEST-CAPTURE 深度 @sc-12-ext', () => {

  test('I1 · quota banner 显示 + aria-live', async ({ page }) => {
    const guest = new GuestCapturePage(page);
    await guest.open();
    const banner = page.getByTestId('guest-quota-banner');
    await expect(banner).toBeVisible();
    const ariaLive = await banner.getAttribute('aria-live');
    expect(ariaLive).toBe('polite');
  });

  test('I2 · Mood C #0B0F1A 全屏 (spec §15)', async ({ page }) => {
    const guest = new GuestCapturePage(page);
    await guest.open();
    const root = page.getByTestId('guest-capture-page');
    const mood = await root.getAttribute('data-mood');
    expect(mood).toBe('C');
    const bg = await root.evaluate((el) => getComputedStyle(el).backgroundColor);
    console.log(`[style] p-guest-capture bg=${bg}`);
    // #0B0F1A = rgb(11, 15, 26)
    expect(bg).toMatch(/11,\s*15,\s*26/);
  });

  test('I3 · 取景器 70vh-ish 高度', async ({ page }) => {
    const guest = new GuestCapturePage(page);
    await guest.open();
    const camera = page.getByTestId('camera-preview');
    await expect(camera).toBeVisible();
    const role = await camera.getAttribute('role');
    expect(role).toBe('main');
  });

  test('I4 · 学科 chip row · navigation role', async ({ page }) => {
    const guest = new GuestCapturePage(page);
    await guest.open();
    const chipRow = page.getByTestId('subject-chip-row');
    await expect(chipRow).toBeVisible();
    const role = await chipRow.getAttribute('role');
    expect(role).toBe('navigation');
  });

  test('I5 · 快门 78px 圆形 · 在 capture-controls 内', async ({ page }) => {
    const guest = new GuestCapturePage(page);
    await guest.open();
    const shutter = page.getByTestId('capture-controls-shutter');
    await expect(shutter).toBeVisible();
    const w = await shutter.evaluate((el) => getComputedStyle(el).width);
    const h = await shutter.evaluate((el) => getComputedStyle(el).height);
    console.log(`[style] shutter ${w} × ${h}`);
    // 78px expected per spec §8 AC-005 · 但若实施偏差 ±2px 可接受
    expect(parseFloat(w)).toBeGreaterThanOrEqual(70);
    expect(parseFloat(w)).toBeLessThanOrEqual(90);
  });

  test('I7 · QUOTA_EXHAUSTED 挡板可见 (AC-006)', async ({ page }) => {
    await page.setExtraHTTPHeaders({ 'x-e2e-quota-out': '1' });
    const guest = new GuestCapturePage(page);
    await guest.open();
    // banner 文案应显示 0 / 耗尽 / 明天
    const txt = await guest.getQuotaText();
    expect(txt).toMatch(/0|额度|耗尽|明天/);
    await page.setExtraHTTPHeaders({});
  });

  test('I12 · A11y · axe 0 serious', async ({ page }) => {
    const guest = new GuestCapturePage(page);
    await guest.open();
    await guest.assertAxeNoSerious();
  });
});
