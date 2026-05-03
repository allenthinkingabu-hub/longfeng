// P11 event detail · Mood B · STYLE-TRUTH §3
// 与 H5 EventDetail 三形态同壳：study / exam / general
import { api } from '../../../utils/api';
import { TEST_IDS } from '../../../utils/testids';
import { t } from '../../../utils/i18n';

interface EventVO {
  id: string;
  type: 'study' | 'exam' | 'general';
  title: string;
  time: string;
  subject: string;
  icon: string;
  relation?: string;
  note?: string;
  items?: { id: string; stem_text: string; subject_label: string; mastery: number }[];
}

const TYPE_LABEL: Record<string, string> = {
  study: '学习节点',
  exam: '考试',
  general: '事件',
};

Page({
  data: {
    tids: TEST_IDS,
    i: {} as Record<string, string>,
    event: null as EventVO | null,
    typeLabel: '',
  },

  async onLoad(query: Record<string, string>) {
    const id = query.id;
    this.setData({
      i: {
        review_now: t('event_detail.review_now'),
        share: t('event_detail.share'),
      },
    });
    if (!id) return wx.navigateBack();
    try {
      const event = await api.get<EventVO>(`/calendar/events/${id}`);
      this.setData({
        event,
        typeLabel: TYPE_LABEL[event.type] || '事件',
      });
    } catch {
      // demo fallback
      const demo: EventVO = {
        id,
        type: 'study',
        title: '一元二次方程 · 第 3 次复习',
        time: '今天 20:00',
        subject: '数学',
        icon: 'edit',
        relation: '5 道错题',
        items: [
          { id: '1', stem_text: '解方程 x² + 2x − 8 = 0', subject_label: '数学', mastery: 60 },
          { id: '2', stem_text: '求 x² = 25 的所有解', subject_label: '数学', mastery: 75 },
        ],
      };
      this.setData({ event: demo, typeLabel: TYPE_LABEL[demo.type] });
    }
  },

  onReviewNow() {
    const ev = this.data.event;
    if (!ev) return;
    wx.navigateTo({ url: `/pages/review/exec/exec?sessionId=${ev.id}` });
  },

  onShare() {
    wx.showShareMenu({ withShareTicket: true });
    wx.showToast({ title: '请使用右上角分享', icon: 'none' });
  },

  onShareAppMessage() {
    const ev = this.data.event;
    return {
      title: ev?.title || 'AI 错题本',
      path: `/pages/calendar/event/event?id=${ev?.id || ''}&shared=1`,
    };
  },
});
