// P07 review today · Mood A · STYLE-TRUTH §3
// 与 H5 ReviewToday 同 API · GET /api/v1/review-plans?status=scheduled&date=today
import { api } from '../../../utils/api';
import { TEST_IDS } from '../../../utils/testids';
import { t } from '../../../utils/i18n';

interface SessionVO {
  id: string;
  subject: string;
  title: string;
  time: string;
  itemCount: number;
  eta: number;
  state: 'scheduled' | 'active' | 'done';
  group: 'morning' | 'afternoon' | 'evening';
}

interface PlanResp {
  total: number;
  done: number;
  sessions: SessionVO[];
}

interface Group {
  key: string;
  label: string;
  eta: number;
  items: SessionVO[];
}

Page({
  data: {
    tids: TEST_IDS,
    i: {} as Record<string, string>,
    summary: { total: 0, done: 0, percent: 0, eta: 0 },
    groups: [] as Group[],
  },

  onLoad() {
    this.setData({
      i: {
        title: t('review_today.title'),
        cta_start_all: t('review_today.cta_start_all'),
        empty: t('review_today.empty'),
        group_morning: t('review_today.group_morning'),
        group_afternoon: t('review_today.group_afternoon'),
        group_evening: t('review_today.group_evening'),
      },
    });
    this.fetch();
  },

  onShow() {
    this.fetch();
  },

  async fetch() {
    try {
      const res = await api.get<PlanResp>('/review-plans/today');
      const total = res.total || 0;
      const done = res.done || 0;
      const percent = total ? Math.round((done / total) * 100) : 0;
      const sessions = res.sessions || [];
      const eta = sessions.filter((s) => s.state !== 'done').reduce((a, s) => a + (s.eta || 0), 0);
      const grouped: Record<string, Group> = {
        morning: { key: 'morning', label: t('review_today.group_morning'), eta: 0, items: [] },
        afternoon: { key: 'afternoon', label: t('review_today.group_afternoon'), eta: 0, items: [] },
        evening: { key: 'evening', label: t('review_today.group_evening'), eta: 0, items: [] },
      };
      for (const s of sessions) {
        const g = grouped[s.group] || grouped.morning;
        g.items.push(s);
        g.eta += s.eta || 0;
      }
      const groups = Object.values(grouped).filter((g) => g.items.length);
      this.setData({
        summary: { total, done, percent, eta },
        groups,
      });
    } catch {
      // 静默 · 显示空态由 groups 长度决定
      this.setData({ groups: [], summary: { total: 0, done: 0, percent: 0, eta: 0 } });
    }
  },

  onStartAll() {
    const first = this.data.groups[0]?.items.find((s) => s.state !== 'done');
    if (!first) {
      wx.showToast({ title: '今日已全部完成', icon: 'success' });
      return;
    }
    wx.navigateTo({ url: `/pages/review/exec/exec?sessionId=${first.id}` });
  },

  onSessionTap(e: WechatMiniprogram.TouchEvent) {
    const id = e.currentTarget.dataset.id as string;
    wx.navigateTo({ url: `/pages/review/exec/exec?sessionId=${id}` });
  },
});
