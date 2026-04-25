// S7 · V-S7-17 · Stryker mutation 配置 · kill rate ≥ 60%
// 跑：pnpm exec stryker run · 由于 mutate 全 src 耗时长（~10-15min）· CI nightly 跑
export default {
  packageManager: 'pnpm',
  reporters: ['html', 'clear-text', 'progress'],
  testRunner: 'vitest',
  coverageAnalysis: 'perTest',
  mutate: [
    'src/pages/**/*.{ts,tsx}',
    '!src/pages/**/*.test.{ts,tsx}',
  ],
  thresholds: {
    high: 80,
    low: 60,
    break: 60,   // < 60% → exit 1（V-S7-17 硬阈值）
  },
  htmlReporter: {
    fileName: '../../reports/stryker/s7/mutation-report.html',
  },
  jsonReporter: {
    fileName: '../../reports/stryker/s7/mutation-report.json',
  },
  timeoutMS: 30000,
  concurrency: 2,
  disableTypeChecks: true,
};
