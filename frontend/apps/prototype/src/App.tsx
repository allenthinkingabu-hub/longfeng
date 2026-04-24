import React from 'react';
import { Routes, Route, Link, useNavigate } from 'react-router-dom';
import { ROUTES } from './routes';
import { P01Login } from './pages/P01Login';
import { P02Home } from './pages/P02Home';
import { P03Capture } from './pages/P03Capture';
import { P04Analyzing } from './pages/P04Analyzing';
import { P05Result } from './pages/P05Result';
import { P06WrongbookList } from './pages/P06WrongbookList';
import { P07WrongbookDetail } from './pages/P07WrongbookDetail';
import { P08ReviewToday } from './pages/P08ReviewToday';
import { P09ReviewExec } from './pages/P09ReviewExec';
import { P10ReviewDone } from './pages/P10ReviewDone';
import { P11CalendarMonth } from './pages/P11CalendarMonth';
import { P12EventDetail } from './pages/P12EventDetail';
import { P13Notifications } from './pages/P13Notifications';
import { P14Settings } from './pages/P14Settings';
import { P15Landing } from './pages/P15Landing';
import { P16GuestCapture } from './pages/P16GuestCapture';
import { P17Shared } from './pages/P17Shared';
import { P18WelcomeBack } from './pages/P18WelcomeBack';
import { P19Observer } from './pages/P19Observer';

const PAGES: Record<string, React.FC> = {
  P01: P01Login,
  P02: P02Home,
  P03: P03Capture,
  P04: P04Analyzing,
  P05: P05Result,
  P06: P06WrongbookList,
  P07: P07WrongbookDetail,
  P08: P08ReviewToday,
  P09: P09ReviewExec,
  P10: P10ReviewDone,
  P11: P11CalendarMonth,
  P12: P12EventDetail,
  P13: P13Notifications,
  P14: P14Settings,
  P15: P15Landing,
  P16: P16GuestCapture,
  P17: P17Shared,
  P18: P18WelcomeBack,
  P19: P19Observer,
};

const Index: React.FC = () => {
  const nav = useNavigate();
  return (
    <div style={{ padding: 20 }}>
      <h1 style={{ fontSize: 20, marginBottom: 12 }}>龙凤错题本 · Sd.9 原型</h1>
      <p style={{ color: 'var(--tkn-color-text-secondary)', fontSize: 13, marginBottom: 16 }}>
        19 路由 · 各页面 URL 参数 <code>?state=X</code> 切换状态 · 或使用右上角切换器
      </p>
      <ol style={{ listStyle: 'none', padding: 0, display: 'grid', gap: 8 }}>
        {ROUTES.map((r) => (
          <li key={r.id}>
            <button
              data-testid={`index.route.${r.id.toLowerCase()}`}
              onClick={() => nav(r.path)}
              style={{
                width: '100%',
                minHeight: 44,
                padding: '8px 12px',
                border: '1px solid var(--tkn-color-border-subtle, #e5e5e5)',
                background: 'var(--tkn-color-bg-primary)',
                color: 'var(--tkn-color-text-primary)',
                borderRadius: 8,
                cursor: 'pointer',
                textAlign: 'left',
                display: 'flex',
                gap: 8,
                alignItems: 'center',
              }}
            >
              <span style={{ fontWeight: 600, minWidth: 40 }}>{r.id}</span>
              <span>{r.title}</span>
              <span style={{ marginLeft: 'auto', color: 'var(--tkn-color-text-secondary)', fontSize: 12 }}>
                {r.states.length} 态
              </span>
            </button>
          </li>
        ))}
      </ol>
    </div>
  );
};

export const App: React.FC = () => (
  <>
    <div
      style={{
        position: 'sticky',
        top: 0,
        zIndex: 100,
        background: 'var(--tkn-color-bg-primary)',
        borderBottom: '1px solid var(--tkn-color-border-subtle, #e5e5e5)',
        padding: '8px 12px',
        fontSize: 12,
        display: 'flex',
        gap: 8,
        alignItems: 'center',
      }}
    >
      <Link to="/" style={{ fontWeight: 600 }}>
        ← Index
      </Link>
      <span style={{ color: 'var(--tkn-color-text-secondary)' }}>Sd.9 prototype · 19 routes</span>
    </div>
    <Routes>
      <Route path="/" element={<Index />} />
      {ROUTES.map((r) => {
        const Page = PAGES[r.id];
        return <Route key={r.id} path={r.path} element={<Page />} />;
      })}
      <Route path="*" element={<Index />} />
    </Routes>
  </>
);
