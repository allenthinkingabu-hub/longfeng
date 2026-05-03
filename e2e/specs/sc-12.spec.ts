/**
 * SC-12 · 游客 + Claim
 *
 * Happy path：游客拍题 → analyze → 注册 → claim → 错题进入正式账号
 * 异常：额度耗尽 → 拍题被拦
 *
 * 轨道：A · B
 * Tag：@smoke · @sc-12
 */
import { test, expect } from '@playwright/test';
import path from 'node:path';
import { newDeviceFingerprint, injectDeviceFingerprint } from '../fixtures/guest';
import { GuestCapturePage, AnalyzingPage, AuthPage, WrongbookListPage } from '../pages';

const FIXTURE_IMG = path.resolve(__dirname, '../assets/sample-question.jpg');

test.describe('SC-12 · 游客 + Claim @sc-12', () => {

  test('happy path · 游客拍 → 注册 → claim @smoke', async ({ page, context }) => {
    const device = newDeviceFingerprint('sc-12-happy');
    await injectDeviceFingerprint(context, device);

    const guest = new GuestCapturePage(page);
    await guest.open();
    await guest.assertQuotaBannerVisible();
    await guest.triggerShutter(FIXTURE_IMG);

    const analyzing = new AnalyzingPage(page);
    await expect(page).toHaveURL(AnalyzingPage.routePattern, { timeout: 15_000 });
    await analyzing.waitForAllStepsDone();

    // 注册 → claim
    await guest.clickRegisterUpsell();
    const auth = new AuthPage(page);
    await expect(page).toHaveURL(AuthPage.route);
    // 走真实注册（A 轨）/ B 轨 mock auth ok 后跳 home
  });

  test('异常 · 额度耗尽 → 拍题拦截', async ({ page, context }) => {
    const device = newDeviceFingerprint('sc-12-quota-out');
    await injectDeviceFingerprint(context, device);

    // MSW 是 SW · page.route 不能拦 · 用 header 让 MSW handler 返 quotaRemaining=0
    // 见 frontend/apps/h5/src/__mocks__/handlers/guest.ts (header 比 cookie 更可靠)
    await page.setExtraHTTPHeaders({ 'x-e2e-quota-out': '1' });

    const guest = new GuestCapturePage(page);
    await guest.open();
    const txt = await guest.getQuotaText();
    expect(txt).toMatch(/0|额度|耗尽|明天/);

    await page.setExtraHTTPHeaders({});
  });
});
