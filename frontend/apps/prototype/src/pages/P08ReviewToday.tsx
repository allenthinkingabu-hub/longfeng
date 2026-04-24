import React from 'react';
import { NavBar, Card, Tag, Button, Empty, Skeleton } from '@longfeng/ui-kit';
import { StateSwitcher, useCurrentState } from '../StateSwitcher';

const STATES = ['has_due', 'empty', 'loading'];

export const P08ReviewToday: React.FC = () => {
  const state = useCurrentState(STATES, 'has_due');
  return (
    <>
      <NavBar title="今日复习" testIdPrefix="review-today" />
      <StateSwitcher pageId="P08" states={STATES} defaultState="has_due" />
      <main style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 12 }}>
        {state === 'loading' && <Skeleton height={120} testIdPrefix="review-today.loading" />}
        {state === 'empty' && <Empty title="今日无复习任务" description="明天再战" testIdPrefix="review-today" />}
        {state === 'has_due' && (
          <>
            <Card testIdPrefix="review-today.summary">
              <div style={{ fontSize: 32, fontWeight: 700 }}>
                8<span style={{ fontSize: 14, fontWeight: 400, marginLeft: 6 }}>题</span>
              </div>
              <div style={{ color: 'var(--tkn-color-text-secondary)' }}>预计 15 分钟</div>
            </Card>
            <Card testIdPrefix="review-today.preview">
              <div style={{ display: 'flex', gap: 6, marginBottom: 6 }}>
                <Tag color="primary">数学</Tag>
                <Tag color="danger">到期</Tag>
              </div>
              三角函数周期问题
            </Card>
            <Button variant="primary" block testIdPrefix="review-today.start">
              开始复习
            </Button>
          </>
        )}
      </main>
    </>
  );
};
