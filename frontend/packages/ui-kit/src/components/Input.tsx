import React from 'react';

export interface InputProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'className'> {
  error?: string;
  testIdPrefix?: string;
}

/** Sd.2 · Input · 文本/数字/手机号/密码 · error state 显示红色边框 + 错误文案. */
export const Input: React.FC<InputProps> = ({ error, testIdPrefix, disabled, ...rest }) => {
  const borderColor = error ? 'var(--tkn-color-danger-default, #c0392b)' : 'var(--tkn-color-text-tertiary)';
  return (
    <span style={{ display: 'inline-flex', flexDirection: 'column', gap: 4 }}>
      <input
        data-testid={testIdPrefix ? `${testIdPrefix}.input` : undefined}
        disabled={disabled}
        aria-invalid={Boolean(error)}
        style={{
          padding: '10px 14px',
          borderRadius: 'var(--tkn-radius-sm, 6px)',
          border: `1px solid ${borderColor}`,
          background: disabled ? 'var(--tkn-color-button-default-light)' : 'var(--tkn-color-white)',
          color: 'var(--tkn-color-text-primary)',
          fontSize: 16,
          minHeight: 44,
        }}
        {...rest}
      />
      {error && (
        <span role="alert" style={{ color: 'var(--tkn-color-danger-default, #c0392b)', fontSize: 12 }}>
          {error}
        </span>
      )}
    </span>
  );
};
