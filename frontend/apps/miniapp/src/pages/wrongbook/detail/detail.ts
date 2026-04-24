// S7 miniapp · SC-02.AC-1 + SC-03.AC-1 · 详情 · Tag 编辑 + AI 讲解（request chunk 降级 · 微信无 SSE）+ 相似题
import { api, WrongItemVO } from '../../../utils/api';
import { t } from '../../../utils/i18n';

const SUBJECT_POOL = ['math', 'physics', 'chemistry', 'english'];

interface SimilarItem { id: string; stem_text: string; distance: number; subject: string }
interface SimilarResp { items: SimilarItem[] }

Page({
  data: {
    item: null as WrongItemVO | null,
    similar: [] as SimilarItem[],
    explain: '',
    subjectPool: SUBJECT_POOL,
    sheetOpen: false,
    localTags: [] as string[],
    customTag: '',
    savingTags: false,
    i18n: {
      tag_edit: t('wrongbook_detail.tag_edit'),
      tag_save: t('wrongbook_detail.tag_save'),
      explain_title: t('wrongbook_detail.explain_title'),
      explain_loading: t('wrongbook_detail.explain_loading'),
      similar_title: t('wrongbook_detail.similar_title'),
    },
  },

  reqTask: null as any,

  async onLoad(opt: { id?: string }) {
    const id = opt.id;
    if (!id) return wx.navigateBack();
    try {
      const item = await api.get<WrongItemVO>(`/wrongbook/items/${id}`);
      this.setData({ item, localTags: item.tags });
      if (item.status === 'completed') {
        this.loadSimilar(id);
        this.subscribeExplain(id);
      }
    } catch {
      wx.showToast({ title: t('common.error_network'), icon: 'none' });
    }
  },

  onUnload() {
    if (this.reqTask?.abort) this.reqTask.abort();
  },

  async loadSimilar(id: string) {
    try {
      const res = await api.get<SimilarResp>(`/analysis/${id}/similar`, { params: { k: 3 } });
      this.setData({
        similar: res.items.map((s) => ({ ...s, distance: Number(s.distance.toFixed(2)) })),
      });
    } catch {
      // silent
    }
  },

  subscribeExplain(id: string) {
    // 微信小程序无 EventSource · 降级 wx.request + enableChunked + onChunkReceived
    const token = wx.getStorageSync('access_token') || '';
    this.reqTask = wx.request({
      url: `https://api.longfeng.local/api/v1/analysis/${id}`,
      method: 'GET',
      header: {
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        Accept: 'text/event-stream',
      },
      enableChunked: true,
      success: () => {},
      fail: () => {
        this.setData({ explain: t('wrongbook_detail.explain_error') });
      },
    });
    this.reqTask.onChunkReceived((res: { data: ArrayBuffer }) => {
      const text = this.decodeChunk(res.data);
      // 简化解析 SSE 格式：`data: {"chunk":"..."}\n\n`
      const lines = text.split(/\n/);
      for (const line of lines) {
        if (!line.startsWith('data:')) continue;
        const body = line.slice(5).trim();
        if (!body || body === '[DONE]') continue;
        try {
          const obj = JSON.parse(body);
          if (obj.chunk) this.setData({ explain: this.data.explain + obj.chunk });
        } catch {
          // 非 JSON 段 · 原样拼
          this.setData({ explain: this.data.explain + body });
        }
      }
    });
  },

  decodeChunk(buf: ArrayBuffer): string {
    const arr = new Uint8Array(buf);
    let s = '';
    for (let i = 0; i < arr.length; i++) s += String.fromCharCode(arr[i]);
    try { return decodeURIComponent(escape(s)); } catch { return s; }
  },

  openTagSheet() {
    this.setData({ sheetOpen: true, localTags: this.data.item?.tags || [] });
  },

  closeTagSheet() {
    this.setData({ sheetOpen: false });
  },

  toggleTag(e: WechatMiniprogram.CustomEvent & { currentTarget: { dataset: { tag: string } } }) {
    const tag = e.currentTarget.dataset.tag;
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
      await api.patch(`/wrongbook/items/${it.id}/tags`, this.data.localTags, {
        headers: { 'If-Match': String(it.version) },
      });
      this.setData({
        item: { ...it, tags: this.data.localTags, version: it.version + 1 },
        sheetOpen: false,
        savingTags: false,
      });
    } catch (e) {
      this.setData({ savingTags: false });
      wx.showToast({ title: t('common.error_network'), icon: 'none' });
    }
  },
});
