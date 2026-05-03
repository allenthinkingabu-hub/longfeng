// P10 calendar month · Mood B · STYLE-TRUTH §3
// 与 H5 CalendarMonth 同 API · GET /api/v1/calendar/month?year=&month=
import { api } from '../../../utils/api';
import { TEST_IDS } from '../../../utils/testids';
import { t } from '../../../utils/i18n';
import { requestSubscribe, reportSubscribeStatus, TEMPLATE_IDS } from '../../../utils/subscribe-msg';

interface CalCell {
  key: string;
  d: number;
  outside: boolean;
  today: boolean;
  dots: ('done' | 'pending' | 'event')[];
}

interface CalEvent {
  id: string;
  type: 'study' | 'exam' | 'general';
  title: string;
  time: string;
  subject: string;
  icon: string;
  date: string;
}

interface MonthResp {
  year: number;
  month: number;
  events_by_day: Record<string, CalEvent[]>;
  status_by_day: Record<string, ('done' | 'pending' | 'event')[]>;
}

const WEEKDAYS = ['日', '一', '二', '三', '四', '五', '六'];

Page({
  data: {
    tids: TEST_IDS,
    i: {} as Record<string, string>,
    weekdays: WEEKDAYS,
    cursor: { year: new Date().getFullYear(), month: new Date().getMonth() + 1 },
    monthLabel: '',
    cells: [] as CalCell[],
    events: [] as CalEvent[],
    eventsByDay: {} as Record<string, CalEvent[]>,
    selectedKey: '',
    selectedLabel: '',
    subscribed: false,
  },

  onLoad() {
    this.setData({
      i: {
        subscribe: t('calendar_month.subscribe'),
        subscribe_done: t('calendar_month.subscribe_done'),
        legend_done: t('calendar_month.legend_done'),
        legend_pending: t('calendar_month.legend_pending'),
        legend_event: t('calendar_month.legend_event'),
        no_events: t('calendar_month.no_events'),
      },
    });
    this.fetch();
  },

  async fetch() {
    const { year, month } = this.data.cursor;
    this.setData({ monthLabel: `${year} 年 ${month} 月` });
    let resp: MonthResp = { year, month, events_by_day: {}, status_by_day: {} };
    try {
      resp = await api.get<MonthResp>('/calendar/month', { params: { year, month } });
    } catch {
      // 静默 · 显示空月历
    }
    const cells = this.buildCells(year, month, resp.status_by_day || {});
    const today = new Date();
    const todayKey = today.getFullYear() === year && today.getMonth() + 1 === month
      ? `${year}-${String(month).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
      : '';
    const selectedKey = this.data.selectedKey || todayKey || cells.find((c) => !c.outside)?.key || '';
    const events = (resp.events_by_day || {})[selectedKey] || [];
    this.setData({
      cells,
      eventsByDay: resp.events_by_day || {},
      events,
      selectedKey,
      selectedLabel: this.formatLabel(selectedKey),
    });
  },

  buildCells(year: number, month: number, statusByDay: Record<string, ('done' | 'pending' | 'event')[]>): CalCell[] {
    const first = new Date(year, month - 1, 1);
    const startDow = first.getDay();
    const daysInMonth = new Date(year, month, 0).getDate();
    const prevMonthDays = new Date(year, month - 1, 0).getDate();
    const cells: CalCell[] = [];
    const today = new Date();
    const isCurMonth = today.getFullYear() === year && today.getMonth() + 1 === month;

    // 前置补
    for (let i = startDow - 1; i >= 0; i--) {
      const d = prevMonthDays - i;
      const key = `${year}-${String(month - 1).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
      cells.push({ key, d, outside: true, today: false, dots: [] });
    }
    // 本月
    for (let d = 1; d <= daysInMonth; d++) {
      const key = `${year}-${String(month).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
      cells.push({
        key,
        d,
        outside: false,
        today: isCurMonth && d === today.getDate(),
        dots: (statusByDay[key] || []).slice(0, 3),
      });
    }
    // 后置补到 6 行
    while (cells.length % 7 !== 0 || cells.length < 42) {
      const lastIdx = cells.length;
      const d = lastIdx - daysInMonth - startDow + 1;
      const key = `${year}-${String(month + 1).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
      cells.push({ key, d, outside: true, today: false, dots: [] });
      if (cells.length >= 42) break;
    }
    return cells.slice(0, 42);
  },

  formatLabel(key: string): string {
    if (!key) return '';
    const [, m, d] = key.split('-');
    return `${parseInt(m, 10)} 月 ${parseInt(d, 10)} 日`;
  },

  onPrev() {
    let { year, month } = this.data.cursor;
    month -= 1;
    if (month < 1) {
      month = 12;
      year -= 1;
    }
    this.setData({ cursor: { year, month }, selectedKey: '' });
    this.fetch();
  },

  onNext() {
    let { year, month } = this.data.cursor;
    month += 1;
    if (month > 12) {
      month = 1;
      year += 1;
    }
    this.setData({ cursor: { year, month }, selectedKey: '' });
    this.fetch();
  },

  onCellTap(e: WechatMiniprogram.TouchEvent) {
    const key = e.currentTarget.dataset.key as string;
    const events = this.data.eventsByDay[key] || [];
    this.setData({ selectedKey: key, events, selectedLabel: this.formatLabel(key) });
  },

  onEventTap(e: WechatMiniprogram.TouchEvent) {
    const id = e.currentTarget.dataset.id as string;
    wx.navigateTo({ url: `/pages/calendar/event/event?id=${id}` });
  },

  async onSubscribe() {
    try {
      const status = await requestSubscribe([TEMPLATE_IDS.reviewReminder, TEMPLATE_IDS.examDay]);
      const ok = Object.values(status).some((v) => v === 'accept');
      if (ok) {
        this.setData({ subscribed: true });
        void reportSubscribeStatus(status);
        wx.showToast({ title: '已开启提醒', icon: 'success' });
      } else {
        wx.showToast({ title: '未开启', icon: 'none' });
      }
    } catch {
      wx.showToast({ title: '订阅失败', icon: 'none' });
    }
  },
});
