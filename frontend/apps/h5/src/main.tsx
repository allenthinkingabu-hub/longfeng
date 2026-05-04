// S7 · main.tsx · H5 应用入口
// 集成 bootstrap/resolve-entry.ts 决策树（PRD §2A.3.1）
//
// 启动流程：
//   1. 同步快速判断（resolveEntrySync）→ 首帧立即渲染 Shell
//   2. 异步完整判断（resolveEntry）→ 若需要跳转则更新路由
//   3. 处理 wb:// 深链（handleWebDeeplink）

import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { I18nextProvider } from 'react-i18next';
import { App } from './App';
import { i18n } from './i18n';
import { resolveEntrySync } from './bootstrap/resolve-entry';
import { handleWebDeeplink } from './bootstrap/deeplink-router';
import '@longfeng/ui-kit/src/tokens.css';
import './app.css';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 60_000,
      refetchOnWindowFocus: false,
    },
  },
});

async function bootstrap() {
  // ── 1. 处理 wb:// 深链（若存在则先重定向到 H5 路径）
  const deeplinkPath = handleWebDeeplink();
  if (deeplinkPath && deeplinkPath !== window.location.pathname) {
    window.history.replaceState(null, '', deeplinkPath);
  }

  // ── 2. Mock 服务（dev only · VITE_DISABLE_MSW=1 强制关 · 用于 L3 hybrid 真链路测试）
  const mswDisabled = import.meta.env.VITE_DISABLE_MSW === '1' || import.meta.env.VITE_DISABLE_MSW === 'true';
  if (import.meta.env.DEV && !mswDisabled) {
    try {
      const { worker } = await import('./__mocks__/browser');
      await worker.start({ onUnhandledRequest: 'bypass' });
    } catch {
      // service workers unavailable (e2e with sw blocked)
    }
  }
  if (mswDisabled) {
    // 标记给 e2e supervisor 验证 (MSW 真关 · 不是 silent fallback)
    (window as unknown as { __lf_msw_disabled__: boolean }).__lf_msw_disabled__ = true;
  }

  // ── 3. 同步快速判断（首帧渲染决策）
  // resolveEntrySync 不做网络请求，纯本地 JWT/URL 判断
  // App 路由通过 URL 自然决定 Shell 类型（无需额外状态注入）
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const entryResult = resolveEntrySync();
  // TODO(S8): 将 entryResult 注入 context 供子组件使用（如 TabShell 获取 deeplink target）

  // ── 4. 渲染
  ReactDOM.createRoot(document.getElementById('root')!).render(
    <React.StrictMode>
      <I18nextProvider i18n={i18n}>
        <QueryClientProvider client={queryClient}>
          <BrowserRouter>
            <App />
          </BrowserRouter>
        </QueryClientProvider>
      </I18nextProvider>
    </React.StrictMode>,
  );
}

bootstrap();
