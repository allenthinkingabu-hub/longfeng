import React from 'react';

export interface BadgeProps {
  count?: number;
  dot?: boolean;
  max?: number;
  children?: React.ReactNode;
  testIdPrefix?: string;
}

/** Sd.2 · Badge · 红点 + 计数. */
export const Badge: React.FC<BadgeProps> = ({ count, dot, max = 99, children, testIdPrefix }) => {
  const show = dot || (typeof count === 'number' && count > 0);
  const label = dot ? '' : (count ?? 0) > max ? `${max}+` : String(count ?? 0);
  return (
    <span
      data-testid={testIdPrefix ? `${testIdPrefix}.badge` : undefined}
      style={{ position: 'relative', display: 'inline-flex' }}
    >
      {children}
      {show && (
        <output
          aria-label={dot ? '新消息' : `${count} 条`}
          style={{
            position: 'absolute',
            top: -4,
            right: -4,
            minWidth: dot ? 8 : 18,
            height: dot ? 8 : 18,
            padding: dot ? 0 : '0 5px',
            borderRadius: 'var(--tkn-radius-pill, 9px)',
            background: 'var(--tkn-color-danger-default, #c0392b)',
            color: 'var(--tkn-color-white)',
            fontSize: 11,
            fontWeight: 700,
            lineHeight: '18px',
            textAlign: 'center',
            boxSizing: 'border-box',
          }}
        >
          {label}
        </output>
      )}
    </span>
  );
};
