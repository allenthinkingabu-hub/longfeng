import React from 'react';
import { NavBar, Card, Badge, Empty, Avatar } from '@longfeng/ui-kit';
import { StateSwitcher, useCurrentState } from '../StateSwitcher';

const STATES = ['default', 'empty', 'unread'];

const NOTES = [
  { id: '1', title: '今日有 3 道题到期', time: '10 分钟前', unread: true },
  { id: '2', title: '连续打卡 7 天！', time: '昨天', unread: false },
  { id: '3', title: '周报：错题减少 12%', time: '3 天前', unread: false },
];

export const P13Notifications: React.FC = () => {
  const state = useCurrentState(STATES, 'default');
  const list = state === 'empty' ? [] : NOTES;
  return (
    <>
      <NavBar title="通知" testIdPrefix="notif" />
      <StateSwitcher pageId="P13" states={STATES} defaultState="default" />
      <main style={{ padding: 12, display: 'flex', flexDirection: 'column', gap: 8 }}>
        {list.length === 0 && <Empty title="暂无通知" testIdPrefix="notif" />}
        {list.map((n) => (
          <Card key={n.id} testIdPrefix={`notif.item-${n.id}`}>
            <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
              <Badge dot={n.unread && state !== 'default'} testIdPrefix={`notif.dot-${n.id}`}>
                <Avatar name="系" size={36} />
              </Badge>
              <div style={{ flex: 1 }}>
                <div style={{ fontWeight: n.unread ? 600 : 400 }}>{n.title}</div>
                <div style={{ fontSize: 12, color: 'var(--tkn-color-text-secondary)' }}>{n.time}</div>
              </div>
            </div>
          </Card>
        ))}
      </main>
    </>
  );
};
