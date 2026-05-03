/**
 * SC-01 · 拍题 → 入库 → 首节点（plan §6.2 · spec/P02-capture, P03-analyzing, P04-result, P05-wrongbook-list）
 *
 * Happy path：登录 → P02 拍照 → presign 上传 → P03 SSE 4 step → P04 保存 → P05 列表 +1
 * 异常 1：上传失败 → 错误 banner 可见 + 不进入 P03
 * 异常 2：SSE 中途 cancel → 退回 P02
 *
 * 轨道：A（真后端） · B（mock-b 必过 · 默认 CI 跑）
 * Tag：@smoke · @sc-01
 */
import { test, expect } from '../fixtures/student';
import path from 'node:path';
import { CapturePage, AnalyzingPage, ResultPage, WrongbookListPage } from '../pages';

const FIXTURE_IMG = path.resolve(__dirname, '../assets/sample-question.jpg');

test.describe('SC-01 · 拍题入库 @sc-01', () => {

  test('happy path · 拍照 → SSE → 保存 → 列表 +1 @smoke', async ({ studentPage }) => {
    const list = new WrongbookListPage(studentPage);
    await list.open();
    const before = await list.getItemCount();

    const capture = new CapturePage(studentPage);
    await capture.goto(CapturePage.route);
    await capture.assertMoodC();
    await capture.selectSubject('math');
    await capture.assertDetectBadgeVisible();
    await capture.triggerShutter(FIXTURE_IMG);
    await capture.assertUploadProgressVisible();

    // SSE 4 step
    const analyzing = new AnalyzingPage(studentPage);
    await expect(studentPage).toHaveURL(AnalyzingPage.routePattern, { timeout: 15_000 });
    await analyzing.assertJsonStreamVisible();
    await analyzing.waitForAllStepsDone();

    // P04 保存
    const result = new ResultPage(studentPage);
    await result.assertReasonCardVisible();
    await result.assertAllMemoryCurveNodes();
    await result.clickSave();

    // 列表 +1
    await list.open();
    const after = await list.getItemCount();
    expect(after).toBe(before + 1);

    await list.assertAxeNoSerious();
  });

  test('异常 · presign 失败 → 显示错误 banner，不进入 P03', async ({ studentPage, context }) => {
    // B 轨 · MSW 是 service worker · page.route 不能直接拦 SW
    // 通过 cookie 让 MSW capture handler 切到 500 分支（见 __mocks__/handlers/capture.ts shouldFailPresign）
    const baseUrl = process.env.BASE_URL ?? 'http://localhost:5173';
    await context.addCookies([{
      name: 'lf_e2e_presign_fail',
      value: '1',
      url: baseUrl,
    }]);

    const capture = new CapturePage(studentPage);
    await capture.goto(CapturePage.route);
    await capture.selectSubject('math');
    await capture.triggerShutter(FIXTURE_IMG);

    await expect(capture.byTestId('p02-error-banner')).toBeVisible({ timeout: 10_000 });
    // 没跳到 analyzing
    await expect(studentPage).not.toHaveURL(AnalyzingPage.routePattern);

    // cleanup
    await context.clearCookies();
  });

  test('异常 · SSE 中途 cancel → 退回 P02', async ({ studentPage }) => {
    const capture = new CapturePage(studentPage);
    await capture.goto(CapturePage.route);
    await capture.selectSubject('math');
    await capture.triggerShutter(FIXTURE_IMG);

    const analyzing = new AnalyzingPage(studentPage);
    await expect(studentPage).toHaveURL(AnalyzingPage.routePattern);
    await analyzing.waitForStep(1);
    await analyzing.clickCancel();

    await expect(studentPage).toHaveURL(/\/capture/, { timeout: 5_000 });
  });
});
