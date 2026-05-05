/**
 * P-GUEST-CAPTURE · 游客拍题（spec/P-GUEST-CAPTURE.spec.md）
 * SC-12 · 1/天 设备额度 · 不写 wb_*（C3 红线）
 */
import { type Page, expect } from '@playwright/test';
import { BasePage } from './_base';

export class GuestCapturePage extends BasePage {
  readonly rootTestId = 'camera-preview';

  static route = '/guest/capture';

  constructor(page: Page) { super(page); }

  async open() { await this.goto(GuestCapturePage.route); }

  async assertQuotaBannerVisible() {
    await expect(this.byTestId('guest-quota-banner')).toBeVisible();
  }

  async getQuotaText(): Promise<string> {
    // SC-12 异常：quota fetch 异步完成后 banner 文案才更新 · 等"额度|耗尽|明天|次"任一出现
    const banner = this.byTestId('guest-quota-banner');
    await expect(banner).toBeVisible();
    await expect(banner).toContainText(/额度|耗尽|明天|次/, { timeout: 5_000 });
    return await banner.innerText();
  }

  async triggerShutter(filePath: string) {
    const fileChooserPromise = this.page.waitForEvent('filechooser');
    // 复用 P02 shutter 形态（spec §3 B5）
    await this.page.getByTestId('capture-controls').getByRole('button').nth(1).click();
    const chooser = await fileChooserPromise;
    await chooser.setFiles(filePath);
  }

  async clickRegisterUpsell() {
    const banner = this.byTestId('guest-quota-banner');
    await banner.getByRole('link', { name: /注册|不限次/ }).click();
    await this.page.waitForLoadState('networkidle');
  }

  /** Phase 2 / BUG-LF-20 · ERROR overlay 出错恢复路径 */
  async expectErrorOverlay(expectedCode: 'PRESIGN' | 'UPLOAD' | 'ANALYZE' | 'NETWORK' | 'CAMERA_PERMISSION') {
    const overlay = this.byTestId('error-overlay');
    await expect(overlay).toBeVisible({ timeout: 10_000 });
    await expect(overlay).toHaveAttribute('data-error-code', expectedCode);
  }

  async expectErrorTitle(expectedText: RegExp | string) {
    await expect(this.byTestId('error-overlay-title')).toContainText(expectedText);
  }

  async clickErrorFallback(target: 'album' | 'file' | 'retry') {
    await this.byTestId(`error-overlay-fallback-${target === 'retry' ? 'retry' : target}`).click();
    // 注: retry 没 -fallback 后缀 · 下行 fallback 修正
  }

  async clickErrorAlbumFallback() {
    const fileChooserPromise = this.page.waitForEvent('filechooser', { timeout: 5_000 });
    await this.byTestId('error-overlay-fallback-album').click();
    return await fileChooserPromise; // 返回 chooser 给调用方 · 验"选相册代替"真触发 file picker
  }

  async clickErrorFileFallback() {
    const fileChooserPromise = this.page.waitForEvent('filechooser', { timeout: 5_000 });
    await this.byTestId('error-overlay-fallback-file').click();
    return await fileChooserPromise;
  }

  async clickErrorRetry() {
    await this.byTestId('error-overlay-retry').click();
    // 重试 · overlay 应消失 (state IDLE)
    await expect(this.byTestId('error-overlay')).toBeHidden({ timeout: 2_000 });
  }
}
