import React from 'react';
import { NavBar, Card, Tag, Picker, Empty, Skeleton } from '@longfeng/ui-kit';
import { StateSwitcher, useCurrentState } from '../StateSwitcher';

const STATES = ['default', 'filtered', 'empty', 'loading'];

const MOCK = [
  { id: '1', subject: 'math', title: '三角函数周期', mastery: 'low' as const },
  { id: '2', subject: 'physics', title: '电磁感应', mastery: 'mid' as const },
  { id: '3', subject: 'english', title: '定语从句 who vs whom', mastery: 'high' as const },
  { id: '4', subject: 'math', title: '数列求和', mastery: 'mid' as const },
];

export const P06WrongbookList: React.FC = () => {
  const state = useCurrentState(STATES, 'default');
  const items = state === 'filtered' ? MOCK.filter((i) => i.subject === 'math') : MOCK;
  return (
    <>
      <NavBar title="错题本" testIdPrefix="book" />
      <StateSwitcher pageId="P06" states={STATES} defaultState="default" />
      <main style={{ padding: 12, display: 'flex', flexDirection: 'column', gap: 10 }}>
        <Picker
          label="筛选科目"
          value={state === 'filtered' ? 'math' : 'all'}
          onChange={() => {}}
          options={[
            { label: '全部', value: 'all' },
            { label: '数学', value: 'math' },
            { label: '物理', value: 'physics' },
            { label: '英语', value: 'english' },
          ]}
          testIdPrefix="book.filter"
        />
        {state === 'loading' && (
          <>
            <Skeleton height={72} testIdPrefix="book.loading" />
            <Skeleton height={72} testIdPrefix="book.loading" />
            <Skeleton height={72} testIdPrefix="book.loading" />
          </>
        )}
        {state === 'empty' && <Empty title="该科目无错题" testIdPrefix="book" />}
        {(state === 'default' || state === 'filtered') &&
          items.map((it) => (
            <Card key={it.id} testIdPrefix={`book.item-${it.id}`}>
              <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                <Tag color="primary">{it.subject}</Tag>
                <Tag color={it.mastery === 'low' ? 'danger' : it.mastery === 'mid' ? 'warning' : 'success'}>
                  {it.mastery}
                </Tag>
                <span style={{ marginLeft: 'auto', fontSize: 14 }}>{it.title}</span>
              </div>
            </Card>
          ))}
      </main>
    </>
  );
};
