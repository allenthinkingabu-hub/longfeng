// P06 wrongbook detail · Mood B · STYLE-TRUTH §3
// 与 H5 WrongbookDetail 同 API · AI 讲解走 wx.request enableChunked (微信无 SSE)
import { api, WrongItemVO } from '../../../utils/api';
import { TEST_IDS } from '../../../utils/testids';
import { t } from '../../../utils/i18n';

const SUBJECT_LABEL: Record<string, string> = {
  math: '数学',
  physics: '物理',
  chemistry: '化学',
  english: '英语',
};
const SUBJECT_POOL = ['基础', '提高', '错过 3 次以上', '考前重点'];

interface SimilarItem {
  id: string;
  stem_text: string;
  distance: number;
  subject: string;
}
interface SimilarResp {
  items: SimilarItem[];
}

Page({
  data: {
    tids: TEST_IDS,
    i: {} as Record<string, string>,
    item: null as WrongItemVO | null,
    subjectLabel: '',
    similar: [] as SimilarItem[],
    explain: '',
    subjectPool: SUBJECT_POOL,
    sheetOpen: false,
    localTags: [] as string[],
    customTag: '',
    savingTags: false,
  },

  reqTask: null as { abort?: () => void; onChunkReceived?: (cb: (r: { data: ArrayBuffer }) => void) => void } | null,

  async onLoad(opt: Record<string, string>) {
    const id = opt.id;
    this.setData({
      i: {
        tag_edit: t('wrongbook_detail.tag_edit'),
        tag_save: t('wrongbook_detail.tag_save'),
        explain_title: t('wrongbook_detail.explain_title'),
        explain_loading: t('wrongbook_detail.explain_loading'),
        similar_title: t('wrongbook_detail.similar_title'),
        archive: t('wrongbook_detail.archive'),
        mastery: t('wrongbook_detail.mastery'),
      },
    });
    if (!id) return wx.navigateBack();
    try {
      const item = await api.get<WrongItemVO>(`/wrong-items/${id}`);
      this.setData({
        item,
        subjectLabel: SUBJECT_LABEL[item.subject] || item.subject,
        localTags: item.tags || [],
      });
      if (item.status === 'completed') {
        this.loadSimilar(id);
        this.subscribeExplain(id);
      }
    } catch {
      wx.showToast({ title: '加载失败', icon: 'none' });
    }
  },

  onUnload() {
    if (this.reqTask?.abort) this.reqTask.abort();
  },

  async loadSimilar(id: string) {
    try {
      const res = await api.get<SimilarResp>(`/analysis/${id}/similar`, { params: { k: 3 } });
      this.setData({
        similar: (res.items || []).map((s) => ({ ...s, distance: Number((s.distance ?? 0).toFixed(2)) })),
      });
    } catch {
      // silent
    }
  },

  subscribeExplain(id: string) {
    const token = wx.getStorageSync('access_token') || '';
    const task = wx.request({
      url: `https://api.longfeng.local/api/v1/analysis/${id}`,
      method: 'GET',
      header: {
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        Accept: 'text/event-stream',
      },
      enableChunked: true,
      success: () => {
        // ignore
      },
      fail: () => {
        this.setData({ explain: '加载讲解失败' });
      },
    }) as unknown as { abort?: () => void; onChunkReceived?: (cb: (r: { data: ArrayBuffer }) => void) => void };
    this.reqTask = task;
    task.onChunkReceived?.((res) => {
      const text = this.decodeChunk(res.data);
      const lines = text.split(/\n/);
      for (const line of lines) {
        if (!line.startsWith('data:')) continue;
        const body = line.slice(5).trim();
        if (!body || body === '[DONE]') continue;
        try {
          const obj = JSON.parse(body) as { chunk?: string };
          if (obj.chunk) this.setData({ explain: this.data.explain + obj.chunk });
        } catch {
          this.setData({ explain: this.data.explain + body });
        }
      }
    });
  },

  decodeChunk(buf: ArrayBuffer): string {
    const arr = new Uint8Array(buf);
    let s = '';
    for (let i = 0; i < arr.length; i++) s += String.fromCharCode(arr[i]);
    try {
      return decodeURIComponent(escape(s));
    } catch {
      return s;
    }
  },

  openTagSheet() {
    this.setData({ sheetOpen: true, localTags: this.data.item?.tags || [] });
  },

  closeTagSheet() {
    this.setData({ sheetOpen: false });
  },

  toggleTag(e: WechatMiniprogram.TouchEvent) {
    const tag = e.currentTarget.dataset.tag as string;
    const arr = this.data.localTags.slice();
    const i = arr.indexOf(tag);
    if (i >= 0) arr.splice(i, 1);
    else arr.push(tag);
    this.setData({ localTags: arr });
  },

  onCustomInput(e: WechatMiniprogram.CustomEvent<{ value: string }>) {
    this.setData({ customTag: e.detail.value });
  },

  addCustom() {
    const v = this.data.customTag.trim();
    if (!v) return;
    const customCount = this.data.localTags.filter((x) => !SUBJECT_POOL.includes(x)).length;
    if (customCount >= 5) {
      wx.showToast({ title: '最多 5 个自定义标签', icon: 'none' });
      return;
    }
    this.setData({ localTags: [...this.data.localTags, v], customTag: '' });
  },

  async saveTags() {
    const it = this.data.item;
    if (!it) return;
    this.setData({ savingTags: true });
    try {
      await api.patch(`/wrong-items/${it.id}/tags`, this.data.localTags, {
        headers: { 'If-Match': String(it.version) },
      });
      this.setData({
        item: { ...it, tags: this.data.localTags, version: it.version + 1 },
        sheetOpen: false,
        savingTags: false,
      });
      wx.showToast({ title: '已保存', icon: 'success' });
    } catch {
      this.setData({ savingTags: false });
      wx.showToast({ title: '保存失败', icon: 'none' });
    }
  },

  onArchive() {
    const it = this.data.item;
    if (!it) return;
    wx.showModal({
      title: t('wrongbook_detail.archive'),
      content: t('wrongbook_detail.archive_confirm'),
      success: async (r) => {
        if (!r.confirm) return;
        try {
          await api.patch(`/wrong-items/${it.id}/archive`, { archived: true }, { headers: { 'If-Match': String(it.version) } });
          wx.showToast({ title: '已归档', icon: 'success' });
          setTimeout(() => wx.navigateBack(), 800);
        } catch {
          wx.showToast({ title: '归档失败', icon: 'none' });
        }
      },
    });
  },

  onReviewNow() {
    const it = this.data.item;
    if (!it) return;
    wx.navigateTo({ url: `/pages/review/exec/exec?wrongItemId=${it.id}` });
  },
});
