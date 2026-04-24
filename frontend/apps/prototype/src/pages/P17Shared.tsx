import React from 'react';
import { NavBar, Card, Tag, Button, Banner } from '@longfeng/ui-kit';
import { StateSwitcher, useCurrentState } from '../StateSwitcher';

const STATES = ['default', 'expired'];

export const P17Shared: React.FC = () => {
  const state = useCurrentState(STATES, 'default');
  return (
    <>
      <NavBar title="分享卡片" testIdPrefix="shared" />
      <StateSwitcher pageId="P17" states={STATES} defaultState="default" />
      <main style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 12 }}>
        {state === 'expired' && <Banner type="warning" message="此分享已过期（24h TTL）" testIdPrefix="shared" />}
        <Card testIdPrefix="shared.card">
          <div style={{ fontSize: 12, color: 'var(--tkn-color-text-secondary)' }}>@王小明 分享了错题</div>
          <div style={{ display: 'flex', gap: 6, margin: '8px 0' }}>
            <Tag color="primary">数学</Tag>
            <Tag color="default">高二</Tag>
          </div>
          <div style={{ fontSize: 16, lineHeight: 1.6 }}>
            已知函数 f(x) = 2sin(ωx + π/6)，求 ω = ?
          </div>
        </Card>
        <Button variant="primary" block disabled={state === 'expired'} testIdPrefix="shared.save">
          {state === 'expired' ? '已过期' : '保存到我的错题本'}
        </Button>
      </main>
    </>
  );
};
