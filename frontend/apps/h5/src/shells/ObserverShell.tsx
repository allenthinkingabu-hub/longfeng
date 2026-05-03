// S7 · ObserverShell.tsx
// 观察者 Shell · scope=READ · OBSERVER JWT
//
// C4 红线（TDD §1.3）：
//   - OBSERVER JWT 三重防护：Gateway 403 + 业务层 + 前端 ARIA aria-disabled
//   - 写按钮 aria-disabled="true" + tabIndex=-1
//   - SCOPE=READ 水印（user-select:none · pointer-events:none）
//
// Mood E · teal-observer（STYLE-TRUTH §3）
// archive 参考：design/mockups/wrongbook/_archive/18_observer.html

import React, { Suspense, useCallback } from 'react';
import { Outlet, useNavigate } from 'react-router-dom';
import { useObserverGuard, setObserverGuardToast } from '../hooks/useObserverGuard';
import s from './ObserverShell.module.css';

// ── 类型 ──────────────────────────────────────────────────────────────────────

interface ObserverInfo {
  /** 观察者角色描述（如 "家长"）*/
  role?: string;
  /** 被观察学生名字 */
  studentName?: string;
  /** 邀请码 */
  code?: string;
  /** 会话剩余时长（分钟）*/
  remainingMinutes?: number;
}

interface ObserverShellProps {
  observerInfo?: ObserverInfo;
  /** Toast 注入（由 App 层提供 UI 层 toast 函数）*/
  onToast?: (message: string) => void;
  /** 退出按钮处理（清除 observer session）*/
  onExit?: () => void;
}

// ── Ghost Tab 配置（只读 · archive 18_observer.html）── */

interface GhostTabConfig {
  key: string;
  label: string;
  /** 是否为观察者可查看的只读 tab（active = 无斑马纹 + opacity:1）*/
  readOnly: boolean;
  /** 是否为当前活跃 tab */
  active?: boolean;
  icon: React.ReactNode;
}

// SVG 内联（保持与 archive 一致）
const GhostHomeIcon = (
  <svg viewBox="0 0 24 24" fill="none" aria-hidden="true" width="22" height="22">
    <path d="M3 11 L12 3 L21 11 V20 a1 1 0 0 1 -1 1 H14 V14 H10 V21 H4 a1 1 0 0 1 -1 -1 Z" stroke="#636366" strokeWidth="1.8" strokeLinejoin="round" strokeLinecap="round" />
  </svg>
);
const GhostWrongbookIcon = (
  <svg viewBox="0 0 24 24" fill="none" aria-hidden="true" width="22" height="22">
    <path d="M5 4 h10 l4 4 v12 a2 2 0 0 1 -2 2 H5 a2 2 0 0 1 -2 -2 V6 a2 2 0 0 1 2 -2 z" stroke="#007AFF" strokeWidth="1.5" />
    <path d="M8 13 h8 M8 17 h5" stroke="#007AFF" strokeWidth="1.5" strokeLinecap="round" />
  </svg>
);
const GhostCaptureIcon = (
  <svg viewBox="0 0 24 24" fill="none" aria-hidden="true" width="22" height="22">
    <rect x="3" y="7" width="18" height="13" rx="3" stroke="#636366" strokeWidth="1.5" />
    <circle cx="12" cy="13.5" r="3.5" stroke="#636366" strokeWidth="1.5" />
    <path d="M9 7 L10 5 h4 L15 7" stroke="#636366" strokeWidth="1.5" strokeLinejoin="round" />
  </svg>
);
const GhostReviewIcon = (
  <svg viewBox="0 0 24 24" fill="none" aria-hidden="true" width="22" height="22">
    <path d="M4 12 a8 8 0 1 1 2.3 5.6" stroke="#636366" strokeWidth="1.5" strokeLinecap="round" />
    <path d="M4 18 V12 h6" stroke="#636366" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
    <path d="M12 8 V12 L15 14" stroke="#636366" strokeWidth="1.5" strokeLinecap="round" />
  </svg>
);
const GhostMeIcon = (
  <svg viewBox="0 0 24 24" fill="none" aria-hidden="true" width="22" height="22">
    <circle cx="12" cy="9" r="4" stroke="#636366" strokeWidth="1.5" />
    <path d="M4 20 c1.5 -4 5 -6 8 -6 s6.5 2 8 6" stroke="#636366" strokeWidth="1.5" strokeLinecap="round" />
  </svg>
);

const GHOST_TABS: GhostTabConfig[] = [
  { key: 'home',      label: '首页',   readOnly: false, icon: GhostHomeIcon },
  { key: 'wrongbook', label: '错题本', readOnly: true,  active: true, icon: GhostWrongbookIcon },
  { key: 'capture',   label: '拍题',   readOnly: false, icon: GhostCaptureIcon },
  { key: 'review',    label: '复习',   readOnly: false, icon: GhostReviewIcon },
  { key: 'me',        label: '我的',   readOnly: false, icon: GhostMeIcon },
];

// ── 组件 ──────────────────────────────────────────────────────────────────────

/**
 * ObserverShell · 观察者会话 Shell
 *
 * 实现 C4 红线三重防护（前端层）：
 *   1. SCOPE=READ 水印（视觉提示）
 *   2. Ghost Tab Dock：写 tab aria-disabled + tabIndex=-1 + 斑马纹遮罩
 *   3. useObserverGuard：所有写动词请求被 guardWrite 拦截 + toast
 *
 * 使用方式（App.tsx）：
 *   <Route element={<ObserverShell observerInfo={...} />}>
 *     <Route path="/observer/:code" element={<ObserverHomePage />} />
 *   </Route>
 */
export const ObserverShell: React.FC<ObserverShellProps> = ({
  observerInfo = {},
  onToast,
  onExit,
}) => {
  const navigate = useNavigate();

  // 注入 toast 函数到全局 guard
  if (onToast) setObserverGuardToast(onToast);

  const { isObserver, guardWrite } = useObserverGuard('观察者模式仅供查看，无法执行此操作');

  const handleExit = useCallback(() => {
    if (onExit) {
      onExit();
    } else {
      // 默认：清除 token 并跳到 P-LANDING
      try {
        localStorage.removeItem('lf:token');
      } catch {
        /* ignore */
      }
      navigate('/welcome');
    }
  }, [navigate, onExit]);

  // 格式化剩余时间
  const formatRemaining = (minutes?: number): string => {
    if (!minutes) return '—';
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    return h > 0 ? `${h}h ${m}m` : `${m}m`;
  };

  const roleText = observerInfo.role ?? '家长';
  const studentName = observerInfo.studentName ?? '同学';
  const avatarLetter = (observerInfo.role ?? 'P').charAt(0).toUpperCase();

  return (
    <div
      className={s.root}
      data-testid="observer-shell"
      data-mood="E"
    >
      {/* Hero Header（Mood E teal-observer）*/}
      <div className={s.header} role="presentation" aria-hidden="true" />

      {/* SCOPE=READ 水印（C4 红线 · user-select:none）*/}
      <div
        className={s.watermark}
        aria-hidden="true"
        data-testid="observer-watermark"
      >
        scope = READ · observer only · scope = READ · observer only
      </div>

      {/* 匿名 Nav（返回 · brand · 退出）*/}
      <nav
        className={s.anonNav}
        data-testid="observer-shell-nav"
        aria-label="观察者导航"
      >
        {/* 返回按钮 */}
        <button
          className={s.backBtn}
          onClick={() => navigate(-1)}
          aria-label="返回上一页"
          data-testid="observer-back-btn"
          type="button"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <polyline points="15 18 9 12 15 6" />
          </svg>
        </button>

        {/* Brand 标识 */}
        <div className={s.brand} aria-label="观察者模式">
          <span className={s.brandDot} aria-hidden="true" />
          <span className={s.brandName}>观察者 · Observer</span>
        </div>

        {/* 退出按钮 */}
        <button
          className={s.exitBtn}
          onClick={handleExit}
          aria-label="退出观察者模式"
          data-testid="observer-exit-btn"
          type="button"
        >
          <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
            <polyline points="16 17 21 12 16 7" />
            <line x1="21" y1="12" x2="9" y2="12" />
          </svg>
          退出
        </button>
      </nav>

      {/* Observer Identity Card（archive §identity）*/}
      <div
        className={s.identityCard}
        data-testid="observer-identity-card"
        role="region"
        aria-label="观察者身份信息"
      >
        <div className={s.idRow}>
          <div className={s.idRole}>
            <div
              className={s.idAvatar}
              aria-label={`观察者头像 ${avatarLetter}`}
            >
              {avatarLetter}
            </div>
            <div className={s.idTx}>
              <span className={s.idKey}>Observer · 观察者</span>
              <span className={s.idValue}>
                您是 {studentName} 的
                <em className={s.idValueEm}>{roleText}</em>
              </span>
            </div>
          </div>
          <span
            className={s.scopeBadge}
            data-testid="observer-scope-badge"
            aria-label="只读模式"
          >
            ● READ
          </span>
        </div>

        <div className={s.idMeta}>
          <div className={s.idMetaItem}>
            <span className={s.idMetaKey}>会话有效</span>
            <span className={s.idMetaValue}>
              剩余 <em className={s.idMetaEm}>{formatRemaining(observerInfo.remainingMinutes)}</em>
            </span>
          </div>
          {observerInfo.code && (
            <div className={s.idMetaItem}>
              <span className={s.idMetaKey}>邀请码</span>
              <span className={s.idMetaValue} style={{ fontFamily: "'SF Mono', monospace" }}>
                {observerInfo.code}
              </span>
            </div>
          )}
          <div className={s.idMetaItem}>
            <span className={s.idMetaKey}>学生可撤销</span>
            <span className={s.idMetaValue}>● 秒级生效</span>
          </div>
        </div>
      </div>

      {/* 内容 Outlet（只读 · useObserverGuard 守护写操作）*/}
      <main
        className={s.outlet}
        data-testid="observer-shell-outlet"
        role="main"
        aria-label="观察者只读内容"
      >
        <Suspense fallback={<ObserverLoading />}>
          <Outlet context={{ isObserver, guardWrite }} />
        </Suspense>
      </main>

      {/* Ghost Tab Dock（只读 · C4 红线 · aria-disabled）*/}
      <nav
        className={s.tabghost}
        role="navigation"
        aria-label="只读导航（观察者模式）"
      >
        {GHOST_TABS.map((tab) => {
          const isDisabled = !tab.readOnly;
          return (
            <div
              key={tab.key}
              className={`${s.ghostTab} ${tab.active ? s.ghostTabActive : ''}`}
              role="tab"
              aria-disabled={isDisabled}
              aria-selected={tab.active ?? false}
              aria-label={isDisabled ? `${tab.label}（观察者不可用）` : tab.label}
              tabIndex={isDisabled ? -1 : 0}
              data-testid={`observer-ghost-tab-${tab.key}`}
            >
              <span className={s.ghostTabIcon}>{tab.icon}</span>
              <span className={s.ghostTabLabel}>{tab.label}</span>
            </div>
          );
        })}
      </nav>
    </div>
  );
};

// ── Loading 占位 ──────────────────────────────────────────────────────────────

const ObserverLoading: React.FC = () => (
  <div
    style={{ padding: '32px 16px', textAlign: 'center', color: '#8E8E93', fontSize: 13 }}
    role="status"
    aria-live="polite"
    aria-label="内容加载中"
  />
);

export default ObserverShell;
