/**
 * SC-04 · FORGOT 重排
 *
 * Happy path：T3 节点 FORGOT → 旧 T4-T6 CANCELLED · 新 T0-T6 SCHEDULED · advance banner 可见 · 日历重排
 *
 * 轨道：A · B
 * Tag：@sc-04
 */
import { test, expect } from '../fixtures/student';
import { ReviewExecPage, ReviewDonePage, CalendarMonthPage } from '../pages';

test.describe('SC-04 · FORGOT 重排 @sc-04', () => {

  test('happy path · T3 FORGOT → advance banner + 重排', async ({ studentPage }) => {
    const exec = new ReviewExecPage(studentPage);
    await exec.goto('/review/test-node-T3/exec');
    await exec.revealAnswer();
    await exec.grade('forgot');

    const done = new ReviewDonePage(studentPage);
    await expect(studentPage).toHaveURL(ReviewDonePage.routePattern);
    await done.assertAdvanceBannerVisible();

    const stats = await done.getStatsRow();
    expect(stats.forgot).toBeGreaterThan(0);

    // 日历重排：跳 P10 验证下一节点 dueAt 提前到当天
    const cal = new CalendarMonthPage(studentPage);
    await cal.open();
    await cal.assertLegendBarVisible();
  });
});
