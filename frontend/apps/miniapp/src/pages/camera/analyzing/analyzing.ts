// P03 analyzing · Mood A · 接 WebSocket /ws/analyze/{taskId} · 替代 H5 SSE
// 与 H5 useEventSource 同语义 · D-WS · 落地计划 §S7
import { TEST_IDS } from '../../../utils/testids';
import { t } from '../../../utils/i18n';
import { AnalyzeWS } from '../../../utils/ws';

type Stage = 'upload' | 'ocr' | 'analyze' | 'save';

Page({
  data: {
    tids: TEST_IDS,
    i: {} as Record<string, string>,
    progress: 0,
    stage: 'upload' as Stage,
    passed: { upload: false, ocr: false, analyze: false, save: false },
    partialText: '',
    fallback: false,
    taskId: '',
    subject: 'math',
  },

  _ws: null as AnalyzeWS | null,

  onLoad(query: Record<string, string>) {
    const taskId = query.taskId || '';
    const subject = query.subject || 'math';
    this.setData({
      taskId,
      subject,
      i: {
        title: t('analyzing.title'),
        stage_upload: t('analyzing.stage_upload'),
        stage_ocr: t('analyzing.stage_ocr'),
        stage_analyze: t('analyzing.stage_analyze'),
        stage_save: t('analyzing.stage_save'),
        cancel: t('analyzing.cancel'),
        fallback: t('analyzing.fallback'),
      },
    });
    if (!taskId) {
      wx.showToast({ title: '缺少 taskId', icon: 'none' });
      return;
    }
    this.connect();
  },

  onUnload() {
    this._ws?.cancel();
    this._ws = null;
  },

  connect() {
    this._ws = new AnalyzeWS(this.data.taskId, {
      onStage: (stage, progress) => {
        const passed = { ...this.data.passed };
        if (stage === 'ocr') passed.upload = true;
        if (stage === 'analyze') {
          passed.upload = true;
          passed.ocr = true;
        }
        if (stage === 'save') {
          passed.upload = true;
          passed.ocr = true;
          passed.analyze = true;
        }
        this.setData({ stage: stage as Stage, progress, passed });
      },
      onPartial: (text) => {
        // 累积部分讲解
        this.setData({ partialText: (this.data.partialText + text).slice(-200) });
      },
      onFinal: (data) => {
        const passed = { upload: true, ocr: true, analyze: true, save: true };
        this.setData({ progress: 100, passed });
        // 缓存最终结果 · result 页读取
        try {
          wx.setStorageSync('analyze_result_' + this.data.taskId, data);
        } catch {
          // ignore
        }
        setTimeout(() => {
          wx.redirectTo({
            url: `/pages/camera/result/result?taskId=${this.data.taskId}&subject=${this.data.subject}`,
          });
        }, 600);
      },
      onError: (msg) => {
        wx.showModal({
          title: '分析失败',
          content: msg,
          showCancel: true,
          cancelText: '返回',
          confirmText: '重试',
          success: (r) => {
            if (r.confirm) this.connect();
            else wx.navigateBack();
          },
        });
      },
      onFallback: () => {
        this.setData({ fallback: true });
      },
      onClose: () => {
        // 服务端关闭 · 已在 onFinal/onError 处理跳转
      },
    });
    this._ws.connect();
  },

  onCancel() {
    wx.showModal({
      title: '取消分析',
      content: '确认取消本次 AI 分析吗？',
      success: (r) => {
        if (r.confirm) {
          this._ws?.cancel();
          wx.navigateBack();
        }
      },
    });
  },
});
