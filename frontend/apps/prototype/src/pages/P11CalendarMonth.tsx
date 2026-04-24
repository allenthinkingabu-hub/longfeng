import React from 'react';
import { NavBar, Badge, Empty, DatePicker } from '@longfeng/ui-kit';
import { StateSwitcher, useCurrentState } from '../StateSwitcher';

const STATES = ['default', 'dense', 'empty'];

export const P11CalendarMonth: React.FC = () => {
  const state = useCurrentState(STATES, 'default');
  const days = Array.from({ length: 30 }, (_, i) => i + 1);
  const hasEvent = (d: number) => {
    if (state === 'empty') return 0;
    if (state === 'dense') return d % 2 === 0 ? (d % 4) + 1 : 0;
    return [3, 8, 15, 22, 28].includes(d) ? 1 : 0;
  };
  return (
    <>
      <NavBar title="复习日历" testIdPrefix="cal" />
      <StateSwitcher pageId="P11" states={STATES} defaultState="default" />
      <main style={{ padding: 16 }}>
        <DatePicker label="月份" value="2026-04-24" onChange={() => {}} testIdPrefix="cal.month" />
        {state === 'empty' ? (
          <Empty title="本月无复习记录" testIdPrefix="cal" />
        ) : (
          <div
            data-testid="cal.grid"
            style={{
              marginTop: 12,
              display: 'grid',
              gridTemplateColumns: 'repeat(7, 1fr)',
              gap: 4,
            }}
          >
            {days.map((d) => (
              <Badge key={d} count={hasEvent(d)} testIdPrefix={`cal.day-${d}`}>
                <span
                  style={{
                    display: 'inline-flex',
                    width: 40,
                    height: 40,
                    alignItems: 'center',
                    justifyContent: 'center',
                    borderRadius: 6,
                    background: hasEvent(d) ? 'var(--tkn-color-bg-light)' : 'transparent',
                  }}
                >
                  {d}
                </span>
              </Badge>
            ))}
          </div>
        )}
      </main>
    </>
  );
};
