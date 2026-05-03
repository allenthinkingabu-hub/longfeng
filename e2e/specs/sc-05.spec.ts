/**
 * SC-05 · 视图融合（plan §6.2 · spec/P-HOME, P10, P11, P08）
 *
 * Happy path：P-HOME 条带 → P10 月历 → P11 事件详情 → 立即复习 → P08
 *
 * 轨道：A · B
 * Tag：@smoke · @sc-05
 */
import { test, expect } from '../fixtures/student';
import { HomePage, CalendarMonthPage, EventDetailPage, ReviewExecPage } from '../pages';

test.describe('SC-05 · 视图融合 @sc-05', () => {

  test('happy path · HOME → 月历 → 事件 → 立即复习 @smoke', async ({ studentPage }) => {
    const home = new HomePage(studentPage);
    await home.open();
    await home.assertWeeklySparklineVisible();
    expect(await home.getReviewTotal()).toBeGreaterThanOrEqual(0);

    // 跳 P10
    const cal = new CalendarMonthPage(studentPage);
    await cal.open();
    await cal.assertLegendBarVisible();
    await cal.openCell(15); // 任意中部 cell

    // P11
    const ev = new EventDetailPage(studentPage);
    await expect(studentPage).toHaveURL(EventDetailPage.routePattern);
    await ev.assertStudyForm();
    await ev.clickReviewNow();

    // P08
    await expect(studentPage).toHaveURL(ReviewExecPage.routePattern);
    await home.assertAxeNoSerious();
  });
});
