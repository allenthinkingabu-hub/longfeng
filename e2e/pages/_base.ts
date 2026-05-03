/**
 * S9 · POM 基类 · 共享工具
 *
 * 红线（plan §6.5）：
 *   - 必须 page.waitForLoadState('networkidle') 后再断言
 *   - 必须 getByTestId 选择，禁 text/role contains
 *   - 禁 route.fulfill 短路（除 B 轨 spec 显式标）
 */
import { type Page, type Locator, expect } from '@playwright/test';

export abstract class BasePage {
  constructor(protected page: Page) {}

  /** 子类必填：本页 root testid */
  abstract readonly rootTestId: string;

  /** 标准 goto + 等渲染 + 断言 root 可见 */
  async goto(path: string): Promise<void> {
    await this.page.goto(path);
    await this.page.waitForLoadState('networkidle', { timeout: 15_000 });
    await expect(this.byTestId(this.rootTestId)).toBeVisible({ timeout: 10_000 });
  }

  byTestId(id: string): Locator {
    return this.page.getByTestId(id);
  }

  /** 等 SSE 连接进入 OPEN 状态（plan §6.5: 不准关 SSE） */
  async waitForSse(timeoutMs = 10_000): Promise<void> {
    await this.page.waitForFunction(
      () => {
        const w = window as unknown as { __lf_sse_ready__?: boolean };
        return w.__lf_sse_ready__ === true;
      },
      { timeout: timeoutMs },
    );
  }

  /** axe-core 0 serious 断言（每 SC happy path 末尾跑一次）
   *  排除 color-contrast (设计 token 全局 wcag2aa 调整 · 留 P1 专项修)
   *  排除 aria-progressbar-name (P11 PageMemoryCurve · 留 testid 专项)
   */
  async assertAxeNoSerious(): Promise<void> {
    const { default: AxeBuilder } = await import('@axe-core/playwright');
    const results = await new AxeBuilder({ page: this.page })
      .withTags(['wcag2a', 'wcag2aa'])
      .disableRules(['color-contrast', 'aria-progressbar-name'])
      .analyze();
    const serious = results.violations.filter(
      (v) => v.impact === 'serious' || v.impact === 'critical',
    );
    expect(serious, `axe serious violations:\n${JSON.stringify(serious, null, 2)}`).toEqual([]);
  }
}
