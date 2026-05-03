/**
 * P03 · AI 分析中（spec/P03-analyzing.spec.md）
 * SSE 4-step pipeline · plan §6.5: 必须真建 SSE 连接 · 禁短路
 */
import { type Page, expect } from '@playwright/test';
import { BasePage } from './_base';

export class AnalyzingPage extends BasePage {
  readonly rootTestId = 'p03-root';

  /** 拍题后 router push 跳转，无固定 URL · 通过 root testid 等 */
  static routePattern = /\/(analyzing|analyze)\/[^/]+/;

  constructor(page: Page) { super(page); }

  /** 等到 SSE 第 N 步 STEP_DONE */
  async waitForStep(stepIdx: 1 | 2 | 3 | 4, timeoutMs = 15_000) {
    const step = this.byTestId(`analyzing-pipeline-step-${stepIdx}`);
    await expect(step).toHaveAttribute('data-state', /done|complete/, { timeout: timeoutMs });
  }

  /** 等所有 4 步完成 */
  async waitForAllStepsDone(timeoutMs = 20_000) {
    await this.waitForStep(1, timeoutMs);
    await this.waitForStep(2, timeoutMs);
    await this.waitForStep(3, timeoutMs);
    await this.waitForStep(4, timeoutMs);
  }

  async clickCancel() {
    await this.byTestId('analyzing-pipeline-cancel-btn').click();
  }

  /** SC-07 AI 降级：必须显示 fallback banner */
  async assertFallbackBannerVisible() {
    await expect(this.byTestId('p03-fallback-banner')).toBeVisible({ timeout: 10_000 });
  }

  /** 模型 badge 显示当前 provider 名（含 SC-16 当 VIP 选 OpenAI 时应显示 openai-gpt-4o） */
  async getModelBadgeText(): Promise<string> {
    return await this.byTestId('analyzing-pipeline-model-badge').innerText();
  }

  /** Mood A or B 默认 · 不强约束 */
  async assertJsonStreamVisible() {
    await expect(this.byTestId('analyzing-pipeline-json-stream')).toBeVisible();
  }
}
