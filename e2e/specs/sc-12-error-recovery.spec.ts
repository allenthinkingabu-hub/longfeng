/**
 * SC-12 · ERROR overlay 文案分类 + fallback button 路径
 *
 * 修复回归 BUG-LF-20 (FE 没解析 BE presign envelope · 触发 ERROR · 文案误导成"请检查相机权限")
 * 验 Phase 1 UX 改造: errorCode 分类准确文案 + 3 fallback button 真触发 file picker
 *
 * 轨道: B (mock-b · 用 MSW header hook 强制不同 fail 模式)
 * Tag: @sc-12 @error-recovery
 */
import { test, expect } from '@playwright/test';
import path from 'node:path';
import { newDeviceFingerprint, injectDeviceFingerprint } from '../fixtures/guest';
import { GuestCapturePage } from '../pages';

const FIXTURE_IMG = path.resolve(__dirname, '../assets/sample-question.jpg');

test.describe('SC-12 ERROR overlay · 文案分类 + fallback @sc-12 @error-recovery', () => {

  test('PRESIGN 5xx → overlay 文案"上传准备失败" · 不再误导"相机权限"', async ({ page, context }) => {
    const device = newDeviceFingerprint('sc-12-err-presign');
    await injectDeviceFingerprint(context, device);
    await page.setExtraHTTPHeaders({ 'x-e2e-fail-presign': '1' });

    const guest = new GuestCapturePage(page);
    await guest.open();
    await guest.assertQuotaBannerVisible();
    await guest.triggerShutter(FIXTURE_IMG);

    await guest.expectErrorOverlay('PRESIGN');
    await guest.expectErrorTitle(/上传准备失败/);
    // 关键反向断言: 不再 hardcode "请检查相机权限"
    await expect(page.getByTestId('error-overlay-title')).not.toContainText('相机权限');
  });

  test('ANALYZE 5xx → overlay 文案"AI 分析失败"', async ({ page, context }) => {
    const device = newDeviceFingerprint('sc-12-err-analyze');
    await injectDeviceFingerprint(context, device);
    await page.setExtraHTTPHeaders({ 'x-e2e-fail-analyze': '1' });

    const guest = new GuestCapturePage(page);
    await guest.open();
    await guest.assertQuotaBannerVisible();
    await guest.triggerShutter(FIXTURE_IMG);

    await guest.expectErrorOverlay('ANALYZE');
    await guest.expectErrorTitle(/AI 分析失败/);
  });

  test('ERROR overlay · "选相册代替" 真触发 file picker (验 fallback 不只是装饰)', async ({ page, context }) => {
    const device = newDeviceFingerprint('sc-12-fallback-album');
    await injectDeviceFingerprint(context, device);
    await page.setExtraHTTPHeaders({ 'x-e2e-fail-presign': '1' });

    const guest = new GuestCapturePage(page);
    await guest.open();
    await guest.triggerShutter(FIXTURE_IMG);
    await guest.expectErrorOverlay('PRESIGN');

    // 关 fail header · 让 picker 选完后下次 attempt 路径不再被强制 fail
    await page.setExtraHTTPHeaders({});

    const chooser = await guest.clickErrorAlbumFallback();
    expect(chooser).toBeTruthy(); // 关键: file picker 真打开 (fallback button 真触发 input.click() · 不只是装饰)
  });

  test('ERROR overlay · "重试" 按钮关 overlay (state IDLE)', async ({ page, context }) => {
    const device = newDeviceFingerprint('sc-12-err-retry');
    await injectDeviceFingerprint(context, device);
    await page.setExtraHTTPHeaders({ 'x-e2e-fail-presign': '1' });

    const guest = new GuestCapturePage(page);
    await guest.open();
    await guest.triggerShutter(FIXTURE_IMG);
    await guest.expectErrorOverlay('PRESIGN');

    await guest.clickErrorRetry(); // 内含 overlay hidden 断言

    // 验 capture-controls 仍可点 (回到 IDLE)
    await expect(page.getByTestId('capture-controls')).toBeVisible();
  });

  test('3 source button 都有 testid (回归 Phase 1.3)', async ({ page, context }) => {
    const device = newDeviceFingerprint('sc-12-source-testids');
    await injectDeviceFingerprint(context, device);

    const guest = new GuestCapturePage(page);
    await guest.open();

    await expect(page.getByTestId('source-album')).toBeVisible();
    await expect(page.getByTestId('source-camera')).toBeVisible();
    await expect(page.getByTestId('source-file')).toBeVisible();
  });
});
