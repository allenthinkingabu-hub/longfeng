import React from 'react';

export interface EmptyProps {
  title?: string;
  description?: string;
  action?: React.ReactNode;
  testIdPrefix?: string;
}

/** Sd.2 · Empty · 空状态. */
export const Empty: React.FC<EmptyProps> = ({
  title = '暂无数据',
  description,
  action,
  testIdPrefix,
}) => (
  <div
    role="status"
    data-testid={testIdPrefix ? `${testIdPrefix}.empty` : undefined}
    style={{
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      padding: '40px 20px',
      color: 'var(--tkn-color-text-secondary)',
      gap: 12,
      textAlign: 'center',
    }}
  >
    <div
      aria-hidden="true"
      style={{
        width: 72,
        height: 72,
        borderRadius: '50%',
        background: 'var(--tkn-color-bg-muted, #efefef)',
      }}
    />
    <div style={{ fontSize: 15, color: 'var(--tkn-color-text-primary)' }}>{title}</div>
    {description && <div style={{ fontSize: 13 }}>{description}</div>}
    {action}
  </div>
);
