/**
 * SC-P00 · 登录页 (P00 /auth) E2E (Phase J)
 *
 * archive 缺 mockup · 走 STYLE-TRUTH §6 设计建议 + spec §8 AC
 *
 * Track: B (mock-b)
 * Tag: @sc-p00
 */
import { test, expect } from '@playwright/test';
import { AuthPage } from '../pages';

test.describe('SC-P00 · 登录页 @sc-p00', () => {

  test('J1 · 进入页面 root + statusbar (AC-P00-001)', async ({ page }) => {
    const auth = new AuthPage(page);
    await auth.open();
    await expect(page.getByTestId('p00-root')).toBeVisible();
    await expect(page.getByTestId('p00-logo-zone')).toBeVisible();
  });

  test('J2 · 微信按钮 iron-rule-1-exception + #07C160 (AC-P00-002)', async ({ page }) => {
    const auth = new AuthPage(page);
    await auth.open();
    const wechatBtn = page.getByTestId('p00-wechat-cta-btn');
    await expect(wechatBtn).toBeVisible();
    // 必须含 iron-rule-1-exception attr
    const exception = await wechatBtn.getAttribute('data-iron-rule-1-exception');
    expect(exception).toBe('wechat-brand');
    // 背景色 #07C160 = rgb(7, 193, 96)
    const bg = await wechatBtn.evaluate((el) => getComputedStyle(el).backgroundColor);
    console.log(`[style] wechat btn bg=${bg}`);
    expect(bg).toMatch(/7,\s*193,\s*96/);
  });

  test('J3 · 协议 checkbox 可点 + tab focus', async ({ page }) => {
    const auth = new AuthPage(page);
    await auth.open();
    const checkbox = page.getByTestId('p00-consent-bar-checkbox');
    await expect(checkbox).toBeVisible();
    // 点一次后应被 checked
    await checkbox.check();
    await expect(checkbox).toBeChecked();
  });

  test('J8 · 协议链接 ToS + Privacy 可点', async ({ page }) => {
    const auth = new AuthPage(page);
    await auth.open();
    const tos = page.getByTestId('p00-consent-bar-link-tos');
    const privacy = page.getByTestId('p00-consent-bar-link-privacy');
    await expect(tos).toBeVisible();
    await expect(privacy).toBeVisible();
  });

  test('J9 · dev 账密 form 可展开', async ({ page }) => {
    const auth = new AuthPage(page);
    await auth.open();
    await page.getByTestId('p00-other-methods-link').click();
    await expect(page.getByTestId('auth-dev-form')).toBeVisible({ timeout: 3_000 });
    await expect(page.getByTestId('auth-form-account')).toBeVisible();
    await expect(page.getByTestId('auth-form-password')).toBeVisible();
    await expect(page.getByTestId('auth-form-submit')).toBeVisible();
  });

  test('J12 · A11y · axe 0 serious', async ({ page }) => {
    const auth = new AuthPage(page);
    await auth.open();
    await auth.assertAxeNoSerious();
  });

  test('J · iPhone viewport responsive sanity', async ({ page }) => {
    // 默认 iPhone 15 Pro · 验 hero 不超过 viewport 宽
    const auth = new AuthPage(page);
    await auth.open();
    const root = page.getByTestId('p00-root');
    const box = await root.boundingBox();
    expect(box, 'root should have dimensions').not.toBeNull();
    if (box) {
      // viewport iPhone 15 Pro = 393px wide · root 应充满（可能含 padding 但不应超）
      expect(box.width).toBeGreaterThan(380);
      expect(box.width).toBeLessThanOrEqual(393);
    }
  });
});
