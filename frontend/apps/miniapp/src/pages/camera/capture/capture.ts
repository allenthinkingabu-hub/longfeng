// P02 capture · Mood C dark-camera · STYLE-TRUTH §3
// 与 H5 Capture 同语义 · 选图后 navigateTo analyzing 携带 taskId
import { TEST_IDS } from '../../../utils/testids';
import { t } from '../../../utils/i18n';
import { api } from '../../../utils/api';
import Toast from '@vant/weapp/toast/toast';

const SIZE_LIMIT_MB = 10;

interface UploadResp {
  task_id: string;
  upload_url?: string;
  ticket?: string;
}

Page({
  data: {
    tids: TEST_IDS,
    i: {} as Record<string, string>,
    subject: 'math' as 'math' | 'physics' | 'chemistry' | 'english',
    detectReady: false,
    paperLine1: 'Solve: x² + 2x − 8 = 0',
    paperLine2: 'x = ?',
    paperMark: '?? 漏写 ±',
  },

  _detectTimer: null as number | null,

  onLoad() {
    this.setData({
      i: {
        title: t('capture.title'),
        tab_camera: t('capture.tab_camera'),
        tab_gallery: t('capture.tab_gallery'),
        tab_manual: t('capture.tab_manual'),
        detect_ready: t('capture.detect_ready'),
        detect_align: t('capture.detect_align'),
        tip: t('capture.tip'),
        subj_math: t('capture.subj_math'),
        subj_physics: t('capture.subj_physics'),
        subj_chemistry: t('capture.subj_chemistry'),
        subj_english: t('capture.subj_english'),
      },
    });
    // 模拟 1.2s 后 detect ready · 真机由摄像头帧分析驱动
    this._detectTimer = setTimeout(() => this.setData({ detectReady: true }), 1200) as unknown as number;
  },

  onUnload() {
    if (this._detectTimer) clearTimeout(this._detectTimer);
  },

  onSubject(e: WechatMiniprogram.TouchEvent) {
    const subject = e.currentTarget.dataset.subject as typeof this.data.subject;
    this.setData({ subject });
  },

  onClose() {
    wx.navigateBack({ delta: 1 });
  },

  onFlashToggle() {
    Toast({ message: '闪光灯切换', position: 'top', duration: 1200 });
  },

  onShutter() {
    if (!this.data.detectReady) {
      Toast({ message: '请先对齐题面', duration: 1200 });
      return;
    }
    this.captureImage('camera');
  },

  onGallery() {
    this.captureImage('album');
  },

  onManual() {
    wx.navigateTo({
      url: `/pages/camera/result/result?manual=1&subject=${this.data.subject}`,
    });
  },

  async captureImage(sourceType: 'camera' | 'album') {
    try {
      const res = await wx.chooseMedia({
        count: 1,
        mediaType: ['image'],
        sourceType: [sourceType],
        camera: 'back',
      });
      const file = res.tempFiles[0];
      if (file.size > SIZE_LIMIT_MB * 1024 * 1024) {
        Toast({ message: t('capture.size_exceeded'), duration: 2000 });
        return;
      }
      // 草稿持久化（重启恢复）
      try {
        wx.setStorageSync('capture_draft', { subject: this.data.subject, tempPath: file.tempFilePath });
      } catch {
        // ignore
      }

      // 上传 · backend file-service /api/v1/files/upload-image
      wx.showLoading({ title: '上传中...', mask: true });
      const upload = await this.uploadImage(file.tempFilePath);
      wx.hideLoading();

      // 跳转 analyzing
      wx.navigateTo({
        url: `/pages/camera/analyzing/analyzing?taskId=${upload.task_id}&subject=${this.data.subject}`,
      });
    } catch (e: unknown) {
      wx.hideLoading();
      const msg = (e as { errMsg?: string }).errMsg || String(e);
      if (!/cancel/.test(msg)) Toast({ message: '拍照失败：' + msg, duration: 2000 });
    }
  },

  uploadImage(tempPath: string): Promise<UploadResp> {
    return new Promise((resolve, reject) => {
      const token = wx.getStorageSync('access_token') || '';
      wx.uploadFile({
        url: 'https://api.longfeng.local/api/v1/files/upload-image',
        filePath: tempPath,
        name: 'file',
        formData: { subject: this.data.subject },
        header: token ? { Authorization: `Bearer ${token}` } : {},
        success: (res) => {
          try {
            const data = JSON.parse(res.data) as UploadResp;
            resolve(data);
          } catch (e) {
            reject(e);
          }
        },
        fail: reject,
      });
    });
  },
});

// 让 TS 满足 api import side-effect (实际未直接调 api · 上传走 wx.uploadFile)
void api;
