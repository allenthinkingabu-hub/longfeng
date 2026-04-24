import React from 'react';
import { NavBar, Card, Tag, Input, Button, Banner } from '@longfeng/ui-kit';
import { StateSwitcher, useCurrentState } from '../StateSwitcher';

const STATES = ['high_conf', 'low_conf', 'manual_edit', 'saved'];

export const P05Result: React.FC = () => {
  const state = useCurrentState(STATES, 'high_conf');
  const conf = state === 'high_conf' ? 0.94 : state === 'low_conf' ? 0.58 : 0.0;
  return (
    <>
      <NavBar title="解析结果" onBack={() => history.back()} testIdPrefix="result" />
      <StateSwitcher pageId="P05" states={STATES} defaultState="high_conf" />
      <main style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 12 }}>
        {state === 'saved' && <Banner type="success" message="已保存到错题本" testIdPrefix="result" />}
        {state === 'low_conf' && <Banner type="warning" message="置信度较低 · 建议人工复核" testIdPrefix="result" />}
        <Card testIdPrefix="result.question">
          <div style={{ display: 'flex', gap: 6, marginBottom: 8 }}>
            <Tag color="primary">高二数学</Tag>
            <Tag color={conf > 0.8 ? 'success' : 'warning'}>
              置信度 {(conf * 100).toFixed(0)}%
            </Tag>
          </div>
          <div style={{ fontSize: 15, lineHeight: 1.6 }}>
            已知函数 f(x) = 2sin(ωx + π/6)，若其最小正周期为 π，求 ω 的值。
          </div>
        </Card>
        {state === 'manual_edit' && (
          <>
            <Input placeholder="修正题干" defaultValue="已知函数..." testIdPrefix="result.edit" />
            <Input placeholder="学科标签" defaultValue="高二数学" testIdPrefix="result.subject" />
          </>
        )}
        <Button variant="primary" block disabled={state === 'saved'} testIdPrefix="result.save">
          {state === 'saved' ? '已保存' : '保存到错题本'}
        </Button>
      </main>
    </>
  );
};
