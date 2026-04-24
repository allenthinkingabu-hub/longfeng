import React from 'react';
import { Button, Card } from '@longfeng/ui-kit';
import { StateSwitcher, useCurrentState } from '../StateSwitcher';

const STATES = ['default'];

export const P15Landing: React.FC = () => {
  useCurrentState(STATES, 'default');
  return (
    <>
      <StateSwitcher pageId="P15" states={STATES} />
      <main
        style={{
          padding: 32,
          minHeight: '100vh',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
          gap: 20,
          textAlign: 'center',
        }}
      >
        <div style={{ fontSize: 72 }} aria-hidden="true">
          📚
        </div>
        <h1 style={{ margin: 0, fontSize: 28 }}>龙凤错题本</h1>
        <p style={{ color: 'var(--tkn-color-text-secondary)', lineHeight: 1.6 }}>
          拍照录入 · AI 解析 · 艾宾浩斯智能复习
        </p>
        <Card testIdPrefix="landing.highlight">
          <div style={{ textAlign: 'left', display: 'flex', flexDirection: 'column', gap: 6 }}>
            <div>✅ 3s 内 AI 识别题目</div>
            <div>✅ SM-2 算法自动排期</div>
            <div>✅ 错题减少 12%（30 天平均）</div>
          </div>
        </Card>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          <Button variant="primary" block testIdPrefix="landing.try">
            匿名试用
          </Button>
          <a href="#/p01-login" data-testid="landing.signin">
            已有账号？登录
          </a>
        </div>
      </main>
    </>
  );
};
