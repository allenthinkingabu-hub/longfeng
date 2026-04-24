import React from 'react';

export type CardVariant = 'default' | 'elevated';

export interface CardProps extends Omit<React.HTMLAttributes<HTMLElement>, 'className'> {
  variant?: CardVariant;
  padding?: number;
  testIdPrefix?: string;
}

/** Sd.2 · Card · 列表项 / 统计卡 / 错题卡 通用容器. */
export const Card: React.FC<CardProps> = ({ variant = 'default', padding = 16, testIdPrefix, children, ...rest }) => (
  <article
    data-testid={testIdPrefix ? `${testIdPrefix}.card` : undefined}
    style={{
      background: 'var(--tkn-color-white)',
      borderRadius: 'var(--tkn-radius-lg, 12px)',
      padding,
      boxShadow:
        variant === 'elevated'
          ? 'var(--tkn-shadow-md, 0 4px 12px rgba(0,0,0,0.08))'
          : 'var(--tkn-shadow-sm, 0 1px 3px rgba(0,0,0,0.04))',
    }}
    {...rest}
  >
    {children}
  </article>
);
