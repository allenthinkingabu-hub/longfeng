import React from 'react';
import { NavBar, Card, Tag, Empty, Banner, Progress } from '@longfeng/ui-kit';
import { StateSwitcher, useCurrentState } from '../StateSwitcher';

const STATES = ['default', 'alert', 'empty'];

export const P19Observer: React.FC = () => {
  const state = useCurrentState(STATES, 'default');
  return (
    <>
      <NavBar title="家长视图" testIdPrefix="observer" />
      <StateSwitcher pageId="P19" states={STATES} defaultState="default" />
      <main style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 12 }}>
        {state === 'alert' && <Banner type="warning" message="孩子已连续 3 天未复习" testIdPrefix="observer" />}
        {state === 'empty' ? (
          <Empty title="暂未绑定学生账号" testIdPrefix="observer" />
        ) : (
          <>
            <Card testIdPrefix="observer.overall">
              <div style={{ marginBottom: 8 }}>本周完成率</div>
              <Progress value={state === 'alert' ? 20 : 78} label="复习到期 / 完成" testIdPrefix="observer" />
            </Card>
            <Card testIdPrefix="observer.subjects">
              <div style={{ marginBottom: 8, fontWeight: 600 }}>薄弱科目</div>
              <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                <Tag color="danger">数学 · 32%</Tag>
                <Tag color="warning">物理 · 48%</Tag>
              </div>
            </Card>
          </>
        )}
      </main>
    </>
  );
};
