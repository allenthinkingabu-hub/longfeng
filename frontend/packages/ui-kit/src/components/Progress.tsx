import React from 'react';

export interface ProgressProps {
  value: number;
  max?: number;
  label?: string;
  testIdPrefix?: string;
}

/** Sd.2 · Progress · 进度条 · role="progressbar". */
export const Progress: React.FC<ProgressProps> = ({ value, max = 100, label, testIdPrefix }) => {
  const pct = Math.max(0, Math.min(100, (value / max) * 100));
  return (
    <div
      data-testid={testIdPrefix ? `${testIdPrefix}.progress` : undefined}
      style={{ display: 'flex', flexDirection: 'column', gap: 4 }}
    >
      {label && (
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, color: 'var(--tkn-color-text-secondary)' }}>
          <span>{label}</span>
          <span>{Math.round(pct)}%</span>
        </div>
      )}
      <div
        role="progressbar"
        aria-valuenow={value}
        aria-valuemin={0}
        aria-valuemax={max}
        aria-label={label ?? '进度'}
        style={{
          width: '100%',
          height: 8,
          borderRadius: 4,
          background: 'var(--tkn-color-bg-muted, #efefef)',
          overflow: 'hidden',
        }}
      >
        <div
          style={{
            width: `${pct}%`,
            height: '100%',
            background: 'var(--tkn-color-primary-default, #0071e3)',
            transition: 'width 0.2s',
          }}
        />
      </div>
    </div>
  );
};
