// S7 miniapp · SC-01.AC-1 + SC-07.AC-1 · 录入 · 三 Tab · 10MB · 原图（Q1=b）· 草稿
import { api } from '../../../utils/api';
import { t } from '../../../utils/i18n';

const MAX_BYTES = 10 * 1024 * 1024;
const DRAFT_KEY = 'lf:capture:draft:v1';
const DRAFT_TTL_MS = 7 * 24 * 3600 * 1000;

type Tab = 'camera' | 'gallery' | 'manual';
interface PresignResp { upload_url: string; file_key: string; ttl_seconds: number; bucket: string }

Page({
  data: {
    tabIdx: 2 as 0 | 1 | 2, // 默认手动
    subject: 'math',
    stem: '',
    fileKey: '' as string,
    uploadPct: null as number | null,
    submitting: false,
    i18n: {
      tab_camera: t('capture.tab_camera'),
      tab_gallery: t('capture.tab_gallery'),
      tab_manual: t('capture.tab_manual'),
      field_subject: t('capture.field_subject'),
      field_stem: t('capture.field_stem'),
      submit: t('capture.submit'),
      draft_hint: t('capture.draft_hint'),
    },
  },

  onLoad() {
    // 恢复草稿
    try {
      const raw = wx.getStorageSync(DRAFT_KEY);
      if (!raw) return;
      const d = JSON.parse(raw) as { subject: string; stem_text: string; file_key?: string; saved_at: number };
      if (Date.now() - d.saved_at > DRAFT_TTL_MS) {
        wx.removeStorageSync(DRAFT_KEY);
        return;
      }
      this.setData({ subject: d.subject, stem: d.stem_text, fileKey: d.file_key || '' });
      wx.showToast({ title: t('capture.draft_restored') || '草稿已恢复', icon: 'none' });
    } catch {
      wx.removeStorageSync(DRAFT_KEY);
    }
  },

  onUnload() {
    this.saveDraft();
  },

  onHide() {
    this.saveDraft();
  },

  saveDraft() {
    if (!this.data.stem && !this.data.fileKey) return;
    wx.setStorageSync(DRAFT_KEY, JSON.stringify({
      subject: this.data.subject,
      stem_text: this.data.stem,
      file_key: this.data.fileKey,
      saved_at: Date.now(),
    }));
  },

  onTabChange(e: WechatMiniprogram.CustomEvent<{ index: number }>) {
    const idx = e.detail.index as 0 | 1 | 2;
    this.setData({ tabIdx: idx });
    if (idx === 0) this.pickByCamera();
    else if (idx === 1) this.pickByGallery();
  },

  onSubjectInput(e: WechatMiniprogram.CustomEvent<{ value: string }>) {
    this.setData({ subject: e.detail.value });
  },

  onStemInput(e: WechatMiniprogram.CustomEvent<{ value: string }>) {
    this.setData({ stem: e.detail.value });
  },

  pickByCamera() {
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['camera'],
      camera: 'back',
      success: (res) => this.handleFile(res.tempFiles[0].tempFilePath, res.tempFiles[0].size),
    });
  },

  pickByGallery() {
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['album'],
      success: (res) => this.handleFile(res.tempFiles[0].tempFilePath, res.tempFiles[0].size),
    });
  },

  async handleFile(filePath: string, size: number) {
    if (size > MAX_BYTES) {
      wx.showToast({ title: t('capture.size_exceeded'), icon: 'none', duration: 3000 });
      return;
    }
    this.setData({ uploadPct: 0 });
    try {
      const presign = await api.post<PresignResp>('/files/presign', { mime: 'image/jpeg', size });
      this.setData({ uploadPct: 10 });
      await this.uploadToOss(presign.upload_url, filePath);
      this.setData({ uploadPct: 80 });
      await api.post(`/files/complete/${presign.file_key}`);
      this.setData({ uploadPct: 100, fileKey: presign.file_key });
      setTimeout(() => this.setData({ uploadPct: null }), 400);
    } catch (e) {
      this.setData({ uploadPct: null });
      wx.showToast({ title: t('capture.ocr_fallback'), icon: 'none' });
    }
  },

  uploadToOss(url: string, filePath: string): Promise<void> {
    return new Promise((resolve, reject) => {
      wx.uploadFile({
        url,
        filePath,
        name: 'file',
        success: () => resolve(),
        fail: (e) => reject(e),
      });
    });
  },

  async submit() {
    if (!this.data.stem || this.data.stem.length < 2) return;
    this.setData({ submitting: true });
    try {
      await api.post('/wrongbook/items', {
        subject: this.data.subject,
        stem_text: this.data.stem,
        image_id: this.data.fileKey || undefined,
      });
      wx.removeStorageSync(DRAFT_KEY);
      this.setData({ submitting: false, stem: '', fileKey: '' });
      wx.switchTab({ url: '/pages/wrongbook/list/list' });
    } catch (e) {
      this.setData({ submitting: false });
      wx.showToast({ title: t('common.error_network'), icon: 'none' });
    }
  },
});
