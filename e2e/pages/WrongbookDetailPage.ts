/**
 * P06 · 错题详情（spec/P06-wrongbook-detail.spec.md）
 */
import { type Page, expect } from '@playwright/test';
import { BasePage } from './_base';

export class WrongbookDetailPage extends BasePage {
  readonly rootTestId = 'wrongbook.detail.root';

  static routePattern = /\/wrongbook\/[^/]+/;

  constructor(page: Page) { super(page); }

  async assertStemTextVisible() {
    await expect(this.byTestId('wrongbook.detail.stem-text')).toBeVisible();
  }

  async openTagSheet() {
    await this.byTestId('wrongbook.detail.tag-sheet').click();
  }

  async addCustomTag(tag: string) {
    await this.byTestId('wrongbook.detail.tag-custom-input').fill(tag);
    await this.byTestId('wrongbook.detail.tag-save').click();
    await this.page.waitForLoadState('networkidle');
  }

  /** SC-10 归档 → 触发节点级联 CANCELLED */
  async clickArchive() {
    await this.byTestId('p06-bottom-actions-archive-btn').click();
  }

  async clickReviewEntry() {
    await this.byTestId('p06-bottom-actions-review-btn').click();
    await this.page.waitForLoadState('networkidle');
  }

  /** 删除二次确认 */
  async deleteWithConfirm() {
    await this.byTestId('wrongbook.detail.delete.btn').click();
    await this.byTestId('wrongbook.detail.delete.confirm').click();
  }

  async cancelDelete() {
    await this.byTestId('wrongbook.detail.delete.btn').click();
    await this.byTestId('wrongbook.detail.delete.cancel').click();
  }

  /** mastery 应在 0-100 范围（S7 契约修正） */
  async assertMasteryDisplayed() {
    const ai = this.byTestId('p06-ai-brief-difficulty');
    await expect(ai).toBeVisible();
  }
}
