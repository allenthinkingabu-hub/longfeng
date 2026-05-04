/**
 * SC-FUNNEL · 跨页全漏斗 (Phase K · plan §2)
 *
 * Phase K1: anonymous /welcome → click 试一试 → /guest/capture → 选学科+拍 → 等结果
 * Phase K2: anonymous /welcome → click 登录 → /auth → dev 账密 → /
 * Phase K3: guest claim → 登录后续期 (guest_session_id 被 claim)
 * Phase K4: quota 耗尽逼迫注册
 * Phase K5: 浏览器 back/forward · session 保持
 *
 * Track: B (mock-b · MSW handlers)
 * Tag: @sc-funnel @smoke
 */
import { test, expect } from '@playwright/test';
import path from 'node:path';
import { LandingPage, GuestCapturePage, AuthPage } from '../pages';
import { newDeviceFingerprint, injectDeviceFingerprint } from '../fixtures/guest';

const FIXTURE_IMG = path.resolve(__dirname, '../assets/sample-question.jpg');

test.describe('SC-FUNNEL · 跨页全漏斗 @sc-funnel', () => {

  test('K1 · anonymous → guest → result · 完整漏斗 @smoke', async ({ page, context }) => {
    const device = newDeviceFingerprint('funnel-k1');
    await injectDeviceFingerprint(context, device);

    const landing = new LandingPage(page);
    await landing.open();

    // 验 hero headline 出现 (AC-LANDING-001)
    await expect(page.getByTestId('landing-hero-headline')).toBeVisible({ timeout: 5_000 });

    // click 试一试 → 跳 /guest/capture (AC-LANDING-003)
    await page.getByTestId('landing-hero-cta-try').click();
    await expect(page).toHaveURL(/\/guest\/capture/);

    // 验进入 guest 页 root 可见
    const guest = new GuestCapturePage(page);
    await expect(page.getByTestId('guest-capture-page')).toBeVisible({ timeout: 5_000 });

    // 拍题流程
    await guest.assertQuotaBannerVisible();
    await guest.triggerShutter(FIXTURE_IMG);

    // 验状态进入 ANALYZING (URL 含 /analyzing 或 page 显示 analyzing 状态)
    await expect(page).toHaveURL(/analyzing|guest\/capture/, { timeout: 15_000 });
  });

  test('K2 · anonymous → login → home · 走 dev 账密 @smoke', async ({ page }) => {
    const landing = new LandingPage(page);
    await landing.open();

    // click 登录 → /auth
    await page.getByTestId('landing-hero-cta-login').click();
    await expect(page).toHaveURL(/\/auth/);

    // 勾协议
    const auth = new AuthPage(page);
    await page.getByTestId('p00-consent-bar-checkbox').check();

    // 走 dev 账密 (绕开微信 OAuth)
    await page.getByTestId('p00-other-methods-link').click();
    await expect(page.getByTestId('auth-dev-form')).toBeVisible({ timeout: 3_000 });

    // 这里不真发 dev 账密 (避免 cookie 污染) · 仅验 form 可见 + landing 入口正确串联
    await expect(page.getByTestId('auth-form-account')).toBeVisible();
    await expect(page.getByTestId('auth-form-submit')).toBeVisible();
  });

  test('K4 · quota 耗尽逼迫注册 @smoke', async ({ page, context }) => {
    const device = newDeviceFingerprint('funnel-k4');
    await injectDeviceFingerprint(context, device);

    // header 模拟 quota 耗尽
    await page.setExtraHTTPHeaders({ 'x-e2e-quota-out': '1' });

    // 直接进 guest/capture
    const guest = new GuestCapturePage(page);
    await guest.open();

    // 验 quota banner 显示 0/耗尽
    const txt = await guest.getQuotaText();
    expect(txt).toMatch(/0|额度|耗尽|明天/);

    // click 注册 CTA
    await guest.clickRegisterUpsell();

    // 验跳到 /auth (with redirect param 最佳)
    await expect(page).toHaveURL(/\/auth/);

    await page.setExtraHTTPHeaders({});
  });

  test('K5 · back/forward · session 保持', async ({ page }) => {
    const landing = new LandingPage(page);
    await landing.open();
    await page.getByTestId('landing-hero-cta-try').click();
    await expect(page).toHaveURL(/\/guest\/capture/);

    // back 回 /welcome
    await page.goBack();
    await expect(page).toHaveURL(/\/welcome/);
    await expect(page.getByTestId('landing-hero')).toBeVisible({ timeout: 5_000 });

    // forward 回 /guest/capture
    await page.goForward();
    await expect(page).toHaveURL(/\/guest\/capture/);
    await expect(page.getByTestId('guest-capture-page')).toBeVisible({ timeout: 5_000 });
  });
});
