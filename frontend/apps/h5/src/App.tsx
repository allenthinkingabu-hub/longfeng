// S7 · App.tsx · H5 路由 · Shell 集成
// FE-01-shells-bootstrap 后路由架构：
//   AnonymousShell → 匿名路由（P-LANDING / P-GUEST-CAPTURE / P-SHARED）
//   ObserverShell  → 观察者路由（P-OBSERVER）
//   TabShell       → 已登录路由（5 Tab + 11 二级页）
//
// 注：页面组件（P-HOME / P03 / P04 等）由后续 FE-02..FE-08 Agent 实现。
// 本文件仅建立 Shell 骨架路由，用 React.lazy 占位页补齐（待替换）。

import React, { Suspense, lazy } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';

// ── Shell 组件 ──────────────────────────────────────────────────────────────
import { AnonymousShell } from './shells/AnonymousShell';
import { TabShell } from './shells/TabShell';
import { ObserverShell } from './shells/ObserverShell';

// ── 现有页面（S7 已实现）──────────────────────────────────────────────────────
import { CapturePage } from './pages/Capture';
import { AnalyzingPage } from './pages/Analyzing';
import { ResultPage } from './pages/Result';
import { ListPage } from './pages/List';
import { DetailPage } from './pages/Detail';
import { LandingPage } from './pages/Landing';
import { GuestCapturePage } from './pages/GuestCapture';
import { SharedPage } from './pages/Shared';

// ── S8 FE-05 实现 (P10/P11) ──────────────────────────────────────────────────
import { CalendarMonthPage } from './pages/CalendarMonth';
import { EventDetailPage } from './pages/EventDetail';

// ── S8 FE-04 复习三页 (P07/P08/P09) ──────────────────────────────────────────
import { ReviewTodayPage } from './pages/ReviewToday';
import { ReviewExecPage } from './pages/ReviewExec';
import { ReviewDonePage } from './pages/ReviewDone';

// ── S8 FE-07 · 新实现页面 (P00/P-HOME/P12/P13) ──────────────────────────────
import { AuthPage } from './pages/Auth';
import { HomePage } from './pages/Home';
import { NotificationsPage } from './pages/Notifications';
import { MePage } from './pages/Settings';

// ── 占位页（待后续 FE Agent 实现 · lazy 加载）──────────────────────────────
// 暂时用内联占位，避免破坏 build
const PlaceholderPage: React.FC<{ name: string }> = ({ name }) => (
  <div style={{ padding: 32, textAlign: 'center', color: '#8E8E93', fontSize: 14 }}>
    {name} · 待实现（FE-0x Agent）
  </div>
);

// 各占位（FE-07 已替换 AuthPage / HomePage / NotificationsPage / MePage）
/**
 * P-WELCOMEBACK · 设备指纹回流唤起 (P1 minimal skeleton for SC-14 e2e)
 * - 指纹存在 (lf_device_fp / __lf_device_fp__) → 显示"欢迎回来"
 * - 指纹缺失 → 自动跳 /auth (降级到 P00)
 */
const WelcomeBackPage: React.FC = () => {
  const [hasFp, setHasFp] = React.useState<boolean | null>(null);
  React.useEffect(() => {
    let fp: string | null = null;
    try {
      fp =
        localStorage.getItem('__lf_device_fp__') ||
        sessionStorage.getItem('__lf_device_fp__') ||
        localStorage.getItem('lf_device_fp');
    } catch { /* private mode */ }
    if (!fp) {
      window.location.replace('/auth');
      return;
    }
    setHasFp(true);
  }, []);
  if (hasFp !== true) {
    return (
      <main
        data-testid="p-welcomeback-root"
        data-mood="A"
        style={{ padding: 32, color: '#8E8E93' }}
        role="status"
        aria-label="正在识别设备"
      >
        正在识别设备...
      </main>
    );
  }
  return (
    <main
      data-testid="p-welcomeback-root"
      data-mood="A"
      role="main"
      aria-label="欢迎回来"
      style={{ padding: 32, fontFamily: 'system-ui, sans-serif' }}
    >
      <h1 style={{ fontSize: 24, fontWeight: 700, marginBottom: 8 }}>
        欢迎回来
      </h1>
      <p style={{ color: '#636366', fontSize: 14 }}>
        继续上次的复习吧，记忆曲线还在等你。
      </p>
      <button
        type="button"
        data-testid="p-welcomeback-continue-btn"
        onClick={() => { window.location.href = '/'; }}
        style={{ marginTop: 24, padding: '10px 18px', borderRadius: 10, border: 'none', background: '#007AFF', color: '#fff', fontSize: 15, fontWeight: 600 }}
      >
        继续学习
      </button>
    </main>
  );
};
const ObserverHomePage = () => <PlaceholderPage name="P-OBSERVER 观察者主页" />;
// P00 AuthPage · P-HOME · P07/P08/P09 · P10/P11 · P12/P13 全部 import 真实页 (FE-04/05/07)
// 仅余 P-WELCOMEBACK (P1) + P-OBSERVER (P1) placeholder · 见上

// ── App 路由 ─────────────────────────────────────────────────────────────────

/**
 * App · H5 路由根组件
 *
 * 路由层级：
 *   /welcome            → AnonymousShell > LandingPage (P-LANDING)
 *   /guest/capture      → AnonymousShell > GuestCapturePage (P-GUEST-CAPTURE)
 *   /s/:shareToken      → AnonymousShell > SharedPage (P-SHARED)
 *   /welcome-back       → AnonymousShell > WelcomeBackPage (P-WELCOMEBACK · P1)
 *   /observer/:code     → ObserverShell > ObserverHomePage (P-OBSERVER)
 *   /auth               → AuthPage (P00 独立页 · 无 Shell)
 *   / (index)           → TabShell > HomePage (P-HOME)
 *   /capture            → TabShell > CapturePage (P02)
 *   /analyzing/:taskId  → TabShell > AnalyzingPage (P03)
 *   /question/:qid/result → TabShell > ResultPage (P04)
 *   /wrongbook          → TabShell > ListPage (P05)
 *   /wrongbook/:id      → TabShell > DetailPage (P06)
 *   /review             → TabShell > ReviewTodayPage (P07)
 *   /review/exec/:id    → TabShell > ReviewExecPage (P08)
 *   /review/done        → TabShell > ReviewDonePage (P09)
 *   /calendar/month     → TabShell > CalendarMonthPage (P10)
 *   /event/:eventId     → TabShell > EventDetailPage (P11)
 *   /notifications      → TabShell > NotificationsPage (P12)
 *   /me                 → TabShell > MePage (P13)
 */
export const App: React.FC = () => (
  <Routes>
    {/* P-LANDING / P-GUEST-CAPTURE / P-SHARED 独立挂 · 自带 hero/nav/CTA · 不进 shell ·
        避免 shell nav + page nav 双重渲染 (per _archive/14|15|16_*.html data-mockup-chrome) */}
    <Route path="/welcome" element={<LandingPage />} />
    <Route path="/guest/capture" element={<GuestCapturePage />} />
    <Route path="/s/:shareToken" element={<SharedPage />} />

    {/* ── 匿名 Shell（Mood A · 无 TabBar · 仅 P-WELCOMEBACK 用）── */}
    <Route element={<AnonymousShell loginRoute="/auth" />}>
      <Route path="/welcome-back" element={<WelcomeBackPage />} />
      {/* SC-14 异常 spec 用 /welcomeback (无横线) · alias 兼容 */}
      <Route path="/welcomeback" element={<WelcomeBackPage />} />
    </Route>

    {/* ── 观察者 Shell（Mood E · scope=READ · C4 红线）── */}
    <Route element={<ObserverShell />}>
      <Route path="/observer/:code" element={<ObserverHomePage />} />
    </Route>

    {/* ── P00 登录（独立页 · 无 Shell）── */}
    <Route path="/auth" element={<AuthPage />} />

    {/* ── 已登录主 Shell（Mood A/B · TabBar 5 项 + 11 二级页）── */}
    <Route element={<TabShell reviewBadgeCount={0} />}>
      {/* Tab 1: P-HOME */}
      <Route path="/" element={<HomePage />} />

      {/* Tab 2: P05 错题本（含二级 P06）*/}
      <Route path="/wrongbook" element={<ListPage />} />
      <Route path="/wrongbook/:id" element={<DetailPage />} />

      {/* Tab 3: P02 拍题 + 二级 P03 / P04 */}
      <Route path="/capture" element={<CapturePage />} />
      <Route path="/analyzing/:taskId" element={<AnalyzingPage />} />
      <Route path="/question/:qid/result" element={<ResultPage />} />
      {/* SC-07 spec 用 /result/:qid alias 兼容 */}
      <Route path="/result/:qid" element={<ResultPage />} />

      {/* Tab 4: P07 复习 + 二级 P08 / P09 */}
      <Route path="/review" element={<ReviewTodayPage />} />
      <Route path="/review/exec/:nodeId" element={<ReviewExecPage />} />
      {/* SC-02 / SC-04 spec 用 /review/:nodeId/exec 形态 · alias 兼容 */}
      <Route path="/review/:nodeId/exec" element={<ReviewExecPage />} />
      <Route path="/review/done" element={<ReviewDonePage />} />

      {/* 二级：P10 / P11 / P12（从 P-HOME / P12 / 深链进入）*/}
      <Route path="/calendar/month" element={<CalendarMonthPage />} />
      <Route path="/event/:eventId" element={<EventDetailPage />} />
      <Route path="/notifications" element={<NotificationsPage />} />

      {/* Tab 5: P13 我的 */}
      <Route path="/me" element={<MePage />} />
    </Route>

    {/* ── 默认重定向（冷启动 → /welcome，由 resolve-entry 实际控制）── */}
    <Route path="*" element={<Navigate to="/welcome" replace />} />
  </Routes>
);
