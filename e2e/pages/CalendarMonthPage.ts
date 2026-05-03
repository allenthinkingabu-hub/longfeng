/**
 * P10 · 月历视图（spec/P10-calendar-month.spec.md）
 * SC-05 视图融合 · SC-06 通用事件
 */
import { type Page, expect } from '@playwright/test';
import { BasePage } from './_base';

export class CalendarMonthPage extends BasePage {
  readonly rootTestId = 'p10-root';

  static route = '/calendar';

  constructor(page: Page) { super(page); }

  async open() { await this.goto(CalendarMonthPage.route); }

  async getMonthTitle(): Promise<string> {
    return await this.byTestId('p10-month-nav-title').innerText();
  }

  async clickPrevMonth() {
    await this.byTestId('p10-month-nav-prev').click();
    await this.page.waitForLoadState('networkidle');
  }

  async clickNextMonth() {
    await this.byTestId('p10-month-nav-next').click();
    await this.page.waitForLoadState('networkidle');
  }

  async clickToday() {
    await this.byTestId('p10-month-nav-today').click();
  }

  async openCell(cellIdx: number) {
    await this.byTestId(`p10-month-grid-cell-${cellIdx}`).click();
    await this.page.waitForLoadState('networkidle');
  }

  /** 6 个 legend 全部存在（math/physics/chemistry/english/exam/family） */
  async assertLegendBarVisible() {
    for (const k of ['math', 'physics', 'chemistry', 'english', 'exam', 'family']) {
      await expect(this.byTestId(`p10-legend-bar-item-${k}`)).toBeVisible();
    }
  }

  /** Observer 形态：readonly banner 必须可见（SC-15 / 三形态同壳） */
  async assertReadonlyBanner(shouldShow: boolean) {
    if (shouldShow) {
      await expect(this.byTestId('p10-readonly-banner')).toBeVisible();
    } else {
      await expect(this.byTestId('p10-readonly-banner')).toHaveCount(0);
    }
  }
}
