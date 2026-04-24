import React from 'react';
import { useSearchParams } from 'react-router-dom';

export interface StateSwitcherProps {
  pageId: string;
  states: string[];
  defaultState?: string;
}

/** Sd.9 · state switcher · URL ?state=X 单向绑定 · 右上角悬浮 · data-testid 方便 Playwright 切换. */
export const StateSwitcher: React.FC<StateSwitcherProps> = ({ pageId, states, defaultState }) => {
  const [params, setParams] = useSearchParams();
  const active = params.get('state') ?? defaultState ?? states[0];
  return (
    <div className="state-switcher" data-testid={`${pageId.toLowerCase()}.state-switcher`}>
      <div style={{ opacity: 0.7 }}>state</div>
      {states.map((s) => (
        <button
          key={s}
          data-active={s === active}
          data-testid={`${pageId.toLowerCase()}.state.${s}`}
          onClick={() => {
            const next = new URLSearchParams(params);
            next.set('state', s);
            setParams(next, { replace: true });
          }}
        >
          {s}
        </button>
      ))}
    </div>
  );
};

export function useCurrentState(states: string[], defaultState?: string): string {
  const [params] = useSearchParams();
  const v = params.get('state');
  if (v && states.includes(v)) return v;
  return defaultState ?? states[0];
}
