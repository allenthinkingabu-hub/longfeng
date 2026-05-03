/**
 * SC-06 · 通用事件
 *
 * Happy path：非 STUDY relation_type 事件在 P11 显示通用版（无 memory-curve · 显示 family/exam 信息）
 *
 * 轨道：A · B
 * Tag：@sc-06
 */
import { test, expect } from '../fixtures/student';
import { CalendarMonthPage, EventDetailPage } from '../pages';

test.describe('SC-06 · 通用事件 @sc-06', () => {

  test('happy path · 通用版（family / exam）', async ({ studentPage }) => {
    const cal = new CalendarMonthPage(studentPage);
    await cal.open();

    // B 轨可控：路由到 fixture 中预置的通用事件 id
    await studentPage.goto('/event/test-event-general');

    const ev = new EventDetailPage(studentPage);
    await ev.assertGeneralForm();
    // 不应显示 study 专属
    await expect(studentPage.getByTestId('p11-related-study-memory-curve')).toHaveCount(0);
  });
});
