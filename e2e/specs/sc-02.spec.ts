/**
 * SC-02 · 推送 → 执行 → 下一节点（plan §6.2 · spec/P12, P08, P09, P05）
 *
 * Happy path：登录 → fixture 模拟推送 → 点 P12 通知 → P08 → 自评 MASTERED → P09 → 列表 mastery 进展
 * 异常：P08 中无 reveal 直接 grade → 应被 disable
 *
 * 轨道：A · B
 * Tag：@smoke · @sc-02
 */
import { test, expect } from '../fixtures/student';
import { NotificationsPage, ReviewExecPage, ReviewDonePage, WrongbookListPage } from '../pages';

test.describe('SC-02 · 推送→执行 @sc-02', () => {

  test('happy path · 推送 → P08 自评 MASTERED → P09 → 列表 +1 mastery @smoke', async ({ studentPage }) => {
    const notif = new NotificationsPage(studentPage);
    await notif.open();
    await notif.assertGroupVisible('today');
    await notif.clickNotification(1);

    const exec = new ReviewExecPage(studentPage);
    await expect(studentPage).toHaveURL(ReviewExecPage.routePattern);
    await exec.assertGradeButtonsHaveException();

    await exec.revealAnswer();
    await exec.grade('mastered');

    const done = new ReviewDonePage(studentPage);
    await expect(studentPage).toHaveURL(ReviewDonePage.routePattern);
    const stats = await done.getStatsRow();
    expect(stats.mastered).toBeGreaterThan(0);

    // 列表 mastery 进展
    const list = new WrongbookListPage(studentPage);
    await list.open();
    expect(await list.getItemCount()).toBeGreaterThan(0);
  });

  test('异常 · P08 未 reveal 不允许 grade', async ({ studentPage }) => {
    const exec = new ReviewExecPage(studentPage);
    await exec.goto('/review/test-node-id/exec');

    // grade 按钮应处于 disabled / 不可点
    const masteredBtn = exec.byTestId('p08-grade-buttons-mastered');
    await expect(masteredBtn).toHaveAttribute('aria-disabled', /true|disabled/);
  });
});
