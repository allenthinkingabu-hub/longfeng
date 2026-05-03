// P12 notifications · Mood B · STYLE-TRUTH §3
// 与 H5 Notifications 同 API · GET /api/v1/notifications
import { api } from '../../../utils/api';
import { TEST_IDS } from '../../../utils/testids';
import { t } from '../../../utils/i18n';
import { requestSubscribe, reportSubscribeStatus, TEMPLATE_IDS } from '../../../utils/subscribe-msg';

interface NotifVO {
  id: string;
  type: 'review' | 'exam' | 'share';
  title: string;
  body: string;
  time: string;
  unread: boolean;
  icon: string;
  link?: string;
}

interface NotifResp {
  items: NotifVO[];
  next_cursor?: string;
}

const ICON_COLORS: Record<string, string> = {
  review: '#5856D6',
  exam: '#FF2D55',
  share: '#30B0C7',
};

Page({
  data: {
    tids: TEST_IDS,
    i: {} as Record<string, string>,
    list: [] as NotifVO[],
    iconColors: ICON_COLORS,
    subscribed: false,
  },

  onLoad() {
    this.setData({
      i: {
        title: t('notification.title'),
        mark_all_read: t('notification.mark_all_read'),
        subscribe_cta: t('notification.subscribe_cta'),
        subscribe_hint: t('notification.subscribe_hint'),
        empty: t('notification.empty'),
      },
      subscribed: !!wx.getStorageSync('subscribed_review'),
    });
    this.fetch();
  },

  onShow() {
    this.fetch();
  },

  onPullDownRefresh() {
    this.fetch().finally(() => wx.stopPullDownRefresh());
  },

  async fetch() {
    try {
      const res = await api.get<NotifResp>('/notifications');
      this.setData({ list: res.items || [] });
    } catch {
      // demo
      this.setData({
        list: [
          { id: '1', type: 'review', title: '今晚 20:00 复习 5 道', body: '一元二次方程 · 第 3 节点', time: '5 分钟前', unread: true, icon: 'clock-o' },
          { id: '2', type: 'exam', title: '考试日 · 数学单元测', body: '后天 9:00 · 准备好了吗？', time: '1 小时前', unread: true, icon: 'medal-o' },
          { id: '3', type: 'share', title: '妈妈 查看了你的进度', body: '本周完成度 85%', time: '昨天', unread: false, icon: 'friends-o' },
        ],
      });
    }
  },

  async onMarkAll() {
    try {
      await api.post('/notifications/mark-all-read');
    } catch {
      // ignore
    }
    this.setData({
      list: this.data.list.map((it) => ({ ...it, unread: false })),
    });
    wx.showToast({ title: '已标记', icon: 'success' });
  },

  onMsgTap(e: WechatMiniprogram.TouchEvent) {
    const id = e.currentTarget.dataset.id as string;
    const it = this.data.list.find((x) => x.id === id);
    if (!it) return;
    // 点击即标记已读
    this.setData({
      list: this.data.list.map((x) => (x.id === id ? { ...x, unread: false } : x)),
    });
    if (it.link) {
      wx.navigateTo({ url: it.link }).catch(() => {
        // ignore
      });
    }
  },

  async onSubscribe() {
    try {
      const status = await requestSubscribe([TEMPLATE_IDS.reviewReminder, TEMPLATE_IDS.examDay, TEMPLATE_IDS.familyShare]);
      const ok = Object.values(status).some((v) => v === 'accept');
      if (ok) {
        this.setData({ subscribed: true });
        wx.setStorageSync('subscribed_review', '1');
        void reportSubscribeStatus(status);
        wx.showToast({ title: '已开启', icon: 'success' });
      } else {
        wx.showToast({ title: '未开启', icon: 'none' });
      }
    } catch {
      wx.showToast({ title: '订阅失败', icon: 'none' });
    }
  },
});
