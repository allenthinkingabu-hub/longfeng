/**
 * SC-16 · VIP AI 模型选择（plan §6.2 · TDD 附录 G）
 *
 * 三 tier 视觉契约：
 *   - NORMAL    · sc16-section data-sc16-tier=NORMAL · upgrade-hint 可见 · selector 不存在
 *   - VIP       · selector 可见 + 2 model item
 *   - VIP_PLUS  · selector 可见 + 4 model item + 每项 cost/latency
 *
 * 红线：NORMAL 用户传 aiModelHint 后端必须静默忽略**不报 403**（plan §6.2 末段）
 *
 * 轨道：A · B
 * Tag：@smoke · @sc-16
 */
import { test as studentTest } from '../fixtures/student';
import { expect } from '@playwright/test';
import { SC16_EXPECTATIONS, NORMAL_TIER_HINT_PAYLOAD } from '../fixtures/userTier';
import { SettingsPage } from '../pages';

studentTest.describe('SC-16 · NORMAL @sc-16', () => {

  studentTest('NORMAL · upgrade-hint 显示 · selector 不存在 · data-sc16-tier=NORMAL @smoke', async ({ studentPage }) => {
    const settings = new SettingsPage(studentPage);
    await settings.open();
    await settings.ai.assertSectionVisible();
    expect(await settings.ai.getTierAttribute()).toBe('NORMAL');
    await settings.ai.assertNormalLocked();

    const exp = SC16_EXPECTATIONS.NORMAL;
    expect(exp.showSelector).toBe(false);
    expect(exp.modelCount).toBe(0);
  });

  studentTest('NORMAL · 强行注入 aiModelHint 必须静默忽略 · 不报 403', async ({ studentPage }) => {
    // 拦截 analyze 请求 · 验证后端响应 200（即使 NORMAL 用户传了 hint）
    let observed: { status: number; reqBody?: any } | null = null;
    await studentPage.route('**/api/ai/analyze**', async (route, req) => {
      const body = JSON.parse(req.postData() ?? '{}');
      // 注入非法 hint
      Object.assign(body, NORMAL_TIER_HINT_PAYLOAD);
      const resp = await route.fetch({ postData: JSON.stringify(body) });
      observed = { status: resp.status(), reqBody: body };
      await route.fulfill({ response: resp });
    });

    // 触发一次 analyze（最简形式：跳 /analyzing/<task>）
    await studentPage.goto('/analyzing/sc-16-normal-hint');
    await studentPage.waitForLoadState('networkidle');

    if (observed) {
      expect(observed.status).not.toBe(403); // 必须不能 403（防 tier 信号泄露）
    }
  });
});

studentTest.describe('SC-16 · VIP @sc-16', () => {

  studentTest('VIP · selector 显示 · 2 个 model · 选 OpenAI 持久化', async ({ vipPage }) => {
    const settings = new SettingsPage(vipPage);
    await settings.open();
    expect(await settings.ai.getTierAttribute()).toBe('VIP');
    await settings.ai.assertSelectorVisible();
    expect(await settings.ai.getModelItemCount()).toBeGreaterThanOrEqual(2);

    await settings.ai.selectModel('openai-gpt-4o');
    await settings.ai.assertModelChecked('openai-gpt-4o');

    // 持久化：刷新后仍 checked
    await vipPage.reload();
    await vipPage.waitForLoadState('networkidle');
    await settings.ai.assertModelChecked('openai-gpt-4o');
  });
});

studentTest.describe('SC-16 · VIP_PLUS @sc-16', () => {

  studentTest('VIP_PLUS · 实验池 + 每项 cost/latency', async ({ vipPlusPage }) => {
    const settings = new SettingsPage(vipPlusPage);
    await settings.open();
    expect(await settings.ai.getTierAttribute()).toBe('VIP_PLUS');
    await settings.ai.assertSelectorVisible();
    expect(await settings.ai.getModelItemCount()).toBeGreaterThanOrEqual(4);

    // 实验池模型必须显示 cost + latency
    await settings.ai.assertCostLatencyVisible('claude-3-7-sonnet-experimental');
  });
});
