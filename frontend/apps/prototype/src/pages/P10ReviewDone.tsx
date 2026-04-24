import React from 'react';
import { NavBar, Card, Button, Tag } from '@longfeng/ui-kit';
import { StateSwitcher, useCurrentState } from '../StateSwitcher';

const STATES = ['mastered', 'more_due', 'streak'];

export const P10ReviewDone: React.FC = () => {
  const state = useCurrentState(STATES, 'mastered');
  return (
    <>
      <NavBar title="复习完成" testIdPrefix="done" />
      <StateSwitcher pageId="P10" states={STATES} defaultState="mastered" />
      <main
        style={{
          padding: 24,
          display: 'flex',
          flexDirection: 'column',
          gap: 16,
          alignItems: 'center',
          textAlign: 'center',
        }}
      >
        <div style={{ fontSize: 80 }} aria-hidden="true">
          🎉
        </div>
        <h2 style={{ margin: 0 }}>
          {state === 'streak' ? '连续打卡 7 天！' : state === 'more_due' ? '还有题' : '完成 8 题'}
        </h2>
        <Card testIdPrefix="done.stats">
          <div style={{ display: 'flex', gap: 16, justifyContent: 'center' }}>
            <div>
              <div style={{ fontSize: 24, fontWeight: 700 }}>8</div>
              <div style={{ fontSize: 12, color: 'var(--tkn-color-text-secondary)' }}>完成</div>
            </div>
            <div>
              <div style={{ fontSize: 24, fontWeight: 700 }}>2</div>
              <div style={{ fontSize: 12, color: 'var(--tkn-color-text-secondary)' }}>已掌握</div>
            </div>
            <div>
              <div style={{ fontSize: 24, fontWeight: 700 }}>12m</div>
              <div style={{ fontSize: 12, color: 'var(--tkn-color-text-secondary)' }}>用时</div>
            </div>
          </div>
        </Card>
        {state === 'mastered' && (
          <div style={{ display: 'flex', gap: 6 }}>
            <Tag color="success">新掌握：数列求和</Tag>
            <Tag color="success">新掌握：三角函数</Tag>
          </div>
        )}
        <Button variant="primary" block testIdPrefix="done.back">
          {state === 'more_due' ? '继续复习' : '返回首页'}
        </Button>
      </main>
    </>
  );
};
