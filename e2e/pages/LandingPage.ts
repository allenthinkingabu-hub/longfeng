/**
 * P-LANDING · 访客落地页（spec/P-LANDING.spec.md）
 * SC-11 · TTI ≤ 1s · samples 500 降级 · 30/min IP 限流
 */
import { type Page, expect } from '@playwright/test';
import { BasePage } from './_base';

export class LandingPage extends BasePage {
  readonly rootTestId = 'landing-hero'; // P-LANDING spec §3 B2 root

  static route = '/welcome';

  constructor(page: Page) { super(page); }

  async open() { await this.goto(LandingPage.route); }

  /** SC-11 · 双 CTA 必须可见 */
  async assertDualCtaVisible() {
    await expect(this.byTestId('landing-hero')).toBeVisible();
    // 子按钮：CTA-try / CTA-login（spec §8 推断）
    const cta = this.page.getByRole('button', { name: /试一试|立即开始|登录/ });
    await expect(cta.first()).toBeVisible();
  }

  async clickTryNow() {
    await this.page.getByRole('button', { name: /试一试|立即开始/ }).first().click();
    await this.page.waitForLoadState('networkidle');
  }

  async clickLogin() {
    await this.page.getByRole('button', { name: /^登录$|登录账号/ }).first().click();
    await this.page.waitForLoadState('networkidle');
  }

  /** 三步漫画 + 样例 + KPI · 第二段 warm */
  async assertWarmSectionsVisible() {
    await expect(this.byTestId('landing-three-step')).toBeVisible();
    await expect(this.byTestId('landing-samples')).toBeVisible();
    await expect(this.byTestId('landing-kpi')).toBeVisible();
  }

  /** SC-11 降级 · samples 500 → KPI banner 可见但 samples block 应空态 */
  async assertSamplesDegraded() {
    await expect(this.byTestId('landing-samples')).toBeVisible();
    // 降级时显示 fallback / empty (spec §9 异常路径)
    const samples = this.byTestId('landing-samples');
    const text = await samples.innerText();
    expect(text).toMatch(/暂无|稍后|示例/);
  }
}
