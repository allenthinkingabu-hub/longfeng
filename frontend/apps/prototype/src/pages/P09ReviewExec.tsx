import React from 'react';
import { NavBar, Card, Button, Progress, Banner } from '@longfeng/ui-kit';
import { StateSwitcher, useCurrentState } from '../StateSwitcher';

const STATES = ['q1', 'q2', 'q3', 'feedback'];

export const P09ReviewExec: React.FC = () => {
  const state = useCurrentState(STATES, 'q1');
  const idx = state === 'q1' ? 1 : state === 'q2' ? 2 : state === 'q3' ? 3 : 3;
  return (
    <>
      <NavBar title={`复习 ${idx} / 8`} onBack={() => history.back()} testIdPrefix="exec" />
      <StateSwitcher pageId="P09" states={STATES} defaultState="q1" />
      <main style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 12 }}>
        <Progress value={(idx / 8) * 100} label={`进度`} testIdPrefix="exec" />
        {state === 'feedback' && <Banner type="success" message="答对了 · quality=good" testIdPrefix="exec" />}
        <Card testIdPrefix="exec.question">
          <div style={{ fontSize: 16, lineHeight: 1.6 }}>
            已知函数 f(x) = 2sin(ωx + π/6)，若其最小正周期为 π，求 ω 的值。
          </div>
        </Card>
        {state !== 'feedback' ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <Button variant="secondary" testIdPrefix="exec.forgot">
              🤷 忘了 (quality=1)
            </Button>
            <Button variant="secondary" testIdPrefix="exec.hard">
              🤔 有点难 (quality=3)
            </Button>
            <Button variant="primary" testIdPrefix="exec.good">
              ✅ 会做 (quality=4)
            </Button>
            <Button variant="primary" testIdPrefix="exec.perfect">
              🌟 秒杀 (quality=5)
            </Button>
          </div>
        ) : (
          <Button variant="primary" block testIdPrefix="exec.next">
            下一题
          </Button>
        )}
      </main>
    </>
  );
};
