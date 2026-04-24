import React from 'react';

export interface PickerOption {
  label: string;
  value: string;
}

export interface PickerProps {
  label?: string;
  options: PickerOption[];
  value: string;
  onChange: (value: string) => void;
  testIdPrefix?: string;
}

/** Sd.2 · Picker · 选择器 · native select 实现（H5 + prototype 共用）. */
export const Picker: React.FC<PickerProps> = ({ label, options, value, onChange, testIdPrefix }) => (
  <label
    data-testid={testIdPrefix ? `${testIdPrefix}.picker` : undefined}
    style={{ display: 'flex', flexDirection: 'column', gap: 6, fontSize: 13 }}
  >
    {label && <span style={{ color: 'var(--tkn-color-text-secondary)' }}>{label}</span>}
    <select
      value={value}
      onChange={(e) => onChange(e.target.value)}
      aria-label={label ?? '选择'}
      data-testid={testIdPrefix ? `${testIdPrefix}.picker.select` : undefined}
      style={{
        minHeight: 44,
        padding: '0 12px',
        border: '1px solid var(--tkn-color-border-subtle, #e5e5e5)',
        borderRadius: 'var(--tkn-radius-md, 8px)',
        background: 'var(--tkn-color-bg-primary)',
        color: 'var(--tkn-color-text-primary)',
        fontSize: 15,
      }}
    >
      {options.map((o) => (
        <option key={o.value} value={o.value}>
          {o.label}
        </option>
      ))}
    </select>
  </label>
);
