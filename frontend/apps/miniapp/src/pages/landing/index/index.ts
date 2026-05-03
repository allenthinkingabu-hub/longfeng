// P-LANDING · 小程序首页 · Mood A · STYLE-TRUTH §3
import { TEST_IDS } from '../../../utils/testids';
import { t } from '../../../utils/i18n';

interface SampleVO {
  id: string;
  subject: 'math' | 'physics' | 'english';
  subjectLabel: string;
  formula: string;
  err: string;
  kp: string;
}

Page({
  data: {
    tids: TEST_IDS,
    i: {} as Record<string, string>,
    samples: [] as SampleVO[],
  },

  onLoad() {
    this.setData({
      i: {
        eyebrow: t('landing.eyebrow'),
        cta_login: t('landing.cta_login'),
        cta_try: t('landing.cta_try'),
        hero_sub: t('landing.hero_sub'),
        metric_users: t('landing.metric_users'),
        metric_users_l: t('landing.metric_users_l'),
        metric_acc: t('landing.metric_acc'),
        metric_acc_l: t('landing.metric_acc_l'),
        metric_save: t('landing.metric_save'),
        metric_save_l: t('landing.metric_save_l'),
        sample_title: t('landing.sample_title'),
        feature_title: t('landing.feature_title'),
        feature_a_t: t('landing.feature_a_t'),
        feature_a_d: t('landing.feature_a_d'),
        feature_b_t: t('landing.feature_b_t'),
        feature_b_d: t('landing.feature_b_d'),
        feature_c_t: t('landing.feature_c_t'),
        feature_c_d: t('landing.feature_c_d'),
        how_title: t('landing.how_title'),
        how_a: t('landing.how_a'),
        how_b: t('landing.how_b'),
        how_c: t('landing.how_c'),
      },
      samples: [
        {
          id: 's1',
          subject: 'math',
          subjectLabel: t('capture.subj_math'),
          formula: 'x² + 2x − 8 = 0',
          err: 'WRONG · 因式分解',
          kp: '一元二次方程根式法',
        },
        {
          id: 's2',
          subject: 'physics',
          subjectLabel: t('capture.subj_physics'),
          formula: 'F = ma · 摩擦',
          err: 'WRONG · 受力分析',
          kp: '斜面 · 摩擦力方向',
        },
        {
          id: 's3',
          subject: 'english',
          subjectLabel: t('capture.subj_english'),
          formula: 'Past Perfect',
          err: 'WRONG · 时态',
          kp: '过去完成 vs 一般过去',
        },
      ],
    });
  },

  onTryTap() {
    wx.navigateTo({ url: '/pages/camera/capture/capture' });
  },

  onLoginTap() {
    wx.showToast({ title: '请使用微信登录', icon: 'none' });
    // TODO · backend auth-service /api/v1/auth/wechat 接入
  },
});
