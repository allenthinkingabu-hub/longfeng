// P08 review exec · Mood B + 自评 3 档铁律 1 例外
// 与 H5 ReviewExec 同 API · POST /api/v1/review-plans/:id/grade
import { api } from '../../../utils/api';
import { TEST_IDS } from '../../../utils/testids';
import { t } from '../../../utils/i18n';

interface ReviewItem {
  id: string;
  subject: string;
  subject_label: string;
  stem_text: string;
  answer?: string;
  explain?: string;
}

interface SessionResp {
  id: string;
  items: ReviewItem[];
}

const SUBJECT_LABEL: Record<string, string> = {
  math: '数学',
  physics: '物理',
  chemistry: '化学',
  english: '英语',
};

Page({
  data: {
    tids: TEST_IDS,
    i: {} as Record<string, string>,
    sessionId: '',
    items: [] as ReviewItem[],
    cur: 0,
    revealed: false,
    currentItem: null as ReviewItem | null,
    progressPct: 0,
  },

  onLoad(query: Record<string, string>) {
    this.setData({
      sessionId: query.sessionId || query.wrongItemId || '',
      i: {
        reveal: t('review_exec.reveal'),
        self_grade_title: t('review_exec.self_grade_title'),
        forgot: t('review_exec.forgot'),
        partial: t('review_exec.partial'),
        mastered: t('review_exec.mastered'),
        progress: '1 / 1',
      },
    });
    this.fetch();
  },

  async fetch() {
    if (!this.data.sessionId) {
      // 单题快速复习模式（来自 detail）
      this.setData({
        items: [
          {
            id: 'demo',
            subject: 'math',
            subject_label: '数学',
            stem_text: '求解 x² + 2x − 8 = 0',
            answer: 'x = −4, x = 2',
            explain: '配方法：(x+1)² = 9，开方得 x = −4 或 x = 2',
          },
        ],
        currentItem: null,
        cur: 0,
      });
      this.refresh();
      return;
    }
    try {
      const res = await api.get<SessionResp>(`/review-plans/${this.data.sessionId}`);
      const items: ReviewItem[] = (res.items || []).map((it) => ({
        ...it,
        subject_label: SUBJECT_LABEL[it.subject] || it.subject,
      }));
      this.setData({ items, cur: 0 });
      this.refresh();
    } catch {
      wx.showToast({ title: '加载失败', icon: 'none' });
    }
  },

  refresh() {
    const cur = this.data.cur;
    const items = this.data.items;
    const currentItem = items[cur] || null;
    const progressPct = items.length ? Math.round(((cur + 1) / items.length) * 100) : 0;
    this.setData({
      currentItem,
      progressPct,
      revealed: false,
      i: { ...this.data.i, progress: `${cur + 1} / ${items.length || 1}` },
    });
  },

  onReveal() {
    this.setData({ revealed: true });
  },

  async onGrade(e: WechatMiniprogram.TouchEvent) {
    const grade = e.currentTarget.dataset.grade as 'forgot' | 'partial' | 'mastered';
    const item = this.data.currentItem;
    if (!item) return;
    try {
      await api.post(`/review-plans/${this.data.sessionId || 'demo'}/grade`, {
        item_id: item.id,
        grade,
      });
    } catch {
      // 软失败 · 仍推进
    }
    if (this.data.cur + 1 >= this.data.items.length) {
      // 完成
      wx.redirectTo({
        url: `/pages/review/done/done?sessionId=${this.data.sessionId}&total=${this.data.items.length}`,
      });
      return;
    }
    this.setData({ cur: this.data.cur + 1 });
    this.refresh();
  },

  onQuit() {
    wx.showModal({
      title: t('review_exec.quit_confirm_title'),
      content: t('review_exec.quit_confirm_body'),
      confirmText: t('review_exec.quit'),
      cancelText: t('common.cancel'),
      success: (r) => {
        if (r.confirm) {
          wx.navigateBack();
        }
      },
    });
  },
});
