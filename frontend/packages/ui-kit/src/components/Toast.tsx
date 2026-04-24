import React from 'react';

export type ToastType = 'info' | 'success' | 'warning' | 'error';

export interface ToastProps {
  type?: ToastType;
  message: string;
  action?: { label: string; onClick: () => void };
  testIdPrefix?: string;
}

const COLORS: Record<ToastType, string> = {
  info: 'var(--tkn-color-info-default, #005c99)',
  success: 'var(--tkn-color-success-default, #1a7d34)',
  warning: 'var(--tkn-color-warning-default, #b45309)',
  error: 'var(--tkn-color-danger-default, #c0392b)',
};

/** Sd.2 · Toast · role="status" · aria-live polite. */
export const Toast: React.FC<ToastProps> = ({ type = 'info', message, action, testIdPrefix }) => (
  <div
    role="status"
    aria-live="polite"
    data-testid={testIdPrefix ? `${testIdPrefix}.toast` : undefined}
    style={{
      position: 'fixed',
      bottom: 40,
      left: '50%',
      transform: 'translateX(-50%)',
      background: COLORS[type],
      color: 'var(--tkn-color-white)',
      padding: '10px 18px',
      borderRadius: 'var(--tkn-radius-pill, 24px)',
      display: 'flex',
      gap: 12,
      alignItems: 'center',
    }}
  >
    <span>{message}</span>
    {action && (
      <button
        onClick={action.onClick}
        data-testid={testIdPrefix ? `${testIdPrefix}.toast.action` : undefined}
        style={{
          background: 'var(--tkn-color-white)',
          color: 'var(--tkn-color-text-primary)',
          border: 'none',
          borderRadius: 6,
          padding: '4px 10px',
          fontWeight: 600,
          cursor: 'pointer',
          minHeight: 28,
        }}
      >
        {action.label}
      </button>
    )}
  </div>
);
