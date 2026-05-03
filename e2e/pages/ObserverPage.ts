/**
 * P-OBSERVER · 观察者只读会话（spec/P-OBSERVER.spec.md）
 * SC-15 · 三重防护：JWT scope=READ · ARIA aria-disabled · 撤销 ≤ 1s 后所有写 API 403
 */
import { type Page, expect } from '@playwright/test';
import { BasePage } from './_base';

export class ObserverPage extends BasePage {
  readonly rootTestId = 'observer-shell';

  static route = '/observer';

  constructor(page: Page) { super(page); }

  async open() { await this.goto(ObserverPage.route); }

  /** ReadOnlyBanner 可见 + 文案"仅可读" */
  async assertReadOnlyBannerVisible() {
    const banner = this.byTestId('observer-banner');
    await expect(banner).toBeVisible();
    const text = await banner.innerText();
    expect(text).toMatch(/仅可读|观察者|读/);
  }

  /** scope badge = READ */
  async assertScopeBadgeRead() {
    const badge = this.byTestId('observer-scope-badge');
    await expect(badge).toBeVisible();
    await expect(badge).toHaveText(/READ|仅读/);
  }

  /** 学生信息 · 必须脱敏 */
  async assertStudentSummary() {
    const card = this.byTestId('observer-student-summary');
    await expect(card).toBeVisible();
    const txt = await card.innerText();
    // C5 红线：观察者不返回原图/email/chat_id · UI 不应出现 @ 邮箱
    expect(txt).not.toMatch(/@[a-z0-9.]+\.[a-z]{2,}/i);
  }

  /** SC-15 三重防护-2：所有写按钮必须 aria-disabled="true" */
  async assertAllWriteButtonsDisabled() {
    const writeButtonsTestids = [
      'p06-bottom-actions-archive-btn',
      'p06-bottom-actions-review-btn',
      'p07-bottom-cta-start-all-btn',
      'p08-grade-buttons-mastered',
      'p11-bottom-cta-review-now',
    ];
    for (const tid of writeButtonsTestids) {
      const el = this.page.getByTestId(tid);
      const count = await el.count();
      if (count > 0) {
        await expect(el).toHaveAttribute('aria-disabled', 'true');
      }
    }
  }

  /** Watermark 始终可见（C5 反截图心理威慑） */
  async assertWatermarkVisible() {
    await expect(this.byTestId('observer-watermark')).toBeVisible();
  }

  /** SC-15 三重防护-3：撤销后 1s 内点写按钮 → 重定向到 revoke modal */
  async assertRevokeRedirectModal() {
    await expect(this.byTestId('observer-revoke-modal')).toBeVisible({ timeout: 1_500 });
  }
}
