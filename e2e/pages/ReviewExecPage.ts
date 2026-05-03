/**
 * P08 · 复习执行（spec/P08-review-exec.spec.md）
 * 自评 3 档：mastery-{forgot|partial|mastered} 色（铁律 1 例外）
 */
import { type Page, expect } from '@playwright/test';
import { BasePage } from './_base';

export type Grade = 'forgot' | 'partial' | 'mastered';

export class ReviewExecPage extends BasePage {
  readonly rootTestId = 'p08-root';

  static routePattern = /\/review\/exec\/[^/]+/;

  constructor(page: Page) { super(page); }

  async revealAnswer() {
    await this.byTestId('p08-reveal-btn').click();
    await expect(this.byTestId('p08-reveal-content')).toBeVisible();
  }

  async grade(g: Grade) {
    await this.byTestId(`p08-grade-buttons-${g}`).click();
  }

  /** SC-03 中途退出：触发二次确认 */
  async clickClose() {
    await this.byTestId('p08-close-btn').click();
  }

  async confirmExit() {
    const sheet = this.byTestId('p08-exit-confirm-sheet');
    await expect(sheet).toBeVisible();
    await sheet.getByRole('button', { name: /确认|退出/i }).click();
  }

  async cancelExit() {
    const sheet = this.byTestId('p08-exit-confirm-sheet');
    await expect(sheet).toBeVisible();
    await sheet.getByRole('button', { name: /取消|继续/i }).click();
  }

  /** 验当前 cursor 显示（如 "3 / 12"） */
  async getCursor(): Promise<string> {
    return await this.byTestId('p08-topbar-cursor').innerText();
  }

  /** 自评按钮 iron-rule-1 例外断言（必须有 data-iron-rule-1-exception="self-grading"） */
  async assertGradeButtonsHaveException() {
    for (const g of ['forgot', 'partial', 'mastered'] as const) {
      const btn = this.byTestId(`p08-grade-buttons-${g}`);
      await expect(btn).toHaveAttribute('data-iron-rule-1-exception', 'self-grading');
    }
  }
}
