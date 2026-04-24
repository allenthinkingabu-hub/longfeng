import React from 'react';

export interface TabBarItem {
  key: string;
  label: string;
  icon?: React.ReactNode;
  badge?: number;
}

export interface TabBarProps {
  items: TabBarItem[];
  activeKey: string;
  onChange: (key: string) => void;
  testIdPrefix?: string;
}

/** Sd.2 · TabBar · 底部导航 · role="tablist". */
export const TabBar: React.FC<TabBarProps> = ({ items, activeKey, onChange, testIdPrefix }) => (
  <div
    role="tablist"
    data-testid={testIdPrefix ? `${testIdPrefix}.tabbar` : undefined}
    style={{
      position: 'fixed',
      bottom: 0,
      left: 0,
      right: 0,
      display: 'flex',
      background: 'var(--tkn-color-bg-primary)',
      borderTop: '1px solid var(--tkn-color-border-subtle, #e5e5e5)',
      paddingBottom: 'env(safe-area-inset-bottom, 0)',
    }}
  >
    {items.map((it) => {
      const active = it.key === activeKey;
      return (
        <button
          key={it.key}
          role="tab"
          aria-selected={active}
          onClick={() => onChange(it.key)}
          data-testid={testIdPrefix ? `${testIdPrefix}.tabbar.${it.key}` : undefined}
          style={{
            flex: 1,
            minHeight: 56,
            background: 'transparent',
            border: 'none',
            color: active ? 'var(--tkn-color-primary-link, #0066cc)' : 'var(--tkn-color-text-secondary)',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 2,
            cursor: 'pointer',
            position: 'relative',
          }}
        >
          {it.icon}
          <span style={{ fontSize: 12 }}>{it.label}</span>
          {typeof it.badge === 'number' && it.badge > 0 && (
            <span
              style={{
                position: 'absolute',
                top: 8,
                right: '30%',
                background: 'var(--tkn-color-danger-default, #c0392b)',
                color: 'var(--tkn-color-white)',
                borderRadius: 10,
                fontSize: 10,
                padding: '0 5px',
                minWidth: 16,
                textAlign: 'center',
              }}
            >
              {it.badge}
            </span>
          )}
        </button>
      );
    })}
  </div>
);
