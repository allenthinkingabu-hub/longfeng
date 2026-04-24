import React from 'react';

export interface NavBarProps {
  title: string;
  onBack?: () => void;
  right?: React.ReactNode;
  testIdPrefix?: string;
}

/** Sd.2 · NavBar · 顶部导航 · 44pt 高度. */
export const NavBar: React.FC<NavBarProps> = ({ title, onBack, right, testIdPrefix }) => (
  <div
    data-testid={testIdPrefix ? `${testIdPrefix}.navbar` : undefined}
    style={{
      position: 'sticky',
      top: 0,
      display: 'flex',
      alignItems: 'center',
      minHeight: 44,
      padding: '0 12px',
      background: 'var(--tkn-color-bg-primary)',
      borderBottom: '1px solid var(--tkn-color-border-subtle, #e5e5e5)',
      zIndex: 10,
    }}
  >
    <div style={{ width: 60, display: 'flex', alignItems: 'center' }}>
      {onBack && (
        <button
          onClick={onBack}
          aria-label="返回"
          data-testid={testIdPrefix ? `${testIdPrefix}.navbar.back` : undefined}
          style={{
            background: 'transparent',
            border: 'none',
            color: 'var(--tkn-color-text-primary)',
            fontSize: 16,
            cursor: 'pointer',
            minWidth: 44,
            minHeight: 44,
          }}
        >
          ‹ 返回
        </button>
      )}
    </div>
    <div
      style={{
        flex: 1,
        textAlign: 'center',
        fontSize: 17,
        fontWeight: 600,
        color: 'var(--tkn-color-text-primary)',
      }}
    >
      {title}
    </div>
    <div style={{ width: 60, display: 'flex', justifyContent: 'flex-end' }}>{right}</div>
  </div>
);
