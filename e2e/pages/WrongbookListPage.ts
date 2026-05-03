/**
 * P05 · 错题本列表（spec/P05-wrongbook-list.spec.md）
 */
import { type Page, expect } from '@playwright/test';
import { BasePage } from './_base';

export class WrongbookListPage extends BasePage {
  readonly rootTestId = 'wrongbook.list.root';

  static route = '/wrongbook';

  constructor(page: Page) { super(page); }

  async open() { await this.goto(WrongbookListPage.route); }

  async getItemCount(): Promise<number> {
    // 实际 List page 用 question-list-card-{N} (root) + 子 testid 加后缀如 -subject-bar/-thumbnail
    // 用 regex 仅匹配根节点 (question-list-card-数字结尾 · 不含其他后缀)
    return await this.page.getByTestId(/^question-list-card-\d+$/).count();
  }

  async openItem(index = 0) {
    await this.page.getByTestId(/^question-list-card-\d+$/).nth(index).click();
    await this.page.waitForLoadState('networkidle');
  }

  async filterBySubject(subject: string) {
    await this.byTestId('wrongbook.list.filter-subject').click();
    await this.page.getByRole('option', { name: subject }).click();
    await this.page.waitForLoadState('networkidle');
  }

  async switchToArchiveTab() {
    await this.byTestId('wrongbook.list.archive-tab').click();
    await this.page.waitForLoadState('networkidle');
  }

  async loadMore() {
    await this.byTestId('wrongbook.list.load-more').click();
    await this.page.waitForLoadState('networkidle');
  }

  async clickFabCapture() {
    await this.byTestId('p05-fab-capture').click();
  }
}
