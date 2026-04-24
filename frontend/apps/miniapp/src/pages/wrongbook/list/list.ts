// S7 miniapp · SC-08.AC-1 · 列表 · Active/Mastered · cursor 分页 · 3s 轮询 analyzing 条目
import { api, WrongItemListResponse, WrongItemVO } from '../../../utils/api';
import { t } from '../../../utils/i18n';

const SUBJECT_OPTS = ['全部', 'math', 'physics', 'chemistry', 'english'];

Page({
  data: {
    status: 'active' as 'active' | 'mastered',
    subject: '',
    items: [] as WrongItemVO[],
    cursor: undefined as string | undefined,
    hasMore: false,
    loading: true,
    loadingMore: false,
    subjectPickerVisible: false,
    subjectOptions: SUBJECT_OPTS,
    i18n: {
      tab_active: t('wrongbook_list.tab_active'),
      tab_mastered: t('wrongbook_list.tab_mastered'),
      empty_active: t('wrongbook_list.empty_active'),
      empty_mastered: t('wrongbook_list.empty_mastered'),
      load_more: t('wrongbook_list.load_more'),
      item_analyzing: t('wrongbook_list.item_analyzing'),
      item_completed: t('wrongbook_list.item_completed'),
      item_error: t('wrongbook_list.item_error'),
    },
  },

  pollTimer: null as any,

  onLoad() {
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

  async fetchFirst() {
    this.setData({ loading: true, items: [], cursor: undefined });
    try {
      const res = await api.get<WrongItemListResponse>('/wrongbook/items', {
        params: {
          status: this.data.status,
          subject: this.data.subject || undefined,
          limit: 20,
        },
      });
      this.setData({
        items: res.items,
        cursor: res.next_cursor,
        hasMore: !!res.has_more,
        loading: false,
      });
      this.maybeStartPoll();
    } catch (e) {
      this.setData({ loading: false });
      wx.showToast({ title: t('common.error_network'), icon: 'none' });
    }
  },

  async loadMore() {
    if (!this.data.hasMore || this.data.loadingMore) return;
    this.setData({ loadingMore: true });
    try {
      const res = await api.get<WrongItemListResponse>('/wrongbook/items', {
        params: {
          status: this.data.status,
          subject: this.data.subject || undefined,
          cursor: this.data.cursor,
          limit: 20,
        },
      });
      this.setData({
        items: [...this.data.items, ...res.items],
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
    this.pollTimer = setInterval(() => this.refreshVisible(), 3000);
  },

  stopPoll() {
    if (this.pollTimer) {
      clearInterval(this.pollTimer);
      this.pollTimer = null;
    }
  },

  async refreshVisible() {
    if (!this.data.items.length) return;
    const analyzingIds = this.data.items
      .filter((i) => i.status === 'analyzing' || i.status === 'pending')
      .map((i) => i.id);
    if (!analyzingIds.length) return this.stopPoll();
    try {
      const res = await api.get<WrongItemListResponse>('/wrongbook/items', {
        params: { status: this.data.status, subject: this.data.subject || undefined, limit: 20 },
      });
      this.setData({ items: res.items });
      this.maybeStartPoll();
    } catch {
      // 网络错过 · 不中断轮询
    }
  },

  onTabChange(e: WechatMiniprogram.CustomEvent<{ index: number }>) {
    const next = e.detail.index === 0 ? 'active' : 'mastered';
    this.setData({ status: next });
    this.fetchFirst();
  },

  openSubjectPicker() {
    this.setData({ subjectPickerVisible: true });
  },

  closeSubjectPicker() {
    this.setData({ subjectPickerVisible: false });
  },

  onSubjectPick(e: WechatMiniprogram.CustomEvent<{ value: string }>) {
    const picked = e.detail.value === '全部' ? '' : e.detail.value;
    this.setData({ subject: picked, subjectPickerVisible: false });
    this.fetchFirst();
  },

  openDetail(e: WechatMiniprogram.CustomEvent & { currentTarget: { dataset: { id: string } } }) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: `/pages/wrongbook/detail/detail?id=${id}` });
  },
});
