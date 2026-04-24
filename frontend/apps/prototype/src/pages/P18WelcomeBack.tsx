import React from 'react';
import { Card, Button, Banner } from '@longfeng/ui-kit';
import { StateSwitcher, useCurrentState } from '../StateSwitcher';

const STATES = ['default', 'streak_broken'];

export const P18WelcomeBack: React.FC = () => {
  const state = useCurrentState(STATES, 'default');
  return (
    <>
      <StateSwitcher pageId="P18" states={STATES} defaultState="default" />
      <main
        style={{
          padding: 32,
          minHeight: '100vh',
          display: 'flex',
          flexDirection: 'column',
          gap: 16,
          textAlign: 'center',
          justifyContent: 'center',
        }}
      >
        <div style={{ fontSize: 72 }} aria-hidden="true">
          👋
        </div>
        <h1 style={{ margin: 0, fontSize: 28 }}>欢迎回来</h1>
        {state === 'streak_broken' && (
          <Banner type="warning" message="连续打卡已中断 · 重新出发" testIdPrefix="welcomeback" />
        )}
        <Card testIdPrefix="welcomeback.stats">
          <div style={{ fontSize: 28, fontWeight: 700 }}>
            {state === 'streak_broken' ? '0' : '12'} 天
          </div>
          <div style={{ fontSize: 12, color: 'var(--tkn-color-text-secondary)' }}>连续复习</div>
        </Card>
        <Card testIdPrefix="welcomeback.pending">
          <div>今日待复习：5 题</div>
        </Card>
        <Button variant="primary" block testIdPrefix="welcomeback.start">
          开始复习
        </Button>
      </main>
    </>
  );
};
