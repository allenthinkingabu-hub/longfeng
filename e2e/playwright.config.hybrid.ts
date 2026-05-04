/**
 * Playwright 配置 · L3 hybrid 真链路联调
 *
 * 跟 playwright.config.ts (mock-b 主轨) 的区别：
 *   - BASE_URL 默认 9174 (FE hybrid mode)
 *   - 跑 *-hybrid.spec.ts · 关 MSW · 走真 BE :9080 → :9086
 *   - 单 worker 顺序跑（DB 状态串扰风险）
 *   - 全程 trace + screenshot + video（hybrid 是稀缺验收 · 留全证据）
 *
 * 用法：
 *   BASE_URL=http://localhost:9174 \
 *     pnpm playwright test --config=playwright.config.hybrid.ts
 *
 * 环境前置：
 *   1. Docker 中间件起（PG 15432 / Redis 16379）
 *   2. BE Gateway :9080 + auth-service :9086 已起
 *   3. FE pnpm dev --mode hybrid --port 9174 已起
 */
import { defineConfig, devices } from '@playwright/test';

const BASE_URL = process.env.BASE_URL ?? 'http://localhost:9174';

export default defineConfig({
  testDir: './specs',
  testMatch: /.*-hybrid\.spec\.ts/,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  retries: 0,
  workers: 1,
  fullyParallel: false,
  reporter: [
    ['list'],
    ['html', { outputFolder: 'reports/hybrid-html', open: 'never' }],
    ['json', { outputFile: 'reports/hybrid.json' }],
  ],
  outputDir: 'reports/hybrid-artifacts',

  use: {
    baseURL: BASE_URL,
    trace: 'on',
    screenshot: 'on',
    video: 'on',
    actionTimeout: 15_000,
    navigationTimeout: 20_000,
    viewport: { width: 393, height: 852 },
    deviceScaleFactor: 3,
    isMobile: true,
    hasTouch: true,
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
  },

  projects: [
    {
      name: 'h5-iphone-15-pro-hybrid',
      use: { ...devices['iPhone 15 Pro'], viewport: { width: 393, height: 852 } },
    },
  ],

  metadata: {
    track: 'hybrid',
    baseUrl: BASE_URL,
    apiGateway: 'http://localhost:9880',
    authService: 'http://localhost:9086',
  },
});
