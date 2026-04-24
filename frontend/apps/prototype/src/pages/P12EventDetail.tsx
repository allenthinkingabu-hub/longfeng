import React from 'react';
import { NavBar, Card, Tag, Button } from '@longfeng/ui-kit';
import { StateSwitcher, useCurrentState } from '../StateSwitcher';

const STATES = ['due', 'done', 'skipped'];

export const P12EventDetail: React.FC = () => {
  const state = useCurrentState(STATES, 'due');
  const color = state === 'due' ? 'warning' : state === 'done' ? 'success' : 'default';
  return (
    <>
      <NavBar title="事件详情" onBack={() => history.back()} testIdPrefix="event" />
      <StateSwitcher pageId="P12" states={STATES} defaultState="due" />
      <main style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 12 }}>
        <Card testIdPrefix="event.info">
          <div style={{ fontSize: 13, color: 'var(--tkn-color-text-secondary)' }}>2026-04-24</div>
          <div style={{ display: 'flex', gap: 6, margin: '8px 0' }}>
            <Tag color="primary">数学</Tag>
            <Tag color={color}>{state}</Tag>
          </div>
          <div>三角函数周期问题 · 复习节点</div>
        </Card>
        {state === 'due' && (
          <Button variant="primary" block testIdPrefix="event.start">
            开始复习
          </Button>
        )}
        {state === 'skipped' && (
          <Button variant="secondary" block testIdPrefix="event.reschedule">
            重新安排
          </Button>
        )}
      </main>
    </>
  );
};
