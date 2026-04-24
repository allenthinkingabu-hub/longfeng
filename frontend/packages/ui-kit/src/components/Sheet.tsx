import React from 'react';

export interface SheetProps {
  open: boolean;
  onClose: () => void;
  title?: string;
  children?: React.ReactNode;
  testIdPrefix?: string;
}

/** Sd.2 · Sheet · 底部抽屉 · role="dialog". */
export const Sheet: React.FC<SheetProps> = ({ open, onClose, title, children, testIdPrefix }) => {
  if (!open) return null;
  return (
    <div
      role="dialog"
      aria-modal="true"
      data-testid={testIdPrefix ? `${testIdPrefix}.sheet` : undefined}
      style={{
        position: 'fixed',
        inset: 0,
        background: 'rgba(0,0,0,0.45)',
        display: 'flex',
        alignItems: 'flex-end',
        zIndex: 1000,
      }}
      onClick={onClose}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        style={{
          width: '100%',
          background: 'var(--tkn-color-bg-primary)',
          borderTopLeftRadius: 'var(--tkn-radius-lg, 16px)',
          borderTopRightRadius: 'var(--tkn-radius-lg, 16px)',
          padding: 20,
          maxHeight: '80vh',
          overflow: 'auto',
        }}
      >
        {title && <div style={{ fontWeight: 600, marginBottom: 12 }}>{title}</div>}
        {children}
      </div>
    </div>
  );
};
