/**
 * P-SHARED · 分享只读预览（spec/P-SHARED.spec.md）
 * SC-13 · 篡改 token → 403 · EXAM_DAY 脱敏 · 匿名写 → 403
 */
import { type Page, expect } from '@playwright/test';
import { BasePage } from './_base';

export class SharedPage extends BasePage {
  readonly rootTestId = 'masked-question';

  /** 不固定 route：构造时给 token */
  static routeOf(token: string) { return `/s/${token}`; }

  constructor(page: Page) { super(page); }

  async openWithToken(token: string) {
    await this.goto(SharedPage.routeOf(token));
  }

  async assertSharerBanner() {
    await expect(this.byTestId('sharer-banner')).toBeVisible();
  }

  async assertMaskedQuestionVisible() {
    await expect(this.byTestId('masked-question')).toBeVisible();
  }

  async assertMemoryCurvePreviewGray() {
    await expect(this.byTestId('memory-curve-preview')).toBeVisible();
  }

  async clickUpgradeCta() {
    await this.byTestId('upgrade-cta-fixed').click();
    await this.page.waitForLoadState('networkidle');
  }

  /** SC-13 篡改 token → 应跳 403 / error 页 */
  async assertTamperedTokenRejected() {
    const errorEl = this.page.getByText(/无效|已过期|403|无权访问/i);
    await expect(errorEl.first()).toBeVisible({ timeout: 10_000 });
  }

  /** 题干必须脱敏（前 12 字清晰 + 后续模糊） */
  async assertStemMasked() {
    const stem = await this.byTestId('masked-question').innerText();
    expect(stem).toMatch(/\*{2,}|……|注册查看/);
  }
}
