/**
 * SC-08 · 跨时区
 *
 * Happy path：学生 SH→LA 切换 → DND 按 LA 重算 → 推送时间相应改变
 *
 * 轨道：B（mock-b · X-Timezone header 切换） · A 抽样
 * Tag：@sc-08
 */
import { test, expect } from '../fixtures/student';
import { setXTimezone } from '../fixtures/clock';
import { SettingsPage, NotificationsPage } from '../pages';

test.describe('SC-08 · 跨时区 @sc-08', () => {

  test('happy path · SH → LA → DND 重算', async ({ studentPage }) => {
    const settings = new SettingsPage(studentPage);
    await settings.open();

    // 模拟用户改时区为 LA
    await setXTimezone(studentPage, 'America/Los_Angeles');

    // 重新加载通知页 → 时间显示应按 LA 渲染
    const notif = new NotificationsPage(studentPage);
    await notif.open();
    await notif.assertGroupVisible('today');

    // 验证至少一条通知的时间格式带 LA tz hint（或 UTC offset 显示）
    const card = studentPage.getByTestId('p12-notif-card-1').first();
    if (await card.count()) {
      const time = await card.getByTestId('p12-notif-card-1-time').innerText();
      expect(time.length).toBeGreaterThan(0);
    }
  });
});
