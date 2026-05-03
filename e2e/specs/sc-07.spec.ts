/**
 * SC-07 · AI 降级（plan §6.2 · spec/P03-analyzing, P04-result）
 *
 * Happy path：主 provider 挂 → 备用 provider → 手填降级；金标 PASS
 * 异常：低置信度 → P04 显示 low-conf banner
 *
 * 轨道：B（mock-b · 强制 fallback） · A 抽样
 * Tag：@smoke · @sc-07
 */
import { test, expect } from '../fixtures/student';
import { AnalyzingPage, ResultPage } from '../pages';

test.describe('SC-07 · AI 降级 @sc-07', () => {

  test('happy path · 主供应商挂 → fallback banner + 备用 provider 出结果 @smoke', async ({ studentPage }) => {
    // B 轨：拦截主 provider analyze 返回 503，备用必须接管
    await studentPage.route('**/api/ai/analyze**', async (route, req) => {
      const body = req.postDataJSON?.() ?? {};
      if (!body.providerHint || body.providerHint === 'qwen-vl-max') {
        return route.fulfill({ status: 503, body: '{"error":"provider_unavailable"}' });
      }
      return route.continue();
    });

    await studentPage.goto('/analyzing/mock-task-id-fallback');

    const analyzing = new AnalyzingPage(studentPage);
    await analyzing.assertFallbackBannerVisible();
    await analyzing.waitForAllStepsDone();
  });

  test('异常 · 低置信度 → P04 low-conf banner', async ({ studentPage }) => {
    await studentPage.goto('/result/mock-low-conf-qid');
    const result = new ResultPage(studentPage);
    await result.assertLowConfBannerVisible();
  });
});
