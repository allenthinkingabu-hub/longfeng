import React from 'react';
import { NavBar, Button, Banner, Modal } from '@longfeng/ui-kit';
import { StateSwitcher, useCurrentState } from '../StateSwitcher';

const STATES = ['ready', 'capturing', 'preview', 'upgrade_prompt'];

export const P16GuestCapture: React.FC = () => {
  const state = useCurrentState(STATES, 'ready');
  return (
    <>
      <NavBar title="拍照试用" onBack={() => history.back()} testIdPrefix="guest-capture" />
      <StateSwitcher pageId="P16" states={STATES} defaultState="ready" />
      <main style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 12 }}>
        <Banner type="info" message="匿名体验 · 最多 3 题 · 登录后无限" testIdPrefix="guest-capture" />
        <div
          data-testid="guest-capture.viewfinder"
          style={{
            aspectRatio: '3/4',
            background: '#222',
            color: '#fff',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            borderRadius: 12,
          }}
        >
          {state === 'ready' && '点击快门'}
          {state === 'capturing' && '📸'}
          {state === 'preview' && '(预览)'}
        </div>
        {state === 'ready' && (
          <Button variant="primary" block testIdPrefix="guest-capture.shutter">
            拍照
          </Button>
        )}
        <Modal
          open={state === 'upgrade_prompt'}
          onClose={() => {}}
          title="已达 3 题上限"
          footer={
            <>
              <Button variant="secondary" testIdPrefix="guest-capture.later">
                稍后
              </Button>
              <Button variant="primary" testIdPrefix="guest-capture.signup">
                注册（免费）
              </Button>
            </>
          }
          testIdPrefix="guest-capture.upgrade"
        >
          注册账号即可继续拍照 · 匿名数据自动合并（S11 落地后）
        </Modal>
      </main>
    </>
  );
};
