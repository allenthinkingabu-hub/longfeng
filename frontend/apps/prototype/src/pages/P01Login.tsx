import React, { useState } from 'react';
import { NavBar, Input, Button, Banner } from '@longfeng/ui-kit';
import { StateSwitcher, useCurrentState } from '../StateSwitcher';

const STATES = ['default', 'typing', 'loading', 'error', 'success', 'disabled'];

export const P01Login: React.FC = () => {
  const state = useCurrentState(STATES, 'default');
  const [phone, setPhone] = useState(state === 'typing' ? '138' : state === 'success' ? '13800138000' : '');
  return (
    <>
      <NavBar title="登录 / 注册" testIdPrefix="login" />
      <StateSwitcher pageId="P01" states={STATES} defaultState="default" />
      <main style={{ padding: 24, display: 'flex', flexDirection: 'column', gap: 16 }}>
        {state === 'error' && <Banner type="error" message="手机号格式不正确" testIdPrefix="login" />}
        <h2 style={{ fontSize: 24, margin: 0 }}>欢迎回来</h2>
        <Input
          placeholder="请输入手机号"
          value={phone}
          onChange={(e) => setPhone(e.target.value)}
          error={state === 'error' ? '手机号需 11 位' : undefined}
          disabled={state === 'loading' || state === 'disabled'}
          testIdPrefix="login.phone"
        />
        <Input placeholder="6 位验证码" disabled={state !== 'success'} testIdPrefix="login.code" />
        <Button
          variant="primary"
          block
          loading={state === 'loading'}
          disabled={state === 'default' || state === 'disabled'}
          testIdPrefix="login.send-code"
        >
          {state === 'success' ? '60s' : '发送验证码'}
        </Button>
        <a href="#/p15-landing" style={{ fontSize: 13 }}>
          匿名体验
        </a>
      </main>
    </>
  );
};
