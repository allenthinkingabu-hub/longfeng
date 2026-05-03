/**
 * P13 · 设置/我的 · SettingsPage (MePage)
 * Mood B (pure-warm) · 米白底 + 白卡 + iOS 分组列表
 * archive ref: _archive/13_settings.html
 * spec: design/system/pages/P13-settings.spec.md
 * AC 覆盖: AC-P13-001 ~ AC-P13-010
 *
 * SC-16 · VIP AI 模型选择三层分流：
 *   NORMAL   → 不暴露选择器 (防 tier 信号泄露) · 显示升级 hint
 *   VIP      → 显示模型选择器 (catalog 白名单)
 *   VIP_PLUS → 显示实验池 + cost/latency 元信息
 */
import React, { useState, useCallback, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import s from './Settings.module.css';

/* ── Types (spec §4 + D-AI-Model-Catalog) ── */
type UserTier = 'NORMAL' | 'VIP' | 'VIP_PLUS';

interface UserProfile {
  id: string;
  nickname: string;
  grade: string;
  phoneBound: boolean;
  streakDays: number;
  tier: UserTier;
  email: string;
}

interface AiModelCatalog {
  id: string;
  provider: string;
  displayName: string;
  vipOnly: boolean;
  supportedSubjects: string[];
  cost_tier: 'L' | 'M' | 'H';
  avg_latency_ms: number;
  notes: string;
}

interface Preferences {
  quietHours: { start: string; end: string };
  forgotResetStrategy: 'reset-T0' | 'keep-current';
  defaultSubject: string | null;
  push: {
    reviewReminder: boolean;
    weeklyReport: boolean;
    frequency: 'daily' | 'weekly';
  };
  preferredAiModel?: string | null;
}

/* ── Mock data ── */
const MOCK_USER: UserProfile = {
  id: 'u-001',
  nickname: 'Allen Wang',
  grade: '高三',
  phoneBound: true,
  streakDays: 12,
  tier: 'NORMAL',        // Default NORMAL; overridden by /api/v1/me/tier at runtime
  email: 'allenthinking.abu@gmail.com',
};

const MOCK_PREFS: Preferences = {
  quietHours: { start: '23:00', end: '07:30' },
  forgotResetStrategy: 'reset-T0',
  defaultSubject: 'math',
  push: { reviewReminder: true, weeklyReport: true, frequency: 'daily' },
  preferredAiModel: 'qwen-vl-max',
};

// SC-16: model catalog (tier 过滤前的完整列表 · 从 GET /api/ai/models 返回的已过滤版)
// 注：后端按 tier 过滤 · 前端直接消费，不需要前端再次过滤
const VIP_MODEL_CATALOG: AiModelCatalog[] = [
  {
    id: 'qwen-vl-max',
    provider: 'Alibaba Cloud',
    displayName: 'Qwen-VL Max',
    vipOnly: false,
    supportedSubjects: ['math', 'physics', 'chemistry', 'chinese'],
    cost_tier: 'M',
    avg_latency_ms: 3200,
    notes: '中文学科准确率更高 · VIP',
  },
  {
    id: 'openai-gpt-4o',
    provider: 'OpenAI',
    displayName: 'OpenAI GPT-4o',
    vipOnly: false,
    supportedSubjects: ['english', 'math'],
    cost_tier: 'M',
    avg_latency_ms: 2100,
    notes: '英语/海外学生首选 · VIP',
  },
  {
    id: 'qwen-vl-plus',
    provider: 'Alibaba Cloud',
    displayName: 'Qwen-VL Plus',
    vipOnly: true,
    supportedSubjects: ['math', 'physics', 'chemistry', 'chinese', 'english'],
    cost_tier: 'H',
    avg_latency_ms: 4800,
    notes: '全学科最高精度 · VIP 专属',
  },
];

const VIP_PLUS_EXTRA_CATALOG: AiModelCatalog[] = [
  {
    id: 'claude-3-7-sonnet-experimental',
    provider: 'Anthropic',
    displayName: 'Claude 3.7 Sonnet（实验池）',
    vipOnly: true,
    supportedSubjects: ['math', 'physics', 'english'],
    cost_tier: 'H',
    avg_latency_ms: 5200,
    notes: '复杂推理实验版 · VIP_PLUS 专属',
  },
  {
    id: 'private-model',
    provider: 'Private',
    displayName: '私有化部署模型',
    vipOnly: true,
    supportedSubjects: ['math'],
    cost_tier: 'H',
    avg_latency_ms: 1500,
    notes: '私有化部署 · 内测 · VIP_PLUS',
  },
];

/* ── StatusBar icons ── */
const StatusIcons = () => (
  <div className={s.statusRight}>
    <svg width="17" height="11" viewBox="0 0 17 11" aria-hidden="true">
      <g fill="#111">
        <rect x="0" y="7" width="3" height="4" rx=".5"/>
        <rect x="4.5" y="5" width="3" height="6" rx=".5"/>
        <rect x="9" y="3" width="3" height="8" rx=".5"/>
        <rect x="13.5" y="1" width="3" height="10" rx=".5"/>
      </g>
    </svg>
    <svg width="16" height="11" viewBox="0 0 16 11" fill="none" aria-hidden="true">
      <path d="M8 3c2 0 3.8.7 5.2 1.9l1.4-1.4C12.8 1.9 10.5 1 8 1S3.2 1.9 1.4 3.5l1.4 1.4C4.2 3.7 6 3 8 3Z" fill="#111"/>
      <path d="M8 6c1.2 0 2.3.4 3.2 1.1l1.4-1.4C11.3 4.6 9.7 4 8 4s-3.3.6-4.6 1.7l1.4 1.4C5.7 6.4 6.8 6 8 6Z" fill="#111"/>
      <circle cx="8" cy="9" r="1.4" fill="#111"/>
    </svg>
    <svg width="26" height="12" viewBox="0 0 26 12" aria-hidden="true">
      <rect x=".5" y=".5" width="22" height="11" rx="3" fill="none" stroke="#111" opacity=".45"/>
      <rect x="2" y="2" width="17" height="8" rx="1.6" fill="#111"/>
      <rect x="23" y="4" width="2" height="4" rx="1" fill="#111" opacity=".45"/>
    </svg>
  </div>
);

const ChevronRight = () => (
  <svg className={s.itemChev} width="8" height="13" viewBox="0 0 8 13" fill="none" aria-hidden="true">
    <path d="M1 1l6 5.5L1 12" stroke="#C7C7CC" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
  </svg>
);

/* ── SC-16 · CostTier chip helper ── */
function costChipClass(tier: 'L' | 'M' | 'H'): string {
  if (tier === 'H') return s.aiModelMetaChipHigh;
  if (tier === 'L') return s.aiModelMetaChipLow;
  return s.aiModelMetaChipMid;
}

/* ──────────────────────────────────────────── */
/* SC-16 · AI 模型选择子区                       */
/* ──────────────────────────────────────────── */
const AiModelSection: React.FC<{
  tier: UserTier;
  selectedModel: string | null | undefined;
  onSelect: (modelId: string) => void;
}> = ({ tier, selectedModel, onSelect }) => {
  // SC-16 round 20 · 本地兜底：click 后立即写本地 state · 防 prefs 异步更新滞后
  // 当 prop 改变时同步更新本地 state（保 prop 优先）
  const [localSelected, setLocalSelected] = React.useState<string | null | undefined>(selectedModel);
  React.useEffect(() => { setLocalSelected(selectedModel); }, [selectedModel]);
  const effectiveSelected = localSelected ?? selectedModel;
  const handleItemClick = (modelId: string) => {
    setLocalSelected(modelId);
    onSelect(modelId);
  };
  // NORMAL 用户: 不暴露选择器 UI (防 tier 信号泄露 · TDD §16.8)
  if (tier === 'NORMAL') {
    return (
      <div
        className={s.aiSection}
        data-testid="p13-sc16-ai-section"
        data-sc16-tier="NORMAL"
      >
        <div className={s.groupLabel}>AI 模型</div>
        <div className={s.list}>
          <div
            className={s.aiUpgradeHint}
            data-testid="p13-sc16-upgrade-hint"
            role="region"
            aria-label="AI模型提示"
          >
            <div className={s.aiUpgradeIco} aria-hidden="true">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <path d="M12 2l3.09 6.26L22 9.27l-5 4.87L18.18 22 12 18.77 5.82 22l1.18-7.86L2 9.27l6.91-1.01L12 2z" stroke="#fff" strokeWidth="1.8" fill="rgba(255,255,255,.25)" strokeLinejoin="round"/>
              </svg>
            </div>
            <div className={s.aiUpgradeContent}>
              <div className={s.aiUpgradeTitle}>AI 模型选择</div>
              <div className={s.aiUpgradeBody}>
                升级 VIP 解锁模型选择，让 AI 更了解你
              </div>
            </div>
            <div className={s.aiUpgradeChev} aria-hidden="true">
              <svg width="8" height="13" viewBox="0 0 8 13" fill="none">
                <path d="M1 1l6 5.5L1 12" stroke="#C7C7CC" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
            </div>
          </div>
        </div>
      </div>
    );
  }

  // VIP + VIP_PLUS: 显示模型选择器
  const catalog = tier === 'VIP_PLUS'
    ? [...VIP_MODEL_CATALOG, ...VIP_PLUS_EXTRA_CATALOG]
    : VIP_MODEL_CATALOG;

  return (
    <div
      className={s.aiSection}
      data-testid="p13-sc16-ai-section"
      data-sc16-tier={tier}
    >
      <div className={s.groupLabel}>AI 模型选择</div>
      <div className={s.aiSelectorCard} data-testid="p13-sc16-model-selector">
        <div className={s.aiSelectorHeader}>
          <div className={s.ico} style={{ background: 'linear-gradient(135deg, #C581F7, #8B87F6)' }}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M12 2l3.09 6.26L22 9.27l-5 4.87L18.18 22 12 18.77 5.82 22l1.18-7.86L2 9.27l6.91-1.01L12 2z" stroke="#fff" strokeWidth="1.6" fill="rgba(255,255,255,.2)" strokeLinejoin="round"/>
            </svg>
          </div>
          <div className={s.aiSelectorTitle}>首选模型</div>
          <div className={s.aiSelectorCurrent}>
            {catalog.find((m) => m.id === effectiveSelected)?.displayName ?? '系统默认'}
          </div>
        </div>

        {catalog.map((model) => {
          const isSelected = effectiveSelected === model.id;
          return (
            <div
              key={model.id}
              className={`${s.aiModelItem} ${isSelected ? s.aiModelItemSelected : ''}`}
              data-testid={`p13-sc16-model-item-${model.id}`}
              role="radio"
              aria-checked={isSelected}
              tabIndex={0}
              onClick={() => handleItemClick(model.id)}
              onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') handleItemClick(model.id); }}
            >
              <div className={`${s.aiModelDot} ${isSelected ? s.aiModelDotSelected : ''}`} />
              <div className={s.aiModelContent}>
                <div className={s.aiModelName}>
                  {model.displayName}
                  {model.vipOnly && (
                    <span className={s.vipBadge}>VIP</span>
                  )}
                </div>
                <div className={s.aiModelDesc}>{model.notes}</div>
                {/* VIP_PLUS only: cost/latency meta */}
                {tier === 'VIP_PLUS' && (
                  <div className={s.aiModelMeta}>
                    <span
                      className={`${s.aiModelMetaChip} ${costChipClass(model.cost_tier)}`}
                      data-testid={`p13-sc16-model-${model.id}-cost`}
                    >
                      成本 {model.cost_tier}
                    </span>
                    <span
                      className={s.aiModelMetaChip}
                      data-testid={`p13-sc16-model-${model.id}-latency`}
                    >
                      延迟 {model.avg_latency_ms >= 1000
                        ? `${(model.avg_latency_ms / 1000).toFixed(1)}s`
                        : `${model.avg_latency_ms}ms`}
                    </span>
                  </div>
                )}
              </div>
            </div>
          );
        })}

        <div className={s.aiHintNote}>
          提示：单次拍题时也可临时选择其他模型（aiModelHint）。优先级：单次选择 &gt; 此处设置 &gt; 系统默认。
        </div>
      </div>
    </div>
  );
};

/* ──────────────────────────────────────────── */
/* 主页面                                        */
/* ──────────────────────────────────────────── */
export const SettingsPage: React.FC = () => {
  const nav = useNavigate();

  const [user, setUser] = useState<UserProfile>(MOCK_USER);
  const [prefs, setPrefs] = useState<Preferences>(MOCK_PREFS);

  /* SC-16 · 从 /api/v1/me/tier + /api/v1/ai-models 读 tier 和当前选中模型（B 轨 MSW 拦截）
   *
   * MSW handler tier 判定基于 Authorization header（包含 'vipplus' / 'vip' 字串）。
   * loginAs(NORMAL/VIP/VIP_PLUS) fixture 写 lf:token JWT · 这里取出附到 Authorization。
   * 兜底：缺 token → 仍发请求（headerless · handler 默认 NORMAL）。
   * 加速：localStorage `lf_user_tier` 同步存在 · 直接 prime 到 state，避免 fetch 抖动。
   */
  useEffect(() => {
    let token: string | null = null;
    let tierHint: UserTier | null = null;
    try {
      token = localStorage.getItem('lf:token');
      const tt = localStorage.getItem('lf_user_tier');
      if (tt === 'NORMAL' || tt === 'VIP' || tt === 'VIP_PLUS') tierHint = tt;
    } catch { /* private mode */ }
    if (tierHint) {
      setUser((prev) => ({ ...prev, tier: tierHint! }));
    }
    const headers: Record<string, string> = { 'Cache-Control': 'no-store' };
    if (token) headers['Authorization'] = `Bearer ${token}`;

    void fetch('/api/v1/me/tier', { headers })
      .then((res) => (res.ok ? (res.json() as Promise<{ tier: UserTier }>) : null))
      .then((data) => {
        if (data?.tier) {
          setUser((prev) => ({ ...prev, tier: data.tier }));
        }
      })
      .catch(() => { /* silently fallback to NORMAL */ });

    void fetch('/api/v1/ai-models', { headers })
      .then((res) => (res.ok ? (res.json() as Promise<{ currentModel?: string }>) : null))
      .then((data) => {
        if (data?.currentModel) {
          setPrefs((prev) => ({ ...prev, preferredAiModel: data.currentModel ?? null }));
        }
      })
      .catch(() => { /* silently keep MOCK_PREFS.preferredAiModel */ });
  }, []);

  const [dangerConfirmOpen, setDangerConfirmOpen] = useState(false);
  const [dangerAction, setDangerAction] = useState<'account-deletion' | 'clear-data' | null>(null);
  const [confirmInput, setConfirmInput] = useState('');

  const handleToggle = useCallback((field: keyof Preferences['push']) => {
    setPrefs((prev) => ({
      ...prev,
      push: { ...prev.push, [field]: !prev.push[field as keyof typeof prev.push] },
    }));
    // In real implementation: debounced PATCH /api/me/preferences
  }, []);

  const handleSelectAiModel = useCallback((modelId: string) => {
    setPrefs((prev) => ({ ...prev, preferredAiModel: modelId }));
    // SC-16 · POST /api/v1/me/ai-model 持久化（VIP/VIP_PLUS 专属 · NORMAL 静默忽略）
    void fetch('/api/v1/me/ai-model', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ modelId }),
    }).catch(() => { /* ignore network error */ });
  }, []);

  const openDanger = (action: 'account-deletion' | 'clear-data') => {
    setDangerAction(action);
    setDangerConfirmOpen(true);
    setConfirmInput('');
  };

  const confirmDanger = () => {
    if (confirmInput !== '注销') return;
    // POST /api/me/danger/account-deletion or /clear-data
    setDangerConfirmOpen(false);
  };

  return (
    <main
      className={s.page}
      role="main"
      data-testid="p13-root"
      data-mood="B"
    >
      {/* ── StatusBar ── */}
      <div className={s.statusbar} role="presentation">
        <span>9:41</span>
        <StatusIcons />
      </div>

      {/* ── Nav ── */}
      <nav className={s.nav} role="navigation" aria-label="设置页导航">
        <div className={s.navRow}>
          <button
            className={s.navBack}
            type="button"
            onClick={() => nav(-1 as unknown as string)}
            aria-label="返回"
          >
            <svg viewBox="0 0 12 20" width="12" height="20" fill="none" aria-hidden="true">
              <path d="M10 2 2 10l8 8" stroke="#007AFF" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
            首页
          </button>
          <button className={s.navEdit} type="button">完成</button>
        </div>
        <h1 className={s.navTitle}>设置</h1>
      </nav>

      {/* ── Content ── */}
      <div className={s.content}>

        {/* ── B1 AvatarBlock (AC-P13-001) ── */}
        <header role="banner">
          <div
            className={s.profile}
            data-testid="p13-avatar-block"
            role="button"
            tabIndex={0}
            aria-label="编辑个人资料"
          >
            <div className={s.avatar} aria-label={`${user.nickname} 的头像`}>
              AW
            </div>
            <div className={s.profileMeta}>
              <div className={s.profileName} data-testid="p13-avatar-block-name">
                {user.nickname}
              </div>
              <div className={s.profileSub}>
                {user.email} · {user.tier === 'NORMAL' ? '普通版' : user.tier === 'VIP' ? 'Pro 版' : 'Pro+ 版'}
              </div>
              <div className={s.profileMetaRow}>
                <span className={s.chipTone}>
                  <span className={s.dotTone} style={{ background: '#34C759' }} />
                  已同步 · 刚刚
                </span>
                <span className={s.chipTone}>
                  <span className={s.dotTone} style={{ background: '#007AFF' }} />
                  3 端在线
                </span>
              </div>
            </div>
            <div className={s.chevron}>
              <ChevronRight />
            </div>
          </div>
        </header>

        <main role="main">
          {/* ── B2 账户设置 (AC-P13-002) ── */}
          <section
            role="region"
            aria-labelledby="group-account"
            data-testid="p13-settings-account"
          >
            <div className={s.group}>
              <div id="group-account" className={s.groupLabel}>账户</div>
              <div className={s.list}>
                <div className={s.item}>
                  <div className={`${s.ico} ${s.icoBlue}`}>
                    <svg viewBox="0 0 24 24" width="17" height="17" fill="none" aria-hidden="true">
                      <circle cx="12" cy="8" r="4" stroke="#fff" strokeWidth="1.8"/>
                      <path d="M4 20c0-4 3.6-7 8-7s8 3 8 7" stroke="#fff" strokeWidth="1.8" strokeLinecap="round"/>
                    </svg>
                  </div>
                  <span className={s.itemTitle}>修改昵称 / 头像</span>
                  <ChevronRight />
                </div>
                <div className={s.item}>
                  <div className={`${s.ico} ${s.icoGreen}`}>
                    <svg viewBox="0 0 24 24" width="17" height="17" fill="none" aria-hidden="true">
                      <rect x="5" y="2" width="14" height="20" rx="2" stroke="#fff" strokeWidth="1.8"/>
                      <path d="M10 16h4M12 9v4" stroke="#fff" strokeWidth="1.6" strokeLinecap="round"/>
                    </svg>
                  </div>
                  <span className={s.itemTitle}>绑定手机号</span>
                  <span className={s.itemValue}>{user.phoneBound ? '已绑定' : '未绑定'}</span>
                  <ChevronRight />
                </div>
                <div
                  className={s.item}
                  data-testid="p13-settings-account-logout-row"
                  role="button"
                  tabIndex={0}
                  onClick={() => {
                    if (window.confirm('确认退出登录？')) {
                      nav('/welcome', { replace: true });
                    }
                  }}
                >
                  <div className={`${s.ico} ${s.icoRed}`}>
                    <svg viewBox="0 0 24 24" width="17" height="17" fill="none" aria-hidden="true">
                      <path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4M10 17l5-5-5-5M15 12H3" stroke="#fff" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
                    </svg>
                  </div>
                  <span className={s.itemTitle} style={{ color: '#FF3B30' }}>退出登录</span>
                </div>
              </div>
            </div>
          </section>

          {/* ── B3 复习偏好 (AC-P13-002/004) ── */}
          <section
            role="region"
            aria-labelledby="group-review"
            data-testid="p13-settings-review"
          >
            <div className={s.group}>
              <div id="group-review" className={s.groupLabel}>复习偏好</div>
              <div className={s.list}>
                <div
                  className={s.item}
                  data-testid="p13-settings-review-quiet-hours-row"
                  role="button"
                  tabIndex={0}
                >
                  <div className={`${s.ico} ${s.icoIndigo}`}>
                    <svg viewBox="0 0 24 24" width="17" height="17" fill="none" aria-hidden="true">
                      <path d="M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9z" stroke="#fff" strokeWidth="1.8"/>
                    </svg>
                  </div>
                  <span className={s.itemTitle}>免打扰时段</span>
                  <span className={s.itemValue}>{prefs.quietHours.start}–{prefs.quietHours.end}</span>
                  <ChevronRight />
                </div>
                <div className={s.item} role="button" tabIndex={0}>
                  <div className={`${s.ico} ${s.icoOrange}`}>
                    <svg viewBox="0 0 24 24" width="17" height="17" fill="none" aria-hidden="true">
                      <path d="M4 6h16M4 12h10M4 18h16" stroke="#fff" strokeWidth="1.8" strokeLinecap="round"/>
                    </svg>
                  </div>
                  <span className={s.itemTitle}>节点重置策略</span>
                  <span className={s.itemValue}>
                    {prefs.forgotResetStrategy === 'reset-T0' ? '遗忘重置 T0' : '保持当前'}
                  </span>
                  <ChevronRight />
                </div>
                <div className={s.item} role="button" tabIndex={0}>
                  <div className={`${s.ico} ${s.icoGreen}`}>
                    <svg viewBox="0 0 24 24" width="17" height="17" fill="none" aria-hidden="true">
                      <path d="M12 2l3.09 6.26L22 9.27l-5 4.87L18.18 22 12 18.77 5.82 22l1.18-7.86L2 9.27l6.91-1.01L12 2z" stroke="#fff" strokeWidth="1.6" fill="rgba(255,255,255,.2)" strokeLinejoin="round"/>
                    </svg>
                  </div>
                  <span className={s.itemTitle}>首选学科</span>
                  <span className={s.itemValue}>{prefs.defaultSubject ? '数学' : '未设置'}</span>
                  <ChevronRight />
                </div>
              </div>
            </div>
          </section>

          {/* ── B4 推送设置 (AC-P13-003/009) ── */}
          <section
            role="region"
            aria-labelledby="group-push"
            data-testid="p13-settings-push"
          >
            <div className={s.group}>
              <div id="group-push" className={s.groupLabel}>推送</div>
              <div className={s.list}>
                <div className={s.item}>
                  <div className={`${s.ico} ${s.icoBlue}`}>
                    <svg viewBox="0 0 24 24" width="17" height="17" fill="none" aria-hidden="true">
                      <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" stroke="#fff" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
                      <path d="M13.7 21a2 2 0 0 1-3.4 0" stroke="#fff" strokeWidth="1.8" strokeLinecap="round"/>
                    </svg>
                  </div>
                  <span className={s.itemTitle}>复习提醒</span>
                  <button
                    className={`${s.switch} ${prefs.push.reviewReminder ? s.switchOn : ''}`}
                    role="switch"
                    aria-checked={prefs.push.reviewReminder}
                    data-testid="p13-settings-push-review-reminder-switch"
                    type="button"
                    onClick={() => handleToggle('reviewReminder')}
                  >
                    <div className={`${s.switchDot} ${prefs.push.reviewReminder ? s.switchOnDot : ''}`} />
                  </button>
                </div>
                <div className={s.item}>
                  <div className={`${s.ico} ${s.icoPink}`}>
                    <svg viewBox="0 0 24 24" width="17" height="17" fill="none" aria-hidden="true">
                      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" stroke="#fff" strokeWidth="1.8" strokeLinejoin="round"/>
                      <path d="M14 2v6h6M16 13H8M16 17H8M10 9H8" stroke="#fff" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
                    </svg>
                  </div>
                  <span className={s.itemTitle}>周报推送</span>
                  <button
                    className={`${s.switch} ${prefs.push.weeklyReport ? s.switchOn : ''}`}
                    role="switch"
                    aria-checked={prefs.push.weeklyReport}
                    type="button"
                    onClick={() => handleToggle('weeklyReport')}
                  >
                    <div className={`${s.switchDot} ${prefs.push.weeklyReport ? s.switchOnDot : ''}`} />
                  </button>
                </div>
                <div className={s.item} role="button" tabIndex={0}>
                  <div className={`${s.ico} ${s.icoOrange}`}>
                    <svg viewBox="0 0 24 24" width="17" height="17" fill="none" aria-hidden="true">
                      <circle cx="12" cy="12" r="9" stroke="#fff" strokeWidth="1.8"/>
                      <path d="M12 7v5l3 3" stroke="#fff" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
                    </svg>
                  </div>
                  <span className={s.itemTitle}>频率</span>
                  <span
                    className={s.itemValue}
                    data-testid="p13-settings-push-frequency-preview"
                  >
                    {prefs.push.frequency === 'daily' ? '每日 · 明日 18:00 提醒' : '每周'}
                  </span>
                  <ChevronRight />
                </div>
              </div>
              <p className={s.note}>关闭复习提醒后，记忆曲线节奏将不受影响，仅停止推送通知。</p>
            </div>
          </section>

          {/* ── SC-16 AI 模型选择子区 ── */}
          <AiModelSection
            tier={user.tier}
            selectedModel={prefs.preferredAiModel}
            onSelect={handleSelectAiModel}
          />

          {/* ── B5 隐私 ── */}
          <section
            role="region"
            aria-labelledby="group-privacy"
            data-testid="p13-settings-privacy"
          >
            <div className={s.group}>
              <div id="group-privacy" className={s.groupLabel}>隐私</div>
              <div className={s.list}>
                <div className={s.item} role="button" tabIndex={0}>
                  <div className={`${s.ico} ${s.icoGray}`}>
                    <svg viewBox="0 0 24 24" width="17" height="17" fill="none" aria-hidden="true">
                      <rect x="5" y="2" width="14" height="20" rx="2" stroke="#fff" strokeWidth="1.8"/>
                      <path d="M9 7h6M9 11h6M9 15h4" stroke="#fff" strokeWidth="1.6" strokeLinecap="round"/>
                    </svg>
                  </div>
                  <span className={s.itemTitle}>设备管理</span>
                  <span className={s.itemValue}>3 台</span>
                  <ChevronRight />
                </div>
                <div className={s.item} role="button" tabIndex={0}>
                  <div className={`${s.ico} ${s.icoBlue}`}>
                    <svg viewBox="0 0 24 24" width="17" height="17" fill="none" aria-hidden="true">
                      <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5" stroke="#fff" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
                    </svg>
                  </div>
                  <span className={s.itemTitle}>数据导出</span>
                  <ChevronRight />
                </div>
                <div className={s.item} role="button" tabIndex={0}>
                  <div className={`${s.ico} ${s.icoPurple}`}>
                    <svg viewBox="0 0 24 24" width="17" height="17" fill="none" aria-hidden="true">
                      <circle cx="12" cy="8" r="4" stroke="#fff" strokeWidth="1.8"/>
                      <path d="M4 20c0-4 3.6-7 8-7" stroke="#fff" strokeWidth="1.8" strokeLinecap="round"/>
                      <path d="M17 15l3 3-3 3M17 15l-3 3 3 3" stroke="#fff" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"/>
                    </svg>
                  </div>
                  <span className={s.itemTitle}>观察者邀请管理</span>
                  <ChevronRight />
                </div>
              </div>
            </div>
          </section>

          {/* ── B6 关于 (AC-P13-005) ── */}
          <section
            role="region"
            aria-labelledby="group-about"
            data-testid="p13-settings-about"
          >
            <div className={s.group}>
              <div id="group-about" className={s.groupLabel}>关于</div>
              <div className={s.list}>
                <div className={s.item}>
                  <div className={`${s.ico} ${s.icoGray}`}>
                    <svg viewBox="0 0 24 24" width="17" height="17" fill="none" aria-hidden="true">
                      <circle cx="12" cy="12" r="9" stroke="#fff" strokeWidth="1.8"/>
                      <path d="M12 8v4M12 16h.01" stroke="#fff" strokeWidth="1.8" strokeLinecap="round"/>
                    </svg>
                  </div>
                  <span className={s.itemTitle}>版本</span>
                  <span
                    className={s.itemValue}
                    data-testid="p13-settings-about-version"
                  >
                    v1.0.0 · 已是最新
                  </span>
                </div>
                <div className={s.item} role="button" tabIndex={0}>
                  <div className={`${s.ico} ${s.icoBlue}`}>
                    <svg viewBox="0 0 24 24" width="17" height="17" fill="none" aria-hidden="true">
                      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" stroke="#fff" strokeWidth="1.8" strokeLinejoin="round"/>
                    </svg>
                  </div>
                  <span className={s.itemTitle}>用户协议</span>
                  <ChevronRight />
                </div>
                <div className={s.item} role="button" tabIndex={0}>
                  <div className={`${s.ico} ${s.icoIndigo}`}>
                    <svg viewBox="0 0 24 24" width="17" height="17" fill="none" aria-hidden="true">
                      <path d="M12 22s-8-4.5-8-11.8A8 8 0 0 1 12 2a8 8 0 0 1 8 8.2c0 7.3-8 11.8-8 11.8z" stroke="#fff" strokeWidth="1.8"/>
                      <path d="M12 11v4M12 8h.01" stroke="#fff" strokeWidth="1.8" strokeLinecap="round"/>
                    </svg>
                  </div>
                  <span className={s.itemTitle}>隐私政策</span>
                  <ChevronRight />
                </div>
                <div className={s.item} role="button" tabIndex={0}>
                  <div className={`${s.ico} ${s.icoOrange}`}>
                    <svg viewBox="0 0 24 24" width="17" height="17" fill="none" aria-hidden="true">
                      <circle cx="12" cy="12" r="9" stroke="#fff" strokeWidth="1.8"/>
                      <path d="M12 8v4M12 16h.01" stroke="#fff" strokeWidth="1.8" strokeLinecap="round"/>
                    </svg>
                  </div>
                  <span className={s.itemTitle}>帮助中心</span>
                  <ChevronRight />
                </div>
              </div>
            </div>
          </section>

          {/* ── B7 DangerZone (AC-P13-006/007) ── */}
          <section
            role="region"
            aria-label="危险操作区 · 慎重选择"
            data-testid="p13-danger-zone"
          >
            <div className={s.dangerZone}>
              <div
                className={s.dangerItem}
                role="button"
                tabIndex={0}
                data-testid="p13-danger-zone-account-deletion-btn"
                aria-label="注销账户"
                aria-describedby="danger-deletion-desc"
                onClick={() => openDanger('account-deletion')}
              >
                <div className={s.dangerIcon}>
                  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" aria-hidden="true">
                    <path d="M3 6h18M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6M10 11v6M14 11v6" stroke="#FF3B30" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
                    <path d="M9 6V4h6v2" stroke="#FF3B30" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
                  </svg>
                </div>
                <div>
                  <div className={s.dangerTitle}>注销账户</div>
                  <div id="danger-deletion-desc" className={s.dangerSubtitle}>72 小时冷静期 · 不可撤销</div>
                </div>
              </div>
              <div
                className={s.dangerItem}
                role="button"
                tabIndex={0}
                onClick={() => openDanger('clear-data')}
              >
                <div className={s.dangerIcon}>
                  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" aria-hidden="true">
                    <path d="M2 8h20M4 8l2 12h12L20 8" stroke="#FF3B30" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
                    <path d="M9 8V5a3 3 0 0 1 6 0v3" stroke="#FF3B30" strokeWidth="1.8" strokeLinecap="round"/>
                  </svg>
                </div>
                <div>
                  <div className={s.dangerTitle}>清除所有数据</div>
                  <div className={s.dangerSubtitle}>错题 · 记忆曲线 · 日历 · 不可恢复</div>
                </div>
              </div>
            </div>
          </section>

          <div className={s.version}>
            AI 错题本 · v1.0.0 · Build 20260502
          </div>
        </main>
      </div>

      {/* ── Danger Confirm Modal (AC-P13-007) ── */}
      {dangerConfirmOpen && (
        <div
          className={s.modalOverlay}
          role="dialog"
          aria-modal="true"
          aria-label="确认危险操作"
          data-testid="p13-danger-confirm"
        >
          <div className={s.modalSheet}>
            <h2 className={s.modalTitle}>
              {dangerAction === 'account-deletion' ? '注销账户' : '清除所有数据'}
            </h2>
            <p className={s.modalBody}>
              {dangerAction === 'account-deletion'
                ? '此操作不可撤销。账户将在 72 小时后永久删除。请输入"注销"确认：'
                : '所有错题、记忆曲线和日历数据将被永久删除。请输入"注销"确认：'}
            </p>
            <input
              className={s.modalInput}
              type="text"
              value={confirmInput}
              onChange={(e) => setConfirmInput(e.target.value)}
              placeholder='请输入"注销"'
              aria-label='请输入"注销"以确认'
              autoFocus
            />
            <div className={s.modalActions}>
              <button
                className={s.modalCancelBtn}
                type="button"
                onClick={() => setDangerConfirmOpen(false)}
              >
                取消
              </button>
              <button
                className={s.modalConfirmBtn}
                type="button"
                disabled={confirmInput !== '注销'}
                onClick={confirmDanger}
                aria-disabled={confirmInput !== '注销'}
              >
                确认{dangerAction === 'account-deletion' ? '注销' : '清除'}
              </button>
            </div>
          </div>
        </div>
      )}

      <div className={s.homebar} aria-hidden="true" />
    </main>
  );
};

// Export alias to match App.tsx usage
export { SettingsPage as MePage };
