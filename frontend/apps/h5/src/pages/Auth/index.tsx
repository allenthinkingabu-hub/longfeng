/// <reference types="vite/client" />
/**
 * P00 · 登录 · AuthPage
 * Mood A (hero+overlap) · 深蓝 hero 380px + 3 blob + conic logo
 * spec: design/system/pages/P00.spec.md
 * archive ref: STYLE-TRUTH.md §6 (P00 archive 缺失 · 按缺失页指引)
 *
 * 铁律 1 例外: 微信主按钮 #07C160 + data-iron-rule-1-exception="wechat-brand"
 */
import React, { useState, useCallback } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import s from './Auth.module.css';

/* ── Global augmentation for dev/e2e wx code injection ── */
declare global {
  interface Window {
    __lf_dev_wx_code__?: string;
  }
}

/* ── Types (spec §4) ── */
type AuthState = 'IDLE' | 'CONSENT_REQUIRED' | 'LOGGING_IN' | 'CLAIMING' | 'SUCCESS' | 'ERROR';

/* ── Static SVG icons ── */
const WechatIcon = () => (
  <svg className={s.wechatIcon} viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
    <path d="M9.5 6.5C7 6.5 5 8.5 5 11c0 1.4.6 2.6 1.6 3.5L6 16l1.7-.9c.6.2 1.2.4 1.8.4.2 0 .4 0 .6-.1A4.1 4.1 0 0 1 9.7 14H9.5c-2.5 0-4.5-2-4.5-4.5S7 5 9.5 5s4.5 2 4.5 4.5c0 .1 0 .3-.1.4A4.3 4.3 0 0 1 15 9.6c-.1-2.9-2.6-5.1-5.5-5.1zm-.5 2.5a1 1 0 1 1 0 2 1 1 0 0 1 0-2zm3.5 0a1 1 0 1 1 0 2 1 1 0 0 1 0-2zm1.5 3.5c-2.2 0-4 1.6-4 3.5S11.8 19.5 14 19.5c.5 0 .9-.1 1.4-.2l1.4.7-.4-1.3c.8-.6 1.3-1.5 1.3-2.5-.1-2-1.8-3.4-4.2-3.4zm-1 1.5a.8.8 0 1 1 0 1.6.8.8 0 0 1 0-1.6zm2 0a.8.8 0 1 1 0 1.6.8.8 0 0 1 0-1.6z"/>
  </svg>
);

const CheckIcon = () => (
  <svg width="12" height="10" viewBox="0 0 12 10" fill="none" aria-hidden="true">
    <path d="M1 5 L4.5 8.5 L11 1" stroke="#fff" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
  </svg>
);

const StatusIcons = () => (
  <div className={s.statusRight}>
    <svg width="18" height="12" viewBox="0 0 18 12" fill="#fff" aria-hidden="true">
      <rect x="0" y="7" width="3" height="5" rx="1"/>
      <rect x="5" y="4" width="3" height="8" rx="1"/>
      <rect x="10" y="1" width="3" height="11" rx="1"/>
      <rect x="15" y="-2" width="3" height="14" rx="1" opacity=".45"/>
    </svg>
    <svg width="16" height="12" viewBox="0 0 16 12" fill="#fff" aria-hidden="true">
      <path d="M8 2.2c2 0 3.9.7 5.4 2L15 2.8A9.6 9.6 0 0 0 8 0 9.6 9.6 0 0 0 1 2.8l1.6 1.4C4.1 2.9 6 2.2 8 2.2z"/>
      <path d="M8 5.6c1 0 2 .4 2.8 1l1.4-1.4A6 6 0 0 0 8 3.8a6 6 0 0 0-4.2 1.4l1.4 1.4C6 5.9 7 5.6 8 5.6z"/>
      <circle cx="8" cy="10" r="1.6"/>
    </svg>
    <svg width="26" height="12" viewBox="0 0 26 12" aria-hidden="true">
      <rect x="0.5" y="0.5" width="22" height="11" rx="2.5" fill="none" stroke="#fff" strokeWidth="1"/>
      <rect x="23" y="4" width="1.5" height="4" rx="0.5" fill="rgba(255,255,255,.6)"/>
      <rect x="2" y="2" width="18" height="8" rx="1" fill="#fff"/>
    </svg>
  </div>
);

export const AuthPage: React.FC = () => {
  const nav = useNavigate();
  const [searchParams] = useSearchParams();

  const redirect = searchParams.get('redirect');
  const guestSessionId = searchParams.get('guest_session_id') ??
    localStorage.getItem('guest_session_token');

  const [consentAccepted, setConsentAccepted] = useState(false);
  const [authState, setAuthState] = useState<AuthState>('IDLE');
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [showDevForm, setShowDevForm] = useState(false);
  const [devAccount, setDevAccount] = useState('qa-normal@longfeng.test');
  const [devPassword, setDevPassword] = useState('Qa!Normal2026');

  const handleWechatLogin = useCallback(async () => {
    if (!consentAccepted) {
      setAuthState('CONSENT_REQUIRED');
      setErrorMsg('请先勾选同意协议');
      setTimeout(() => setErrorMsg(null), 3000);
      return;
    }

    setAuthState('LOGGING_IN');
    setErrorMsg(null);

    try {
      // dev / e2e 注入：window.__lf_dev_wx_code__ 优先 · 否则 fallback dev_code_alice
      // 生产环境（小程序内）应该调真 wx.login() 拿 code · H5 demo 暂时用 stub code
      const wxCode =
        (typeof window !== 'undefined' && window.__lf_dev_wx_code__) ||
        'dev_code_alice';
      const deviceFp = localStorage.getItem('__lf_device_fp__') || 'fp_unknown';

      const apiBase = import.meta.env.VITE_API_BASE || '';
      const resp = await fetch(`${apiBase}/api/auth/wechat-login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          wx_code: wxCode,
          device_fp: deviceFp,
          consent_accepted: true,
        }),
      });

      if (!resp.ok) {
        throw new Error(`HTTP ${resp.status}`);
      }

      // BE 用统一 envelope { code, message, data, trace_id } · 解一层
      const envelope = await resp.json() as {
        code: number;
        message: string;
        data: {
          access_token: string;
          refresh_token: string;
          student_id: string;
          is_new_user: boolean;
          expires_at?: number | string;
        };
        trace_id?: string;
      };
      if (envelope.code !== 0 || !envelope.data) {
        throw new Error(`BE error code=${envelope.code} msg=${envelope.message}`);
      }
      const data = envelope.data;

      localStorage.setItem('lf:token', data.access_token);
      localStorage.setItem('lf_user_tier', 'NORMAL');

      // claim guest_session（如有）— 后端尚未实现 /api/guest/claim 故先静默尝试
      if (guestSessionId) {
        setAuthState('CLAIMING');
        try {
          await fetch(`${apiBase}/api/guest/claim`, {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              'Authorization': `Bearer ${data.access_token}`,
            },
            body: JSON.stringify({ guest_session_id: guestSessionId, device_fp: deviceFp }),
          });
        } catch {
          // claim 失败不阻塞登录主流程（spec §9 异常路径）
        }
        localStorage.removeItem('guest_session_token');
      }

      setAuthState('SUCCESS');
      nav(redirect ?? '/', { replace: true });
    } catch (err) {
      console.error('[wechat-login] failed', err);
      setAuthState('ERROR');
      setErrorMsg('登录失败 · 请重试');
      setTimeout(() => setAuthState('IDLE'), 3000);
    }
  }, [consentAccepted, guestSessionId, redirect, nav]);

  const isLoggingIn = authState === 'LOGGING_IN' || authState === 'CLAIMING';
  const isDisabled = !consentAccepted || isLoggingIn;

  const loadingText = authState === 'CLAIMING'
    ? '正在把刚才的分析保存到错题本...'
    : '正在登录...';

  return (
    <main
      className={s.page}
      role="main"
      data-testid="p00-root"
      data-mood="A"
    >
      {/* ── StatusBar ── */}
      <div className={s.statusbar} data-testid="p00-statusbar" role="presentation">
        <span>9:41</span>
        <StatusIcons />
      </div>

      {/* ── Hero (Mood A 深蓝 380px + 3 blob) ── */}
      <div className={s.hero} role="presentation">
        <div className={s.blob} />
      </div>

      {/* ── Hero 内容 ── */}
      <div className={s.heroContent} data-testid="p00-logo-zone">
        <div className={s.logo} aria-hidden="true">
          <span className={s.logoText}>AI</span>
        </div>
        <h1
          className={s.appName}
          aria-label="AI 错题本 · 让每一道错题都被看见"
          data-testid="p00-logo-zone-logo"
        >
          AI 错题本
        </h1>
        <p className={s.slogan}>让每一道错题都被看见</p>
      </div>

      {/* ── Scroll area (overlap over hero) ── */}
      <div className={s.scroll}>

        {/* ── 登录卡 ── */}
        <section className={s.loginCard} aria-label="登录方式">
          <h2 className={s.cardTitle}>选择登录方式</h2>
          <p className={s.cardSubtitle}>首次登录即自动注册账号</p>

          {/* 错误提示 */}
          {errorMsg && (
            <div
              role="alert"
              style={{
                background: 'rgba(255, 59, 48, 0.08)',
                borderRadius: 10,
                padding: '10px 14px',
                fontSize: 13,
                color: '#FF3B30',
                fontWeight: 600,
                marginBottom: 14,
                textAlign: 'center',
              }}
            >
              {errorMsg}
            </div>
          )}

          {/* 微信主按钮 (铁律 1 例外) */}
          <button
            className={s.wechatBtn}
            data-testid="p00-wechat-cta-btn"
            data-iron-rule-1-exception="wechat-brand"
            onClick={handleWechatLogin}
            disabled={isDisabled}
            aria-disabled={isDisabled}
            aria-label="微信一键登录"
            type="button"
          >
            <WechatIcon />
            微信一键登录
          </button>

          {/* 其他登录方式 */}
          <div className={s.otherMethods}>
            <button
              className={s.otherMethodsLink}
              data-testid="p00-other-methods-link"
              type="button"
              style={{ fontSize: 14, fontWeight: 600, color: '#007AFF' }}
              onClick={() => setShowDevForm((v) => !v)}
            >
              其他登录方式
            </button>
          </div>

          {/* dev-only 账密 form · 默认隐藏 · QA loginAs fixture 通过 click "其他登录方式" 展开 */}
          {showDevForm && (
            <div data-testid="auth-dev-form" style={{ marginTop: 16, display: 'flex', flexDirection: 'column', gap: 8 }}>
              <input
                type="text"
                data-testid="auth-form-account"
                placeholder="账号 (dev only)"
                value={devAccount}
                onChange={(e) => setDevAccount(e.target.value)}
                style={{ padding: 12, borderRadius: 8, border: '1px solid #C7C7CC', fontSize: 14 }}
              />
              <input
                type="password"
                data-testid="auth-form-password"
                placeholder="密码 (dev only)"
                value={devPassword}
                onChange={(e) => setDevPassword(e.target.value)}
                style={{ padding: 12, borderRadius: 8, border: '1px solid #C7C7CC', fontSize: 14 }}
              />
              <button
                type="button"
                data-testid="auth-form-submit"
                onClick={async () => {
                  const tier = devAccount.includes('vipplus') ? 'VIP_PLUS' : devAccount.includes('vip') ? 'VIP' : 'NORMAL';
                  const b64u = (s: string) => btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
                  const header = b64u('{"alg":"HS256","typ":"JWT"}');
                  const payload = b64u(JSON.stringify({
                    sub: `qa-${tier.toLowerCase()}`,
                    scope: 'USER',
                    tier,
                    exp: Math.floor(Date.now() / 1000) + 24 * 3600,
                  }));
                  const jwt = `${header}.${payload}.devsig`;
                  localStorage.setItem('lf:token', jwt);
                  localStorage.setItem('lf_user_tier', tier);
                  window.location.href = '/';
                }}
                style={{ padding: 12, borderRadius: 8, border: 'none', background: '#007AFF', color: '#fff', fontSize: 14, fontWeight: 600 }}
              >
                登录 (dev)
              </button>
            </div>
          )}

          {/* ── 协议勾选 ── */}
          <footer role="contentinfo" className={s.consentBar} data-testid="p00-consent-bar">
            <label className={s.checkboxWrap} htmlFor="consent-checkbox">
              <input
                id="consent-checkbox"
                type="checkbox"
                className={s.checkboxInput}
                checked={consentAccepted}
                onChange={(e) => {
                  setConsentAccepted(e.target.checked);
                  if (authState === 'CONSENT_REQUIRED') setAuthState('IDLE');
                }}
                role="checkbox"
                aria-checked={consentAccepted}
                aria-label="同意《用户协议》和《隐私政策》"
                data-testid="p00-consent-bar-checkbox"
              />
              <div className={s.checkboxVisual}>
                {consentAccepted && <CheckIcon />}
              </div>
            </label>
            <p className={s.consentText}>
              登录即代表同意{' '}
              <a
                href="/legal/tos"
                className={s.consentLink}
                data-testid="p00-consent-bar-link-tos"
                target="_blank"
                rel="noopener noreferrer"
              >
                《用户协议》
              </a>
              {' '}和{' '}
              <a
                href="/legal/privacy"
                className={s.consentLink}
                data-testid="p00-consent-bar-link-privacy"
                target="_blank"
                rel="noopener noreferrer"
              >
                《隐私政策》
              </a>
              ，并授权使用您的微信信息
            </p>
          </footer>
        </section>
      </div>

      {/* ── 加载 Sheet ── */}
      {isLoggingIn && (
        <div className={s.loadingOverlay} role="dialog" aria-modal="true" aria-label={loadingText}>
          <div className={s.loadingSheet}>
            <div className={s.loadingSpinner} aria-hidden="true" />
            <p className={s.loadingText}>{loadingText}</p>
          </div>
        </div>
      )}
    </main>
  );
};
