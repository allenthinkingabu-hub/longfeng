/**
 * P09 · 复习完成（spec/P09-review-done.spec.md）
 * Mood D celebrate-green · ConfettiBurst 仅"今日全部完成"触发（铁律 3）
 */
import { type Page, expect } from '@playwright/test';
import { BasePage } from './_base';

export class ReviewDonePage extends BasePage {
  readonly rootTestId = 'p09-root';

  static routePattern = /\/review\/done/;

  constructor(page: Page) { super(page); }

  async assertMoodD() {
    await expect(this.byTestId(this.rootTestId)).toHaveAttribute('data-mood', /D|celebrate/);
  }

  /** ConfettiBurst 触发条件：今日全部完成。否则不可见。 */
  async assertConfettiVisible(shouldShow: boolean) {
    if (shouldShow) {
      await expect(this.byTestId('confetti-burst')).toBeVisible();
    } else {
      await expect(this.byTestId('confetti-burst')).toHaveCount(0);
    }
  }

  async getStatsRow(): Promise<{ mastered: number; partial: number; forgot: number }> {
    const m = parseInt((await this.byTestId('p09-stats-row-mastered').innerText()).replace(/\D/g, ''), 10);
    const p = parseInt((await this.byTestId('p09-stats-row-partial').innerText()).replace(/\D/g, ''), 10);
    const f = parseInt((await this.byTestId('p09-stats-row-forgot').innerText()).replace(/\D/g, ''), 10);
    return { mastered: m, partial: p, forgot: f };
  }

  async clickContinue() {
    await this.byTestId('p09-cta-row-continue-btn').click();
  }

  async clickEnd() {
    await this.byTestId('p09-cta-row-end-btn').click();
    await this.page.waitForLoadState('networkidle');
  }

  async clickAddCalendar() {
    await this.byTestId('p09-next-due-card-add-calendar-btn').click();
  }

  /** SC-04 FORGOT 重排：advance-banner 应可见 */
  async assertAdvanceBannerVisible() {
    await expect(this.byTestId('p09-advance-banner')).toBeVisible();
  }
}
