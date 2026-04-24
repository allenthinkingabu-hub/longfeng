import React from 'react';

export interface SwitchProps {
  checked: boolean;
  onChange: (checked: boolean) => void;
  disabled?: boolean;
  label?: string;
  testIdPrefix?: string;
}

/** Sd.2 · Switch · 开关 · role="switch". */
export const Switch: React.FC<SwitchProps> = ({ checked, onChange, disabled, label, testIdPrefix }) => (
  <span
    data-testid={testIdPrefix ? `${testIdPrefix}.switch` : undefined}
    style={{
      display: 'inline-flex',
      alignItems: 'center',
      gap: 8,
      cursor: disabled ? 'not-allowed' : 'pointer',
    }}
  >
    <button
      role="switch"
      aria-checked={checked}
      aria-label={label ?? '开关'}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      data-testid={testIdPrefix ? `${testIdPrefix}.switch.toggle` : undefined}
      style={{
        position: 'relative',
        width: 44,
        height: 26,
        minHeight: 26,
        border: 'none',
        borderRadius: 13,
        background: checked
          ? 'var(--tkn-color-primary-default, #0071e3)'
          : 'var(--tkn-color-text-tertiary, rgba(0,0,0,0.48))',
        cursor: disabled ? 'not-allowed' : 'pointer',
        padding: 0,
        transition: 'background 0.15s',
        opacity: disabled ? 0.6 : 1,
      }}
    >
      <span
        aria-hidden="true"
        style={{
          position: 'absolute',
          top: 2,
          left: checked ? 20 : 2,
          width: 22,
          height: 22,
          borderRadius: '50%',
          background: 'var(--tkn-color-white)',
          transition: 'left 0.15s',
          boxShadow: '0 1px 3px rgba(0,0,0,0.2)',
        }}
      />
    </button>
    {label && (
      <span
        style={{
          fontSize: 14,
          color: disabled
            ? 'var(--tkn-color-text-secondary, rgba(0,0,0,0.8))'
            : 'var(--tkn-color-text-primary, #1d1d1f)',
        }}
      >
        {label}
      </span>
    )}
  </span>
);
