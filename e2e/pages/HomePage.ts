/**
 * P-HOME · 今日聚合首页（spec/P-HOME.spec.md）
 */
import { type Page, expect } from '@playwright/test';
import { BasePage } from './_base';

export class HomePage extends BasePage {
  readonly rootTestId = 'p-home-root';

  static route = '/home';

  constructor(page: Page) { super(page); }

  async open() { await this.goto(HomePage.route); }

  async getStreakDays(): Promise<number> {
    const txt = await this.byTestId('streak-bar-days-number').innerText();
    return parseInt(txt.trim(), 10);
  }

  async getReviewTotal(): Promise<number> {
    const txt = await this.byTestId('today-review-card-total').innerText();
    return parseInt(txt.replace(/\D/g, ''), 10);
  }

  async clickStartAll() {
    await this.byTestId('today-review-card-start-all-btn').click();
    await this.page.waitForLoadState('networkidle');
  }

  /** SC-05 视图融合：weekly sparkline 必须可见 */
  async assertWeeklySparklineVisible() {
    await expect(this.byTestId('p-home-weekly-sparkline')).toBeVisible();
  }
}
