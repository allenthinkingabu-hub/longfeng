import React from 'react';
import { NavBar, Card, Skeleton, Empty, Banner, Badge, TabBar, Tag } from '@longfeng/ui-kit';
import { StateSwitcher, useCurrentState } from '../StateSwitcher';

const STATES = ['default', 'with_due', 'loading', 'error', 'empty'];

export const P02Home: React.FC = () => {
  const state = useCurrentState(STATES, 'with_due');
  return (
    <>
      <NavBar title="首页" testIdPrefix="home" />
      <StateSwitcher pageId="P02" states={STATES} defaultState="with_due" />
      <main style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 12, paddingBottom: 80 }}>
        {state === 'error' && <Banner type="error" message="网络异常 · 下拉重试" testIdPrefix="home" />}
        {state === 'loading' && (
          <>
            <Skeleton height={80} testIdPrefix="home.due" />
            <Skeleton height={120} testIdPrefix="home.stats" />
          </>
        )}
        {state === 'empty' && (
          <Empty
            title="还没有错题"
            description="点右下角相机按钮拍第一题"
            testIdPrefix="home"
          />
        )}
        {(state === 'default' || state === 'with_due') && (
          <>
            <Card testIdPrefix="home.due">
              <div style={{ fontSize: 13, color: 'var(--tkn-color-text-secondary)' }}>今日待复习</div>
              <div style={{ fontSize: 32, fontWeight: 700 }}>
                {state === 'with_due' ? 8 : 0}
                <span style={{ fontSize: 14, fontWeight: 400, marginLeft: 8 }}>题</span>
              </div>
            </Card>
            <Card testIdPrefix="home.weak">
              <div style={{ fontSize: 13, marginBottom: 8 }}>TOP3 薄弱科目</div>
              <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                <Tag color="danger">数学 · 三角函数 32%</Tag>
                <Tag color="warning">物理 · 电磁学 48%</Tag>
                <Tag color="success">英语 · 定语从句 67%</Tag>
              </div>
            </Card>
          </>
        )}
      </main>
      <TabBar
        activeKey="home"
        onChange={() => {}}
        items={[
          { key: 'home', label: '首页' },
          { key: 'book', label: '错题', badge: state === 'with_due' ? 8 : 0 },
          { key: 'plan', label: '复习' },
          { key: 'me', label: '我的' },
        ]}
        testIdPrefix="home"
      />
    </>
  );
};
