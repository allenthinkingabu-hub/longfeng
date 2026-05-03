// S7 · TabShell.tsx
// 已登录主 Shell · 5 Tab（Home/Wrongbook/Review/Calendar/Me）+ 11 二级页路由
//
// STYLE-TRUTH §4.14 Tabbar 规范（archive 01_home.html 实测）：
//   - 5 Tab：首页 / 错题本 / 拍题 / 复习 / 我的
//   - bg: rgba(242,242,247,0.86) + blur(22px) saturate(180%)
//   - active: #007AFF；非活跃: #8E8E93
//   - 复习 Tab 有红色 badge（待复习数）
//
// PRD §2A.3.1 硬性导航规则：
//   - 深链通过 deeplink-router 解析
//   - token 过期跳 P00（redirect 回原页）

import React, { Suspense, useCallback } from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import s from './TabShell.module.css';

// ── Tab 配置 ──────────────────────────────────────────────────────────────────

interface TabConfig {
  key: string;
  label: string;
  route: string;
  /** 匹配当前路径（精确 or 前缀） */
  matchPrefix?: boolean;
  testId: string;
  badgeCount?: number;
  icon: (active: boolean) => React.ReactNode;
}

// SVG 图标（与 archive 01_home.html 完全对齐）
const HomeIcon = (active: boolean) => (
  <svg viewBox="0 0 24 24" fill="none" aria-hidden="true" width="24" height="24">
    <path
      d="M3 11 L12 3 L21 11 V20 a1 1 0 0 1 -1 1 H14 V14 H10 V21 H4 a1 1 0 0 1 -1 -1 Z"
      stroke={active ? '#007AFF' : '#8E8E93'}
      strokeWidth="1.8"
      strokeLinejoin="round"
      strokeLinecap="round"
    />
  </svg>
);

const WrongbookIcon = (active: boolean) => (
  <svg viewBox="0 0 24 24" fill="none" aria-hidden="true" width="24" height="24">
    <path
      d="M5 4 h10 l4 4 v12 a2 2 0 0 1 -2 2 H5 a2 2 0 0 1 -2 -2 V6 a2 2 0 0 1 2 -2 z"
      stroke={active ? '#007AFF' : '#8E8E93'}
      strokeWidth="1.5"
    />
    <path
      d="M8 13 h8 M8 17 h5"
      stroke={active ? '#007AFF' : '#8E8E93'}
      strokeWidth="1.5"
      strokeLinecap="round"
    />
  </svg>
);

const CaptureIcon = (active: boolean) => (
  <svg viewBox="0 0 24 24" fill="none" aria-hidden="true" width="24" height="24">
    <rect x="3" y="7" width="18" height="13" rx="3" stroke={active ? '#007AFF' : '#8E8E93'} strokeWidth="1.5" />
    <circle cx="12" cy="13.5" r="3.5" stroke={active ? '#007AFF' : '#8E8E93'} strokeWidth="1.5" />
    <path d="M9 7 L10 5 h4 L15 7" stroke={active ? '#007AFF' : '#8E8E93'} strokeWidth="1.5" strokeLinejoin="round" />
  </svg>
);

const ReviewIcon = (active: boolean) => (
  <svg viewBox="0 0 24 24" fill="none" aria-hidden="true" width="24" height="24">
    <path
      d="M4 12 a8 8 0 1 1 2.3 5.6"
      stroke={active ? '#007AFF' : '#8E8E93'}
      strokeWidth="1.5"
      strokeLinecap="round"
    />
    <path
      d="M4 18 V12 h6"
      stroke={active ? '#007AFF' : '#8E8E93'}
      strokeWidth="1.5"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
    <path
      d="M12 8 V12 L15 14"
      stroke={active ? '#007AFF' : '#8E8E93'}
      strokeWidth="1.5"
      strokeLinecap="round"
    />
  </svg>
);

const MeIcon = (active: boolean) => (
  <svg viewBox="0 0 24 24" fill="none" aria-hidden="true" width="24" height="24">
    <circle cx="12" cy="9" r="4" stroke={active ? '#007AFF' : '#8E8E93'} strokeWidth="1.5" />
    <path
      d="M4 20 c1.5 -4 5 -6 8 -6 s6.5 2 8 6"
      stroke={active ? '#007AFF' : '#8E8E93'}
      strokeWidth="1.5"
      strokeLinecap="round"
    />
  </svg>
);

// ── Props ──────────────────────────────────────────────────────────────────────

interface TabShellProps {
  /** 待复习数量（用于复习 Tab badge） */
  reviewBadgeCount?: number;
}

// ── 工具：判断 Tab 激活 ────────────────────────────────────────────────────────

function isTabActive(tabRoute: string, currentPath: string, matchPrefix?: boolean): boolean {
  if (matchPrefix) {
    return currentPath.startsWith(tabRoute);
  }
  return currentPath === tabRoute;
}

// ── 组件 ──────────────────────────────────────────────────────────────────────

/**
 * TabShell · 已登录主应用骨架
 *
 * 路由结构（对应 PRD §2A.3.1 Tab Shell 导航规则）：
 *   Tab 1 · P-HOME      → `/`
 *   Tab 2 · P05 List    → `/wrongbook`
 *   Tab 3 · P02 Capture → `/capture`
 *   Tab 4 · P07 Review  → `/review`
 *   Tab 5 · P13 Me      → `/me`
 *
 * 二级页（从 Tab 进入）：
 *   /analyzing/:taskId       P03 Analyzing
 *   /question/:qid/result    P04 Result
 *   /wrongbook/:qid          P06 Detail
 *   /review/exec/:nodeId     P08 ReviewExec
 *   /review/done             P09 ReviewDone
 *   /calendar/month          P10 CalendarMonth
 *   /event/:eventId          P11 EventDetail
 *   /notifications           P12 Notifications
 *
 * 使用方式（App.tsx）：
 *   <Route element={<TabShell reviewBadgeCount={8} />}>
 *     <Route path="/" element={<HomePage />} />
 *     <Route path="/wrongbook" element={<WrongbookListPage />} />
 *     ...
 *   </Route>
 */
export const TabShell: React.FC<TabShellProps> = ({ reviewBadgeCount }) => {
  const location = useLocation();
  const navigate = useNavigate();

  const tabs: TabConfig[] = [
    {
      key: 'home',
      label: '首页',
      route: '/',
      testId: 'tab-home',
      icon: HomeIcon,
    },
    {
      key: 'wrongbook',
      label: '错题本',
      route: '/wrongbook',
      matchPrefix: true,
      testId: 'tab-wrongbook',
      icon: WrongbookIcon,
    },
    {
      key: 'capture',
      label: '拍题',
      route: '/capture',
      matchPrefix: true,
      testId: 'tab-capture',
      icon: CaptureIcon,
    },
    {
      key: 'review',
      label: '复习',
      route: '/review',
      matchPrefix: true,
      testId: 'tab-review',
      badgeCount: reviewBadgeCount,
      icon: ReviewIcon,
    },
    {
      key: 'me',
      label: '我的',
      route: '/me',
      matchPrefix: true,
      testId: 'tab-me',
      icon: MeIcon,
    },
  ];

  const handleTabClick = useCallback((route: string) => {
    navigate(route);
  }, [navigate]);

  return (
    <div
      className={s.root}
      data-testid="tab-shell"
      data-mood="B"
    >
      {/* 内容区 Outlet */}
      <main
        className={s.outlet}
        data-testid="tab-shell-outlet"
        role="main"
        id="main-content"
        aria-label="页面内容"
      >
        <Suspense fallback={<TabShellLoading />}>
          <Outlet />
        </Suspense>
      </main>

      {/* Tab Bar（固定底部 · STYLE-TRUTH §4.14） */}
      <nav
        className={s.tabbar}
        data-testid="tab-shell-tabbar"
        role="tablist"
        aria-label="主导航"
      >
        {tabs.map((tab) => {
          const active = isTabActive(tab.route, location.pathname, tab.matchPrefix);
          return (
            <button
              key={tab.key}
              className={`${s.tab} ${active ? s.tabActive : ''}`}
              role="tab"
              aria-selected={active}
              aria-label={tab.label}
              onClick={() => handleTabClick(tab.route)}
              data-testid={tab.testId}
              type="button"
            >
              {/* 复习 badge */}
              {tab.badgeCount && tab.badgeCount > 0 && (
                <span
                  className={s.badge}
                  aria-label={`${tab.badgeCount} 项待复习`}
                  data-testid={`${tab.testId}-badge`}
                >
                  {tab.badgeCount > 99 ? '99+' : tab.badgeCount}
                </span>
              )}
              <span className={s.tabIcon}>
                {tab.icon(active)}
              </span>
              <span className={s.tabLabel}>{tab.label}</span>
            </button>
          );
        })}
      </nav>
    </div>
  );
};

// ── Loading 占位 ──────────────────────────────────────────────────────────────

const TabShellLoading: React.FC = () => (
  <div
    style={{
      padding: '64px 16px',
      textAlign: 'center',
      color: '#8E8E93',
      fontSize: 13,
    }}
    role="status"
    aria-live="polite"
    aria-label="页面加载中"
  />
);

export default TabShell;
