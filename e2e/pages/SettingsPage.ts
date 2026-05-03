/**
 * P13 · 设置（spec/P13-settings.spec.md）
 * 含 SC-16 VIP AI 模型选择子区（NORMAL 锁 / VIP 选 / VIP_PLUS 实验池）
 */
import { type Page, expect } from '@playwright/test';
import { BasePage } from './_base';
import type { UserTier } from '../fixtures/student';

export class SettingsPage extends BasePage {
  readonly rootTestId = 'p13-root';

  static route = '/me/settings';

  constructor(page: Page) { super(page); }

  async open() { await this.goto(SettingsPage.route); }

  async clickLogout() {
    await this.byTestId('p13-settings-account-logout-row').click();
  }

  async toggleQuietHours() {
    await this.byTestId('p13-settings-review-quiet-hours-row').click();
  }

  async assertSettingsAbout() {
    await expect(this.byTestId('p13-settings-about-version')).toBeVisible();
  }

  /** SC-16 子区入口 */
  ai = new SettingsAiModelSection(this.page);
}

/**
 * SC-16 · VIP AI 模型选择子区（独立 POM 段 · 复用 P13 page）
 */
export class SettingsAiModelSection {
  constructor(private page: Page) {}

  async assertSectionVisible() {
    await expect(this.page.getByTestId('p13-sc16-ai-section')).toBeVisible();
  }

  async getTierAttribute(): Promise<string | null> {
    return await this.page.getByTestId('p13-sc16-ai-section').getAttribute('data-sc16-tier');
  }

  /** NORMAL 必须显示 upgrade-hint · 不显示 selector */
  async assertNormalLocked() {
    await expect(this.page.getByTestId('p13-sc16-upgrade-hint')).toBeVisible();
    await expect(this.page.getByTestId('p13-sc16-model-selector')).toHaveCount(0);
  }

  /** VIP / VIP_PLUS 显示 selector */
  async assertSelectorVisible() {
    await expect(this.page.getByTestId('p13-sc16-model-selector')).toBeVisible();
  }

  async selectModel(modelId: string) {
    await this.page.getByTestId(`p13-sc16-model-item-${modelId}`).click();
    await this.page.waitForLoadState('networkidle');
  }

  async assertModelChecked(modelId: string) {
    const item = this.page.getByTestId(`p13-sc16-model-item-${modelId}`);
    await expect(item).toHaveAttribute('aria-checked', 'true');
  }

  /** VIP_PLUS 实验池：每项必须有 cost + latency 子节点 */
  async assertCostLatencyVisible(modelId: string) {
    await expect(this.page.getByTestId(`p13-sc16-model-${modelId}-cost`)).toBeVisible();
    await expect(this.page.getByTestId(`p13-sc16-model-${modelId}-latency`)).toBeVisible();
  }

  async getModelItemCount(): Promise<number> {
    // role=radio 计数
    return await this.page.getByTestId('p13-sc16-model-selector').getByRole('radio').count();
  }
}
