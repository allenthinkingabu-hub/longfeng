/**
 * P12 · 通知中心（spec/P12-notifications.spec.md）
 */
import { type Page, expect } from '@playwright/test';
import { BasePage } from './_base';

export class NotificationsPage extends BasePage {
  readonly rootTestId = 'p12-root';

  static route = '/notifications';

  constructor(page: Page) { super(page); }

  async open() { await this.goto(NotificationsPage.route); }

  async clickMarkAllRead() {
    await this.byTestId('p12-header-mark-all-read').click();
  }

  async assertGroupVisible(group: 'today' | 'yesterday' | 'thisweek' | 'earlier') {
    await expect(this.byTestId(`p12-group-${group}`)).toBeVisible();
  }

  async assertEmptyState() {
    await expect(this.byTestId('p12-empty-state')).toBeVisible();
  }

  async clickNotification(idx: number) {
    await this.page.getByTestId(`p12-notif-card-${idx}`).click();
    await this.page.waitForLoadState('networkidle');
  }
}
