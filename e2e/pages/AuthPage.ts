/**
 * P00 · 登录页（spec/P00.spec.md）
 * Mood: hero+overlap dark · 微信 CTA + 其他登录方式
 */
import { type Page, expect } from '@playwright/test';
import { BasePage } from './_base';

export class AuthPage extends BasePage {
  readonly rootTestId = 'p00-root';

  static route = '/auth';

  constructor(page: Page) { super(page); }

  async open() { await this.goto(AuthPage.route); }

  // 同意条款 → 必须先勾选才能点登录
  async acceptConsent() {
    await this.byTestId('p00-consent-bar-checkbox').check();
    await expect(this.byTestId('p00-consent-bar-checkbox')).toBeChecked();
  }

  async clickWechatLogin() {
    await this.byTestId('p00-wechat-cta-btn').click();
  }

  async openOtherMethods() {
    await this.byTestId('p00-other-methods-link').click();
    await this.page.waitForLoadState('networkidle');
  }

  /** 验登录页是否显示 mood A hero overlap（spec §8） */
  async assertMoodA() {
    const root = this.byTestId(this.rootTestId);
    await expect(root).toHaveAttribute('data-mood', /A|hero/);
  }
}
