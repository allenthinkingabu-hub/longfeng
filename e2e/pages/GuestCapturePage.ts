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
    return await this.byTestId('guest-quota-banner').innerText();
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
}
