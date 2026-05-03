// S7 · AnonymousShell.tsx
// 匿名 Shell · 无登录态 · 承载路由：P-LANDING / P-GUEST-CAPTURE / P-SHARED / P-OBSERVER(P1)
//
// CLAUDE.md 设计实施铁律:
//   - Mood A (P-LANDING/P-GUEST/P-WELCOMEBACK): 深蓝 hero + overlap + 无 TabBar
//   - 匿名 Shell 禁止 Tab Bar（PRD §2A.3.1 §8 硬性规则）
//   - Logo 左上 · 登录 pill 右上 · 底部全屏浮层 CTA（各页面自管）
// STYLE-TRUTH §3 Mood A 参考 archive: 14_landing.html

import React, { Suspense } from 'react';
import { Outlet, useNavigate } from 'react-router-dom';
import s from './AnonymousShell.module.css';

// ── 类型 ──────────────────────────────────────────────────────────────────────

interface AnonymousShellProps {
  /** 登录按钮点击目标路由（默认 '/auth'） */
  loginRoute?: string;
}

// ── 组件 ──────────────────────────────────────────────────────────────────────

/**
 * AnonymousShell · 匿名态应用骨架
 *
 * 结构（从上到下，对应 STYLE-TRUTH §4.3 Mood A）：
 *   Hero 深蓝渐变区（240px）
 *     ├── StatusBar 占位（54px · 刘海）
 *     ├── AnonNav（Logo + 登录 pill）
 *     ├── 3 blob 装饰
 *     └── heroContent slot（可选 · 各页面自定义）
 *   Outlet（overlap 26px 圆角白底）
 *
 * 使用方式（App.tsx）：
 *   <Route element={<AnonymousShell />}>
 *     <Route path="/welcome" element={<LandingPage />} />
 *     <Route path="/guest/capture" element={<GuestCapturePage />} />
 *     <Route path="/s/:shareToken" element={<SharedPage />} />
 *   </Route>
 */
export const AnonymousShell: React.FC<AnonymousShellProps> = ({
  loginRoute = '/auth',
}) => {
  const navigate = useNavigate();

  const handleLogin = () => {
    navigate(loginRoute);
  };

  return (
    <div
      className={s.root}
      data-testid="anon-shell"
      data-mood="A"
    >
      {/* Hero 深蓝渐变背景 (Mood A) */}
      {/* 注：hero 容器不能整体 aria-hidden，因为内部 nav 包含 focusable
          的登录按钮（axe rule: aria-hidden-focus serious）。仅装饰性子节点
          单独标 aria-hidden。 */}
      <div className={s.hero}>
        {/* Blob 3: 粉 · 中央偏左 */}
        <div className={s.heroBlobPink} aria-hidden="true" />

        {/* 导航栏（Hero 内 · 绝对定位）*/}
        <nav
          className={s.nav}
          data-testid="anon-shell-nav"
          aria-label="匿名导航"
        >
          {/* Logo 区 */}
          <a
            href="/welcome"
            className={s.logoArea}
            data-testid="anon-shell-logo"
            aria-label="AI 错题本 · 返回首页"
          >
            <div className={s.logoBlock} aria-hidden="true">
              <span className={s.logoText} aria-hidden="true">AI</span>
            </div>
            <span className={s.appName}>错题本</span>
          </a>

          {/* 登录 Pill */}
          <button
            className={s.loginPill}
            onClick={handleLogin}
            data-testid="anon-shell-login-btn"
            aria-label="登录账号"
            type="button"
          >
            登录
          </button>
        </nav>
      </div>

      {/* 内容 Outlet（overlap 盖在 hero 上）*/}
      <main
        className={s.outlet}
        data-testid="anon-shell-outlet"
        role="main"
        aria-label="页面内容"
      >
        <Suspense fallback={<AnonymousShellLoading />}>
          <Outlet />
        </Suspense>
      </main>
    </div>
  );
};

// ── Loading 占位 ──────────────────────────────────────────────────────────────

const AnonymousShellLoading: React.FC = () => (
  <div
    style={{
      padding: '32px 16px',
      textAlign: 'center',
      color: '#8E8E93',
      fontSize: 13,
    }}
    role="status"
    aria-live="polite"
    aria-label="页面加载中"
  >
    {/* 骨架占位 · 无 spinner（减少动效） */}
  </div>
);

export default AnonymousShell;
