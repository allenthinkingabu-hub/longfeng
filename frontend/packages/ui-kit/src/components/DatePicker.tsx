import React from 'react';

export interface DatePickerProps {
  label?: string;
  value: string;
  onChange: (value: string) => void;
  min?: string;
  max?: string;
  testIdPrefix?: string;
}

/** Sd.2 · DatePicker · 日期选择 · input[type=date]. */
export const DatePicker: React.FC<DatePickerProps> = ({
  label,
  value,
  onChange,
  min,
  max,
  testIdPrefix,
}) => (
  <label
    data-testid={testIdPrefix ? `${testIdPrefix}.datepicker` : undefined}
    style={{ display: 'flex', flexDirection: 'column', gap: 6, fontSize: 13 }}
  >
    {label && <span style={{ color: 'var(--tkn-color-text-secondary)' }}>{label}</span>}
    <input
      type="date"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      min={min}
      max={max}
      aria-label={label ?? '选择日期'}
      data-testid={testIdPrefix ? `${testIdPrefix}.datepicker.input` : undefined}
      style={{
        minHeight: 44,
        padding: '0 12px',
        border: '1px solid var(--tkn-color-border-subtle, #e5e5e5)',
        borderRadius: 'var(--tkn-radius-md, 8px)',
        background: 'var(--tkn-color-bg-primary)',
        color: 'var(--tkn-color-text-primary)',
        fontSize: 15,
      }}
    />
  </label>
);
