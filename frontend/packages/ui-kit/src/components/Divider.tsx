import React from 'react';

export interface DividerProps {
  orientation?: 'horizontal' | 'vertical';
  text?: string;
  testIdPrefix?: string;
}

/** Sd.2 · Divider · 分隔线. */
export const Divider: React.FC<DividerProps> = ({ orientation = 'horizontal', text, testIdPrefix }) => {
  if (orientation === 'vertical') {
    return (
      <span
        role="separator"
        aria-orientation="vertical"
        data-testid={testIdPrefix ? `${testIdPrefix}.divider` : undefined}
        style={{
          display: 'inline-block',
          width: 1,
          height: '1em',
          background: 'var(--tkn-color-border-subtle, #e5e5e5)',
          margin: '0 8px',
        }}
      />
    );
  }
  if (text) {
    return (
      <div
        role="separator"
        data-testid={testIdPrefix ? `${testIdPrefix}.divider` : undefined}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          color: 'var(--tkn-color-text-secondary)',
          fontSize: 12,
          margin: '16px 0',
        }}
      >
        <span style={{ flex: 1, height: 1, background: 'var(--tkn-color-border-subtle, #e5e5e5)' }} />
        {text}
        <span style={{ flex: 1, height: 1, background: 'var(--tkn-color-border-subtle, #e5e5e5)' }} />
      </div>
    );
  }
  return (
    <hr
      role="separator"
      data-testid={testIdPrefix ? `${testIdPrefix}.divider` : undefined}
      style={{
        border: 0,
        borderTop: '1px solid var(--tkn-color-border-subtle, #e5e5e5)',
        margin: '12px 0',
      }}
    />
  );
};
