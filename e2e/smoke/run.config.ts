/**
 * Smoke 子集 runner 配置（可被 ci.yml 引用）
 *
 * 6 + 1 个 SC tag · 总 ≤ 8 min · plan §5.S9 出口门禁
 */
export const SMOKE_TAGS = [
  '@sc-01',
  '@sc-02',
  '@sc-05',
  '@sc-11',
  '@sc-12',
  '@sc-13',
  '@sc-16',
] as const;

export const SMOKE_GREP = `(${SMOKE_TAGS.join('|')})`;

export const SMOKE_BUDGET_MS = 8 * 60 * 1000;
