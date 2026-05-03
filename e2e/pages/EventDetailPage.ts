/**
 * P11 · 事件详情（spec/P11-event-detail.spec.md）
 * 三形态同壳：学习版 / 通用版 / 分享脱敏版
 */
import { type Page, expect } from '@playwright/test';
import { BasePage } from './_base';

export type EventForm = 'STUDY' | 'GENERAL' | 'SHARED_MASKED';

export class EventDetailPage extends BasePage {
  readonly rootTestId = 'p11-root';

  static routePattern = /\/event\/[^/]+/;

  constructor(page: Page) { super(page); }

  /** 学习版必含：related-study + memory-curve */
  async assertStudyForm() {
    await expect(this.byTestId('p11-related-study')).toBeVisible();
    await expect(this.byTestId('p11-related-study-memory-curve')).toBeVisible();
  }

  /** 通用版必含：related-family or related-exam */
  async assertGeneralForm() {
    const hasFamily = await this.byTestId('p11-related-family').count();
    const hasExam   = await this.byTestId('p11-related-exam').count();
    expect(hasFamily + hasExam).toBeGreaterThan(0);
  }

  /** SC-13 分享脱敏：必须没有 memory-curve（脱敏） */
  async assertSharedMasked() {
    await expect(this.byTestId('p11-related-study-memory-curve')).toHaveCount(0);
  }

  async clickReviewNow() {
    await this.byTestId('p11-bottom-cta-review-now').click();
    await this.page.waitForLoadState('networkidle');
  }

  async clickAddCalendar() {
    await this.byTestId('p11-bottom-cta-add-calendar').click();
  }
}
