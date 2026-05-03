/**
 * P07 · 今日复习（spec/P07-review-today.spec.md）
 */
import { type Page, expect } from '@playwright/test';
import { BasePage } from './_base';

export class ReviewTodayPage extends BasePage {
  readonly rootTestId = 'p07-root';

  static route = '/review';

  constructor(page: Page) { super(page); }

  async open() { await this.goto(ReviewTodayPage.route); }

  async clickStartAll() {
    await this.byTestId('p07-bottom-cta-start-all-btn').click();
    await this.page.waitForLoadState('networkidle');
  }

  async getProgressPct(): Promise<number> {
    const txt = await this.byTestId('p07-hero-progress-pct').innerText();
    return parseInt(txt.replace(/\D/g, ''), 10);
  }

  async assertHeroVisible() {
    await expect(this.byTestId('today-review-card')).toBeVisible();
  }

  async assertEmptyState() {
    await expect(this.byTestId('p07-empty-state')).toBeVisible();
  }
}
