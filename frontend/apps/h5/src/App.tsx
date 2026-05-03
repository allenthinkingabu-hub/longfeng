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

// ── S8 FE-04 复习三页（P07/P08/P09）──────────────────────────────────────────
import { ReviewTodayPage } from './pages/ReviewToday';
import { ReviewExecPage } from './pages/ReviewExec';
import { ReviewDonePage } from './pages/ReviewDone';

// ── 占位页（待后续 FE Agent 实现 · lazy 加载）──────────────────────────────
// 暂时用内联占位，避免破坏 build
const PlaceholderPage: React.FC<{ name: string }> = ({ name }) => (
  <div style={{ padding: 32, textAlign: 'center', color: '#8E8E93', fontSize: 14 }}>
    {name} · 待实现（FE-0x Agent）
  </div>
);

// 各占位（FE-02/03/06 已写真实页 import 在上 · 此处仅余未实现）
const WelcomeBackPage = () => <PlaceholderPage name="P-WELCOMEBACK 回流唤起 (P1)" />;
const ObserverHomePage = () => <PlaceholderPage name="P-OBSERVER 观察者主页" />;
const HomePage = () => <PlaceholderPage name="P-HOME 今日聚合首页" />;
const AuthPage = () => <PlaceholderPage name="P00 登录" />;
// ReviewTodayPage, ReviewExecPage, ReviewDonePage — imported above from real pages (S8 FE-04)
const CalendarMonthPage = () => <PlaceholderPage name="P10 日历月视图" />;
const EventDetailPage = () => <PlaceholderPage name="P11 事件详情" />;
const NotificationsPage = () => <PlaceholderPage name="P12 通知中心" />;
const MePage = () => <PlaceholderPage name="P13 设置/我的" />;

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
    {/* ── 匿名 Shell（Mood A · 无 TabBar）── */}
    <Route element={<AnonymousShell loginRoute="/auth" />}>
      <Route path="/welcome" element={<LandingPage />} />
      <Route path="/guest/capture" element={<GuestCapturePage />} />
      <Route path="/s/:shareToken" element={<SharedPage />} />
      <Route path="/welcome-back" element={<WelcomeBackPage />} />
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

      {/* Tab 4: P07 复习 + 二级 P08 / P09 */}
      <Route path="/review" element={<ReviewTodayPage />} />
      <Route path="/review/exec/:nodeId" element={<ReviewExecPage />} />
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
