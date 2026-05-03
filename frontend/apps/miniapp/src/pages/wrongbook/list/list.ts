// P05 wrongbook list · Mood B · STYLE-TRUTH §3
// 与 H5 WrongbookList 同 API 契约 · cursor 分页 · 3s 轮询 analyzing
import { api, WrongItemListResponse, WrongItemVO } from '../../../utils/api';
import { TEST_IDS } from '../../../utils/testids';
import { t } from '../../../utils/i18n';

const SUBJECT_LABEL: Record<string, string> = {
  math: '数学',
  physics: '物理',
  chemistry: '化学',
  english: '英语',
};

interface VM extends WrongItemVO {
  subject_label: string;
}

Page({
  data: {
    tids: TEST_IDS,
    i: {} as Record<string, string>,
    tab: 'active' as 'active' | 'archive',
    subjectFilter: '' as string,
    items: [] as VM[],
    cursor: undefined as string | undefined,
    hasMore: false,
    loading: true,
    loadingMore: false,
    statusLabel: {
      pending: '待处理',
      analyzing: 'AI 解析中',
      completed: '已解析',
      error: '解析失败',
    } as Record<string, string>,
  },

  pollTimer: null as number | null,

  onLoad() {
    this.setData({
      i: {
        tab_active: t('wrongbook_list.tab_active'),
        tab_archive: t('wrongbook_list.tab_archive'),
        empty_active: t('wrongbook_list.empty_active'),
        load_more: t('wrongbook_list.load_more'),
        loading: t('common.loading'),
      },
    });
    this.fetchFirst();
  },

  onShow() {
    this.maybeStartPoll();
  },

  onHide() {
    this.stopPoll();
  },

  onUnload() {
    this.stopPoll();
  },

  onPullDownRefresh() {
    this.fetchFirst().finally(() => wx.stopPullDownRefresh());
  },

  async fetchFirst(): Promise<void> {
    this.setData({ loading: true, items: [], cursor: undefined });
    try {
      const res = await api.get<WrongItemListResponse>('/wrong-items', {
        params: {
          status: this.data.tab === 'active' ? 'active' : 'archived',
          subject: this.data.subjectFilter || undefined,
          limit: 20,
        },
      });
      const items: VM[] = (res.items || []).map((it) => ({
        ...it,
        subject_label: SUBJECT_LABEL[it.subject] || it.subject,
      }));
      this.setData({
        items,
        cursor: res.next_cursor,
        hasMore: !!res.has_more,
        loading: false,
      });
      this.maybeStartPoll();
    } catch {
      this.setData({ loading: false });
      wx.showToast({ title: '加载失败', icon: 'none' });
    }
  },

  async onLoadMore() {
    if (!this.data.hasMore || this.data.loadingMore) return;
    this.setData({ loadingMore: true });
    try {
      const res = await api.get<WrongItemListResponse>('/wrong-items', {
        params: {
          status: this.data.tab === 'active' ? 'active' : 'archived',
          subject: this.data.subjectFilter || undefined,
          cursor: this.data.cursor,
          limit: 20,
        },
      });
      const newItems: VM[] = (res.items || []).map((it) => ({
        ...it,
        subject_label: SUBJECT_LABEL[it.subject] || it.subject,
      }));
      this.setData({
        items: [...this.data.items, ...newItems],
        cursor: res.next_cursor,
        hasMore: !!res.has_more,
        loadingMore: false,
      });
    } catch {
      this.setData({ loadingMore: false });
    }
  },

  maybeStartPoll() {
    const hasAnalyzing = this.data.items.some((i) => i.status === 'analyzing' || i.status === 'pending');
    if (!hasAnalyzing) return this.stopPoll();
    if (this.pollTimer) return;
    this.pollTimer = setInterval(() => this.fetchFirst(), 3000) as unknown as number;
  },

  stopPoll() {
    if (this.pollTimer) {
      clearInterval(this.pollTimer);
      this.pollTimer = null;
    }
  },

  onTab(e: WechatMiniprogram.TouchEvent) {
    const tab = e.currentTarget.dataset.tab as 'active' | 'archive';
    if (tab === this.data.tab) return;
    this.setData({ tab });
    this.fetchFirst();
  },

  onSubject(e: WechatMiniprogram.TouchEvent) {
    const subject = (e.currentTarget.dataset.subject || '') as string;
    this.setData({ subjectFilter: subject });
    this.fetchFirst();
  },

  onItemTap(e: WechatMiniprogram.TouchEvent) {
    const id = e.currentTarget.dataset.id as string;
    wx.navigateTo({ url: `/pages/wrongbook/detail/detail?id=${id}` });
  },

  onGoCapture() {
    wx.navigateTo({ url: '/pages/camera/capture/capture' });
  },
});
