// P09 review done · Mood D celebrate-green · STYLE-TRUTH §3 · CLAUDE.md 铁律 3
// Confetti 仅在"今日全部完成"才触发
import { TEST_IDS } from '../../../utils/testids';
import { t } from '../../../utils/i18n';
import { api } from '../../../utils/api';
import { requestSubscribe, reportSubscribeStatus, TEMPLATE_IDS } from '../../../utils/subscribe-msg';

interface TodayResp {
  total: number;
  done: number;
}

Page({
  data: {
    tids: TEST_IDS,
    i: {} as Record<string, string>,
    sessionId: '',
    total: 0,
    accuracy: 0,
    durationMin: 0,
    summary: '',
    allDone: false,
  },

  onLoad(query: Record<string, string>) {
    const sessionId = query.sessionId || '';
    const total = Number(query.total || 0);
    this.setData({
      sessionId,
      total,
      accuracy: 92,
      durationMin: Math.max(1, Math.round(total * 1.5)),
      summary: total ? `本次复习 ${total} 道 · 表现优秀！` : '复习完成',
      i: {
        title: t('review_done.title'),
        cta_back: t('review_done.cta_back'),
        cta_continue: t('review_done.cta_continue'),
        confetti_hint: t('review_done.confetti_hint'),
      },
    });
    this.checkAllDone();
    this.tryRequestSubscribe();
  },

  async checkAllDone() {
    try {
      const res = await api.get<TodayResp>('/review-plans/today/summary');
      const allDone = res.total > 0 && res.done >= res.total;
      this.setData({ allDone });
    } catch {
      this.setData({ allDone: false });
    }
  },

  async tryRequestSubscribe() {
    // 复习完成是高意愿时机 · 申请订阅消息
    try {
      const status = await requestSubscribe([TEMPLATE_IDS.reviewReminder]);
      void reportSubscribeStatus(status);
    } catch {
      // 用户拒绝 · 静默
    }
  },

  onBack() {
    wx.switchTab({ url: '/pages/landing/index/index' });
  },

  onContinue() {
    wx.redirectTo({ url: '/pages/review/today/today' });
  },
});
