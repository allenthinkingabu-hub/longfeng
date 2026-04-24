import React from 'react';

export type TagColor = 'default' | 'primary' | 'success' | 'warning' | 'danger';

export interface TagProps {
  color?: TagColor;
  children: React.ReactNode;
  closable?: boolean;
  onClose?: () => void;
  testIdPrefix?: string;
}

const FG: Record<TagColor, string> = {
  default: 'var(--tkn-color-text-primary, #1d1d1f)',
  primary: 'var(--tkn-color-primary-link, #0066cc)',
  success: 'var(--tkn-color-success-default, #1a7d34)',
  warning: 'var(--tkn-color-warning-default, #b45309)',
  danger: 'var(--tkn-color-danger-default, #c0392b)',
};

/** Sd.2 · Tag · 标签. */
export const Tag: React.FC<TagProps> = ({ color = 'default', children, closable, onClose, testIdPrefix }) => (
  <span
    data-testid={testIdPrefix ? `${testIdPrefix}.tag` : undefined}
    style={{
      display: 'inline-flex',
      alignItems: 'center',
      gap: 4,
      padding: '2px 8px',
      borderRadius: 'var(--tkn-radius-sm, 4px)',
      fontSize: 12,
      color: FG[color],
      border: `1px solid ${FG[color]}`,
      background: 'transparent',
    }}
  >
    {children}
    {closable && (
      <button
        aria-label="移除"
        onClick={onClose}
        data-testid={testIdPrefix ? `${testIdPrefix}.tag.close` : undefined}
        style={{ background: 'transparent', border: 'none', color: 'inherit', cursor: 'pointer', padding: 0 }}
      >
        ×
      </button>
    )}
  </span>
);
