/**
 * P04 · 分析结果（spec/P04-result.spec.md）
 * Mood B pure-warm · 含 memory-curve 6 节点预览
 */
import { type Page, expect } from '@playwright/test';
import { BasePage } from './_base';

export class ResultPage extends BasePage {
  readonly rootTestId = 'p04-root';

  static routePattern = /\/result\/[^/]+/;

  constructor(page: Page) { super(page); }

  async clickSave() {
    await this.byTestId('p04-save-cta').click();
    await this.page.waitForLoadState('networkidle');
  }

  /** 6 个节点 T1-T6 全部存在 */
  async assertAllMemoryCurveNodes() {
    for (const t of ['T1', 'T2', 'T3', 'T4', 'T5', 'T6']) {
      await expect(this.byTestId(`memory-curve-node-${t}`)).toBeVisible();
    }
  }

  async assertReasonCardVisible() {
    await expect(this.byTestId('p04-reason-card')).toBeVisible();
  }

  async assertSolutionStep(idx: 1 | 2 | 3) {
    await expect(this.byTestId(`p04-solution-stepper-step-${idx}`)).toBeVisible();
  }

  /** SC-07 低置信度 banner */
  async assertLowConfBannerVisible() {
    await expect(this.byTestId('p04-low-conf-banner')).toBeVisible();
  }
}
