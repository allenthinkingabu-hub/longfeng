/**
 * SC-14 · Welcomeback（P1 skeleton）
 *
 * Happy path：设备指纹存在 → 显示 greeting 卡片
 * 异常：多账号歧义 → 显示账号选择列表
 * 异常 2：指纹缺失 → 降级到 P00
 *
 * 轨道：B（mock-b · P1 skeleton 不强制 A 轨）
 * Tag：@sc-14
 */
import { test, expect } from '@playwright/test';
import { newDeviceFingerprint, injectDeviceFingerprint } from '../fixtures/guest';
import { WelcomeBackPage } from '../pages';

test.describe('SC-14 · Welcomeback (P1) @sc-14', () => {

  test('happy path · 指纹匹配 → greeting', async ({ page, context }) => {
    const device = newDeviceFingerprint('sc-14-hit');
    await injectDeviceFingerprint(context, device);

    const wb = new WelcomeBackPage(page);
    await wb.open();
    await wb.assertGreetingVisible();
  });

  test('异常 · 指纹缺失 → 降级 P00', async ({ page, context }) => {
    // 不注入指纹
    await context.addInitScript(() => {
      try { window.localStorage.removeItem('__lf_device_fp__'); } catch {}
      try { window.sessionStorage.removeItem('__lf_device_fp__'); } catch {}
    });

    await page.goto('/welcomeback');
    const wb = new WelcomeBackPage(page);
    await wb.assertFallbackToAuth();
  });
});
