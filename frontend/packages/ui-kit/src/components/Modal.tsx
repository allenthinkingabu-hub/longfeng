import React, { useEffect } from 'react';

export interface ModalProps {
  open: boolean;
  onClose: () => void;
  title?: string;
  children?: React.ReactNode;
  footer?: React.ReactNode;
  testIdPrefix?: string;
}

/** Sd.2 · Modal · role="dialog" aria-modal · Esc 关闭. */
export const Modal: React.FC<ModalProps> = ({ open, onClose, title, children, footer, testIdPrefix }) => {
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby={title ? `${testIdPrefix ?? 'modal'}-title` : undefined}
      data-testid={testIdPrefix ? `${testIdPrefix}.modal` : undefined}
      style={{
        position: 'fixed',
        inset: 0,
        background: 'rgba(0,0,0,0.45)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 1000,
      }}
      onClick={onClose}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        style={{
          background: 'var(--tkn-color-bg-primary)',
          borderRadius: 'var(--tkn-radius-md, 12px)',
          minWidth: 280,
          maxWidth: '90vw',
          maxHeight: '85vh',
          display: 'flex',
          flexDirection: 'column',
        }}
      >
        {title && (
          <div
            id={`${testIdPrefix ?? 'modal'}-title`}
            style={{
              padding: '16px 20px',
              fontWeight: 600,
              borderBottom: '1px solid var(--tkn-color-border-subtle, #e5e5e5)',
            }}
          >
            {title}
          </div>
        )}
        <div style={{ padding: 20, overflow: 'auto', flex: 1 }}>{children}</div>
        {footer && (
          <div
            style={{
              padding: '12px 20px',
              borderTop: '1px solid var(--tkn-color-border-subtle, #e5e5e5)',
              display: 'flex',
              justifyContent: 'flex-end',
              gap: 8,
            }}
          >
            {footer}
          </div>
        )}
      </div>
    </div>
  );
};
