// P · AI Model Pref · Mood B · STYLE-TRUTH §3
// SC-16 三层防护：NORMAL 显示 hint(锁定) · VIP 显示选择器 · VIP+ 显示实验池
// SC-16 §16.8 防 tier 信号泄露：NORMAL 静默忽略 hint，不报 403
import { api } from '../../../utils/api';
import { TEST_IDS } from '../../../utils/testids';
import { t } from '../../../utils/i18n';

interface ProfileResp {
  tier: 'normal' | 'vip' | 'vip_plus';
  ai_model_pref?: string;
}

interface ModelDef {
  key: string;
  label: string;
  desc?: string;
  iconChar: string;
  vipPlusOnly?: boolean;
}

Page({
  data: {
    tids: TEST_IDS,
    i: {} as Record<string, string>,
    tier: 'normal' as 'normal' | 'vip' | 'vip_plus',
    tierLabel: '免费版',
    tierHint: '',
    selected: 'auto',
    disabled: true,
    models: [] as ModelDef[],
  },

  onLoad() {
    this.setData({
      i: {
        save: t('ai_model_pref.save'),
      },
    });
    this.fetch();
  },

  async fetch() {
    let profile: ProfileResp = { tier: 'normal', ai_model_pref: 'auto' };
    try {
      profile = await api.get<ProfileResp>('/me/profile');
    } catch {
      // demo
    }
    const disabled = profile.tier === 'normal';
    const models: ModelDef[] = [
      { key: 'auto', label: t('ai_model_pref.auto'), desc: t('ai_model_pref.auto_desc'), iconChar: 'A' },
      { key: 'openai', label: t('ai_model_pref.openai'), iconChar: 'O' },
      { key: 'claude', label: t('ai_model_pref.claude'), iconChar: 'C' },
      { key: 'gemini', label: t('ai_model_pref.gemini'), iconChar: 'G' },
      { key: 'qwen', label: t('ai_model_pref.qwen'), iconChar: 'Q', vipPlusOnly: true },
    ];
    let tierHint = '';
    if (profile.tier === 'normal') tierHint = t('ai_model_pref.tier_hint_normal');
    else if (profile.tier === 'vip') tierHint = t('ai_model_pref.tier_hint_vip');
    else tierHint = t('ai_model_pref.tier_hint_vip_plus');

    const tierLabel = profile.tier === 'normal'
      ? t('settings.tier_normal')
      : profile.tier === 'vip'
        ? t('settings.tier_vip')
        : t('settings.tier_vip_plus');

    this.setData({
      tier: profile.tier,
      tierLabel,
      tierHint,
      selected: profile.ai_model_pref || 'auto',
      disabled,
      models: models.filter((m) => !m.vipPlusOnly || profile.tier === 'vip_plus'),
    });
  },

  onPick(e: WechatMiniprogram.TouchEvent) {
    if (this.data.disabled) {
      wx.showToast({ title: '升级 VIP 即可自选', icon: 'none' });
      return;
    }
    const key = e.currentTarget.dataset.key as string;
    this.setData({ selected: key });
  },

  async onSave() {
    if (this.data.disabled) {
      wx.showToast({ title: '升级 VIP 才能保存', icon: 'none' });
      return;
    }
    try {
      await api.patch('/me/ai-model-pref', { ai_model_hint: this.data.selected });
      wx.showToast({ title: '已保存', icon: 'success' });
      setTimeout(() => wx.navigateBack(), 600);
    } catch {
      // SC-16 §16.8: NORMAL 用户传 hint 后端静默忽略 · 不报 403 (前端不暴露 tier 区别)
      wx.showToast({ title: '保存失败', icon: 'none' });
    }
  },
});
