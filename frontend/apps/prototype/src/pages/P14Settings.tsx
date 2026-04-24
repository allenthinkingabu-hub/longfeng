import React, { useState } from 'react';
import { NavBar, Card, Divider, Switch, Button, Avatar, Banner } from '@longfeng/ui-kit';
import { StateSwitcher, useCurrentState } from '../StateSwitcher';

const STATES = ['default', 'signed_in', 'guest'];

export const P14Settings: React.FC = () => {
  const state = useCurrentState(STATES, 'signed_in');
  const [dark, setDark] = useState(false);
  const [notif, setNotif] = useState(true);
  return (
    <>
      <NavBar title="设置" testIdPrefix="settings" />
      <StateSwitcher pageId="P14" states={STATES} defaultState="signed_in" />
      <main style={{ padding: 12, display: 'flex', flexDirection: 'column', gap: 10 }}>
        {state === 'guest' && (
          <Banner type="info" message="当前匿名体验 · 登录后数据云同步" testIdPrefix="settings" />
        )}
        <Card testIdPrefix="settings.profile">
          <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
            <Avatar name={state === 'guest' ? '访' : '王小明'} size={48} />
            <div>
              <div style={{ fontWeight: 600 }}>{state === 'guest' ? '匿名访客' : '王小明'}</div>
              <div style={{ fontSize: 12, color: 'var(--tkn-color-text-secondary)' }}>
                {state === 'guest' ? '未登录' : '138****8000'}
              </div>
            </div>
          </div>
        </Card>
        <Card testIdPrefix="settings.prefs">
          <Switch checked={dark} onChange={setDark} label="夜间模式" testIdPrefix="settings.dark" />
          <Divider />
          <Switch checked={notif} onChange={setNotif} label="复习提醒" testIdPrefix="settings.notif" />
        </Card>
        {state === 'guest' ? (
          <Button variant="primary" block testIdPrefix="settings.signin">
            登录 / 注册
          </Button>
        ) : (
          <Button variant="ghost" block testIdPrefix="settings.signout">
            退出登录
          </Button>
        )}
      </main>
    </>
  );
};
