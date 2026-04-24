import React from 'react';

export type BannerType = 'info' | 'success' | 'warning' | 'error';

export interface BannerProps {
  type?: BannerType;
  title?: string;
  message: string;
  closable?: boolean;
  onClose?: () => void;
  testIdPrefix?: string;
}

const BG: Record<BannerType, string> = {
  info: 'var(--tkn-color-bg-info, #e8f0fe)',
  success: 'var(--tkn-color-bg-success, #e8f5e9)',
  warning: 'var(--tkn-color-bg-warning, #fff7e6)',
  error: 'var(--tkn-color-bg-error, #fdecec)',
};

/** Sd.2 · Banner · 条幅通知 · role="alert". */
export const Banner: React.FC<BannerProps> = ({
  type = 'info',
  title,
  message,
  closable,
  onClose,
  testIdPrefix,
}) => (
  <div
    role="alert"
    data-testid={testIdPrefix ? `${testIdPrefix}.banner` : undefined}
    style={{
      background: BG[type],
      padding: '10px 14px',
      borderRadius: 'var(--tkn-radius-md, 8px)',
      display: 'flex',
      alignItems: 'flex-start',
      gap: 10,
    }}
  >
    <div style={{ flex: 1, color: 'var(--tkn-color-text-primary)' }}>
      {title && <div style={{ fontWeight: 600, marginBottom: 2 }}>{title}</div>}
      <div style={{ fontSize: 14 }}>{message}</div>
    </div>
    {closable && (
      <button
        aria-label="关闭"
        onClick={onClose}
        data-testid={testIdPrefix ? `${testIdPrefix}.banner.close` : undefined}
        style={{
          background: 'transparent',
          border: 'none',
          cursor: 'pointer',
          color: 'var(--tkn-color-text-secondary)',
          minWidth: 28,
          minHeight: 28,
        }}
      >
        ×
      </button>
    )}
  </div>
);
