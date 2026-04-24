import React from 'react';

export interface SkeletonProps {
  width?: number | string;
  height?: number | string;
  circle?: boolean;
  testIdPrefix?: string;
}

/** Sd.2 · Skeleton · 骨架 · aria-busy. */
export const Skeleton: React.FC<SkeletonProps> = ({ width = '100%', height = 16, circle, testIdPrefix }) => (
  <div
    aria-busy="true"
    aria-live="polite"
    data-testid={testIdPrefix ? `${testIdPrefix}.skeleton` : undefined}
    style={{
      display: 'inline-block',
      width,
      height,
      borderRadius: circle ? '50%' : 'var(--tkn-radius-sm, 4px)',
      background:
        'linear-gradient(90deg, var(--tkn-color-bg-muted, #efefef) 25%, var(--tkn-color-bg-elevated, #f7f7f7) 50%, var(--tkn-color-bg-muted, #efefef) 75%)',
      backgroundSize: '200% 100%',
      animation: 'tkn-skel 1.4s ease-in-out infinite',
    }}
  />
);
