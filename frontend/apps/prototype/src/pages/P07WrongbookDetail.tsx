import React from 'react';
import { NavBar, Card, Tag, Button, Banner, Divider } from '@longfeng/ui-kit';
import { StateSwitcher, useCurrentState } from '../StateSwitcher';

const STATES = ['default', 'editing', 'mastered', 'with_plan'];

export const P07WrongbookDetail: React.FC = () => {
  const state = useCurrentState(STATES, 'default');
  return (
    <>
      <NavBar title="错题详情" onBack={() => history.back()} testIdPrefix="detail" />
      <StateSwitcher pageId="P07" states={STATES} defaultState="default" />
      <main style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 12 }}>
        {state === 'mastered' && <Banner type="success" message="已掌握 · SM-2 连续 3 次 quality=good" testIdPrefix="detail" />}
        {state === 'with_plan' && <Banner type="info" message="下次复习：3 日后" testIdPrefix="detail" />}
        <Card testIdPrefix="detail.question">
          <div style={{ display: 'flex', gap: 6, marginBottom: 8 }}>
            <Tag color="primary">数学</Tag>
            <Tag color="warning">高二</Tag>
          </div>
          <div style={{ fontSize: 16, lineHeight: 1.6 }}>
            已知函数 f(x) = 2sin(ωx + π/6)，求 ω = ?
          </div>
          <Divider />
          <div style={{ fontSize: 14, color: 'var(--tkn-color-text-secondary)' }}>
            <strong>解析</strong>：最小正周期 T = 2π/ω = π，故 ω = 2
          </div>
        </Card>
        <div style={{ display: 'flex', gap: 8 }}>
          <Button variant="secondary" block testIdPrefix="detail.edit">
            {state === 'editing' ? '保存' : '编辑'}
          </Button>
          <Button variant="primary" block disabled={state === 'mastered'} testIdPrefix="detail.review">
            立即复习
          </Button>
        </div>
      </main>
    </>
  );
};
