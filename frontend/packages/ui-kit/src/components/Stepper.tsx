import React from 'react';

export interface StepperProps {
  value: number;
  onChange: (value: number) => void;
  min?: number;
  max?: number;
  step?: number;
  testIdPrefix?: string;
}

/** Sd.2 · Stepper · 数字步进 · 44pt 按钮. */
export const Stepper: React.FC<StepperProps> = ({
  value,
  onChange,
  min = 0,
  max = 999,
  step = 1,
  testIdPrefix,
}) => {
  const dec = () => onChange(Math.max(min, value - step));
  const inc = () => onChange(Math.min(max, value + step));
  const btn = {
    minWidth: 44,
    minHeight: 44,
    border: '1px solid var(--tkn-color-border-subtle, #e5e5e5)',
    background: 'var(--tkn-color-bg-primary)',
    color: 'var(--tkn-color-text-primary)',
    fontSize: 18,
    cursor: 'pointer',
  } as const;
  return (
    <div
      data-testid={testIdPrefix ? `${testIdPrefix}.stepper` : undefined}
      style={{ display: 'inline-flex', alignItems: 'stretch' }}
    >
      <button
        onClick={dec}
        disabled={value <= min}
        aria-label="减少"
        data-testid={testIdPrefix ? `${testIdPrefix}.stepper.dec` : undefined}
        style={{ ...btn, borderTopLeftRadius: 8, borderBottomLeftRadius: 8 }}
      >
        −
      </button>
      <input
        type="number"
        value={value}
        onChange={(e) => onChange(Number(e.target.value))}
        min={min}
        max={max}
        step={step}
        aria-label="数值"
        data-testid={testIdPrefix ? `${testIdPrefix}.stepper.input` : undefined}
        style={{
          width: 60,
          textAlign: 'center',
          border: '1px solid var(--tkn-color-border-subtle, #e5e5e5)',
          borderLeft: 'none',
          borderRight: 'none',
          background: 'var(--tkn-color-bg-primary)',
          color: 'var(--tkn-color-text-primary)',
          fontSize: 15,
        }}
      />
      <button
        onClick={inc}
        disabled={value >= max}
        aria-label="增加"
        data-testid={testIdPrefix ? `${testIdPrefix}.stepper.inc` : undefined}
        style={{ ...btn, borderTopRightRadius: 8, borderBottomRightRadius: 8 }}
      >
        +
      </button>
    </div>
  );
};
