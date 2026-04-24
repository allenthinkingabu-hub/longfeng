import React from 'react';

export type ButtonVariant = 'primary' | 'secondary' | 'pill-link' | 'ghost' | 'danger' | 'icon-only';
export type ButtonSize = 'sm' | 'md' | 'lg';

export interface ButtonProps extends Omit<React.ButtonHTMLAttributes<HTMLButtonElement>, 'className'> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
  block?: boolean;
  testIdPrefix?: string;
}

const STYLES: Record<ButtonVariant, React.CSSProperties> = {
  primary: { background: 'var(--tkn-color-primary-default, #0071e3)', color: 'var(--tkn-color-white)' },
  secondary: { background: 'var(--tkn-color-button-default-light)', color: 'var(--tkn-color-text-primary)' },
  'pill-link': { background: 'transparent', color: 'var(--tkn-color-primary-default, #0071e3)', textDecoration: 'underline' },
  ghost: { background: 'transparent', color: 'var(--tkn-color-text-primary)', border: '1px solid var(--tkn-color-text-tertiary)' },
  danger: { background: 'var(--tkn-color-danger-default, #c0392b)', color: 'var(--tkn-color-white)' },
  'icon-only': { background: 'transparent', padding: '8px', color: 'var(--tkn-color-text-primary)' },
};

const SIZE_PAD: Record<ButtonSize, string> = { sm: '6px 12px', md: '10px 18px', lg: '14px 24px' };

/** Sd.2 · Button · 6 variants · 6 states · testIdPrefix prop. */
export const Button: React.FC<ButtonProps> = ({
  variant = 'primary',
  size = 'md',
  loading = false,
  block = false,
  disabled,
  testIdPrefix,
  children,
  ...rest
}) => {
  const style: React.CSSProperties = {
    ...STYLES[variant],
    padding: variant === 'icon-only' ? '8px' : SIZE_PAD[size],
    border: STYLES[variant].border ?? 'none',
    borderRadius: 'var(--tkn-radius-md, 8px)',
    fontWeight: 500,
    cursor: disabled || loading ? 'not-allowed' : 'pointer',
    opacity: disabled ? 0.5 : 1,
    width: block ? '100%' : 'auto',
    display: 'inline-flex',
    alignItems: 'center',
    gap: 6,
    minHeight: 44, // a11y 触达区 ≥ 44pt
  };
  return (
    <button
      data-testid={testIdPrefix ? `${testIdPrefix}.btn` : undefined}
      disabled={disabled || loading}
      aria-busy={loading}
      style={style}
      {...rest}
    >
      {loading && <span aria-hidden>⏳</span>}
      {children}
    </button>
  );
};
