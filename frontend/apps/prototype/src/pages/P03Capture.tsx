import React from 'react';
import { NavBar, Button, Progress, Banner } from '@longfeng/ui-kit';
import { StateSwitcher, useCurrentState } from '../StateSwitcher';

const STATES = ['ready', 'capturing', 'preview', 'uploading', 'error'];

export const P03Capture: React.FC = () => {
  const state = useCurrentState(STATES, 'ready');
  return (
    <>
      <NavBar title="拍照录入" onBack={() => history.back()} testIdPrefix="capture" />
      <StateSwitcher pageId="P03" states={STATES} defaultState="ready" />
      <main style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 12 }}>
        {state === 'error' && <Banner type="error" message="上传失败 · 点击重试" testIdPrefix="capture" />}
        <div
          data-testid="capture.viewfinder"
          style={{
            aspectRatio: '3/4',
            background: state === 'preview' ? 'linear-gradient(45deg,#ccc,#999)' : '#222',
            color: '#fff',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            borderRadius: 12,
          }}
        >
          {state === 'ready' && '点击快门拍照'}
          {state === 'capturing' && '📸 拍摄中...'}
          {state === 'preview' && '(预览图)'}
          {state === 'uploading' && '⬆ 上传中'}
        </div>
        {state === 'uploading' && <Progress value={65} label="上传进度" testIdPrefix="capture" />}
        {state === 'ready' && (
          <Button variant="primary" block testIdPrefix="capture.shutter">
            拍照
          </Button>
        )}
        {state === 'preview' && (
          <div style={{ display: 'flex', gap: 8 }}>
            <Button variant="secondary" block testIdPrefix="capture.retake">
              重拍
            </Button>
            <Button variant="primary" block testIdPrefix="capture.confirm">
              确认上传
            </Button>
          </div>
        )}
      </main>
    </>
  );
};
