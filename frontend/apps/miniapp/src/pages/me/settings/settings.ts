// P13 settings · Mood B · STYLE-TRUTH §3
// 含 SC-16 VIP 入口分流 (Free/VIP/VIP+)
import { api } from '../../../utils/api';
import { TEST_IDS } from '../../../utils/testids';
import { t } from '../../../utils/i18n';

interface ProfileResp {
  name: string;
  initial: string;
  school: string;
  grade: string;
  tier: 'normal' | 'vip' | 'vip_plus';
  ai_model_pref?: string;
  notify_on?: boolean;
}

const TIER_LABEL_MAP: Record<string, () => string> = {
  normal: () => t('settings.tier_normal'),
  vip: () => t('settings.tier_vip'),
  vip_plus: () => t('settings.tier_vip_plus'),
};

const MODEL_LABEL: Record<string, () => string> = {
  auto: () => t('ai_model_pref.auto'),
  openai: () => t('ai_model_pref.openai'),
  claude: () => t('ai_model_pref.claude'),
  gemini: () => t('ai_model_pref.gemini'),
  qwen: () => t('ai_model_pref.qwen'),
};

Page({
  data: {
    tids: TEST_IDS,
    i: {} as Record<string, string>,
    profile: { name: '同学', initial: '同', school: '—', grade: '—', tier: 'normal' as const } as ProfileResp,
    tierLabel: '',
    langLabel: '中文',
    aiModelLabel: '自动',
    notifyOn: false,
  },

  onLoad() {
    this.setData({
      i: {
        lang: t('settings.lang'),
        ai_model: t('settings.ai_model'),
        notifications: t('settings.notifications'),
        privacy: t('settings.privacy'),
        about: t('settings.about'),
        logout: t('settings.logout'),
      },
    });
    this.fetch();
  },

  onShow() {
    this.fetch();
  },

  async fetch() {
    let profile: ProfileResp = this.data.profile;
    try {
      profile = await api.get<ProfileResp>('/me/profile');
    } catch {
      // demo
      profile = { name: '同学 A', initial: 'A', school: '示范中学', grade: '初三', tier: 'normal', ai_model_pref: 'auto', notify_on: true };
    }
    const lang = (wx.getStorageSync('lang') as string) || 'zh-CN';
    this.setData({
      profile,
      tierLabel: (TIER_LABEL_MAP[profile.tier] || TIER_LABEL_MAP.normal)(),
      langLabel: lang === 'en-US' ? 'English' : '中文',
      aiModelLabel: (MODEL_LABEL[profile.ai_model_pref || 'auto'] || MODEL_LABEL.auto)(),
      notifyOn: !!profile.notify_on,
    });
  },

  onLang() {
    const cur = wx.getStorageSync('lang') || 'zh-CN';
    const next = cur === 'en-US' ? 'zh-CN' : 'en-US';
    wx.setStorageSync('lang', next);
    wx.showToast({ title: '已切换 · 重启生效', icon: 'success' });
    this.setData({ langLabel: next === 'en-US' ? 'English' : '中文' });
  },

  onAiModel() {
    wx.navigateTo({ url: '/pages/me/ai-model-pref/ai-model-pref' });
  },

  onNotifications() {
    wx.openSetting();
  },

  onPrivacy() {
    wx.showToast({ title: '隐私设置 · 待接入', icon: 'none' });
  },

  onAbout() {
    wx.showModal({
      title: 'AI 错题本',
      content: 'v0.1.0 · MVP\n© 2026 longfeng',
      showCancel: false,
    });
  },

  onLogout() {
    wx.showModal({
      title: '确认退出？',
      content: '退出后将清除本地登录态',
      success: (r) => {
        if (r.confirm) {
          wx.removeStorageSync('access_token');
          wx.reLaunch({ url: '/pages/landing/index/index' });
        }
      },
    });
  },
});
