import React from 'react';
import { NavBar, Progress, Banner, Button } from '@longfeng/ui-kit';
import { StateSwitcher, useCurrentState } from '../StateSwitcher';

const STATES = ['analyzing', 'slow', 'failed'];

export const P04Analyzing: React.FC = () => {
  const state = useCurrentState(STATES, 'analyzing');
  return (
    <>
      <NavBar title="AI 解析中" testIdPrefix="analyzing" />
      <StateSwitcher pageId="P04" states={STATES} defaultState="analyzing" />
      <main style={{ padding: 24, textAlign: 'center', display: 'flex', flexDirection: 'column', gap: 16 }}>
        <div style={{ fontSize: 72 }} aria-hidden="true">
          {state === 'failed' ? '⚠️' : '🤖'}
        </div>
        <h2 style={{ margin: 0 }}>
          {state === 'analyzing' && '正在识别题目...'}
          {state === 'slow' && '识别较慢 · 请稍候'}
          {state === 'failed' && '解析失败'}
        </h2>
        {state === 'analyzing' && <Progress value={45} testIdPrefix="analyzing" />}
        {state === 'slow' && (
          <>
            <Progress value={30} testIdPrefix="analyzing" />
            <Banner type="warning" message="LLM 响应较慢 · 已排队 15s" testIdPrefix="analyzing" />
          </>
        )}
        {state === 'failed' && (
          <>
            <Banner type="error" message="OCR 服务暂不可用 · SC-06 降级不阻塞" testIdPrefix="analyzing" />
            <Button variant="primary" testIdPrefix="analyzing.retry">
              重试
            </Button>
            <Button variant="ghost" testIdPrefix="analyzing.manual">
              手动录入
            </Button>
          </>
        )}
      </main>
    </>
  );
};
