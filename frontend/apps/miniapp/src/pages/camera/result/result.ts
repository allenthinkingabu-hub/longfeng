// P04 result · Mood B · STYLE-TRUTH §3
// 读取 analyze_result_${taskId} · 用户确认后 POST /api/v1/wrong-items
import { TEST_IDS } from '../../../utils/testids';
import { t } from '../../../utils/i18n';
import { api } from '../../../utils/api';
import Toast from '@vant/weapp/toast/toast';

interface AnalyzeResult {
  userAnswer?: string;
  correctAnswer?: string;
  userNote?: string;
  correctNote?: string;
  reason?: string;
  steps?: { n: number; exp: string; fm?: string }[];
  kpId?: string;
}

Page({
  data: {
    tids: TEST_IDS,
    i: {} as Record<string, string>,
    taskId: '',
    subject: 'math',
    result: {} as AnalyzeResult,
    steps: [
      { n: 1, exp: '原方程化简为 x² = 4' },
      { n: 2, exp: '开方时取根 ±2', fm: 'x = ±2' },
      { n: 3, exp: '代回原式验证两根均成立', fm: 'x = −2, x = 2' },
    ] as { n: number; exp: string; fm?: string }[],
  },

  onLoad(query: Record<string, string>) {
    const taskId = query.taskId || '';
    const subject = query.subject || 'math';
    let result: AnalyzeResult = {};
    if (taskId) {
      try {
        result = (wx.getStorageSync('analyze_result_' + taskId) as AnalyzeResult) || {};
      } catch {
        // ignore
      }
    }
    this.setData({
      taskId,
      subject,
      result,
      steps: result.steps && result.steps.length ? result.steps : this.data.steps,
      i: {
        title: t('result.title'),
        ans_wrong: t('result.ans_wrong'),
        ans_right: t('result.ans_right'),
        reason_title: t('result.reason_title'),
        steps_title: t('result.steps_title'),
        kp_title: t('result.kp_title'),
        ebbing_title: t('result.ebbing_title'),
        ebbing_first: t('result.ebbing_first'),
        cta_save: t('result.cta_save'),
        cta_retake: t('result.cta_retake'),
      },
    });
  },

  async onSave() {
    try {
      wx.showLoading({ title: '保存中', mask: true });
      await api.post('/wrong-items', {
        task_id: this.data.taskId,
        subject: this.data.subject,
      });
      wx.hideLoading();
      Toast({ message: '已收入错题本', duration: 1200 });
      // 清缓存
      try {
        wx.removeStorageSync('analyze_result_' + this.data.taskId);
        wx.removeStorageSync('capture_draft');
      } catch {
        // ignore
      }
      setTimeout(() => {
        wx.switchTab({ url: '/pages/wrongbook/list/list' });
      }, 800);
    } catch (e) {
      wx.hideLoading();
      Toast({ message: '保存失败：' + String(e), duration: 2000 });
    }
  },

  onRetake() {
    wx.redirectTo({ url: '/pages/camera/capture/capture' });
  },
});
