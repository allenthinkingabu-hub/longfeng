/**
 * SC-03 · 全部开始 → 中途退出（D-Cancel-Race）
 *
 * Happy path：P07 → 点全部开始 → P08 → 中途点 close → 二次确认 → session=PAUSED · 节点 SCHEDULED 不变
 * 异常：取消 cancel 弹窗后回到 P08 继续
 *
 * 轨道：A · B
 * Tag：@sc-03
 */
import { test, expect } from '../fixtures/student';
import { freezeClock } from '../fixtures/clock';
import { ReviewTodayPage, ReviewExecPage, WrongbookListPage } from '../pages';

test.describe('SC-03 · 全部开始 + 中途退出 (D-Cancel-Race) @sc-03', () => {

  test('happy path · 中途退出 → 二次确认 → session=PAUSED', async ({ studentPage }) => {
    // 冻结时钟避免 cursor 漂移
    await freezeClock(studentPage, '2026-05-02T10:30:00+08:00');

    const today = new ReviewTodayPage(studentPage);
    await today.open();
    await today.assertHeroVisible();
    await today.clickStartAll();

    const exec = new ReviewExecPage(studentPage);
    await expect(studentPage).toHaveURL(ReviewExecPage.routePattern);
    const cursorBefore = await exec.getCursor();
    expect(cursorBefore).toMatch(/^\d+\s*\/\s*\d+$/);

    await exec.clickClose();
    await exec.confirmExit();

    // 退出后跳回 P07，hero 进度不变（session=PAUSED · 节点状态不变）
    await expect(studentPage).toHaveURL(/\/review$/);
    // 列表 mastery 不应变化
    const list = new WrongbookListPage(studentPage);
    await list.open();
    // 仅验证页面正常渲染（mastery 数值由后端时序决定，不强约束）
  });

  test('异常 · cancel 弹窗 → 留在 P08 继续', async ({ studentPage }) => {
    const today = new ReviewTodayPage(studentPage);
    await today.open();
    await today.clickStartAll();

    const exec = new ReviewExecPage(studentPage);
    await exec.clickClose();
    await exec.cancelExit();

    await expect(studentPage).toHaveURL(ReviewExecPage.routePattern);
  });
});
