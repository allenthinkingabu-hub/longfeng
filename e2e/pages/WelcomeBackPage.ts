/**
 * P-WELCOMEBACK · 设备指纹回归（spec/P-WELCOMEBACK.spec.md）
 * SC-14 · P1 · 设备指纹 → 多账号歧义 → 降级 P00
 *
 * 注意：本 spec 在 P1 标记为 "skeleton"。本 POM 仅覆盖 happy path + 降级路径。
 */
import { type Page, expect } from '@playwright/test';
import { BasePage } from './_base';

export class WelcomeBackPage extends BasePage {
  readonly rootTestId = 'p-welcomeback-root';

  static route = '/welcome-back';

  constructor(page: Page) { super(page); }

  async open() { await this.goto(WelcomeBackPage.route); }

  async assertGreetingVisible() {
    const greet = this.page.getByText(/欢迎回来|继续|上次/);
    await expect(greet.first()).toBeVisible();
  }

  /** 多账号歧义 → 应该展示"选择账号"列表 */
  async assertMultiAccountAmbiguity() {
    const list = this.page.getByRole('listbox');
    await expect(list).toBeVisible();
  }

  /** 降级到 P00：无指纹 / 指纹漂移 → 跳 /auth */
  async assertFallbackToAuth() {
    await this.page.waitForURL(/\/auth/, { timeout: 5_000 });
  }
}
