import React from 'react';

export interface AvatarProps {
  src?: string;
  name?: string;
  size?: number;
  testIdPrefix?: string;
}

/** Sd.2 · Avatar · 头像 + fallback 首字母. */
export const Avatar: React.FC<AvatarProps> = ({ src, name = '', size = 40, testIdPrefix }) => {
  const initial = name.trim().charAt(0).toUpperCase() || '?';
  return (
    <span
      data-testid={testIdPrefix ? `${testIdPrefix}.avatar` : undefined}
      aria-label={name || '头像'}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        width: size,
        height: size,
        borderRadius: '50%',
        background: 'var(--tkn-color-bg-muted, #efefef)',
        color: 'var(--tkn-color-text-primary)',
        fontSize: size / 2.5,
        fontWeight: 600,
        overflow: 'hidden',
      }}
    >
      {src ? (
        <img src={src} alt={name} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
      ) : (
        initial
      )}
    </span>
  );
};
