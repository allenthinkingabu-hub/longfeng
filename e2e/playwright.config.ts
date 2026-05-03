/**
 * S9 · Playwright 配置 · QA Agent 主战场
 *
 * 三轨独立运行（plan §6.3）：
 *   - B 轨 mock-b   · MSW 拦截 · 每 PR · 必过 · `pnpm e2e:mock-b`
 *   - C 轨 vrt      · pixel diff vs mockup · 每 commit · `pnpm e2e:vrt`
 *   - A 轨 e2e-a    · 真 staging · 每 sprint · `pnpm e2e:e2e-a`
 *
 * 视口：iPhone 15 Pro 393×852（H5 默认 mobile）
 * baseURL：BASE_URL env 或 http://localhost:5173 (vite dev)
 *
 * 红线（plan §6.5）：禁 localStorage 跳登录 · 禁直 fetch · 禁关 SSE · 必 networkidle
 */
import { defineConfig, devices } from '@playwright/test';

const BASE_URL = process.env.BASE_URL ?? 'http://localhost:5173';
const TRACK    = process.env.E2E_TRACK ?? 'mock-b'; // mock-b | vrt | e2e-a

export default defineConfig({
  testDir: './specs',
  timeout: 30_000,
  expect: {
    timeout: 5_000,
    // VRT C 轨：阈值 1%（plan §3.2 + §6.3）
    toHaveScreenshot: { maxDiffPixelRatio: 0.01, threshold: 0.2 },
  },
  // plan §6.5 · "重试 ≤ 1" · CI 上允许 1 次重跑兜 flaky
  retries: process.env.CI ? 1 : 0,
  workers: process.env.CI ? 2 : undefined,
  fullyParallel: false,
  reporter: process.env.CI
    ? [['html', { outputFolder: 'reports/html' }], ['github'], ['junit', { outputFile: 'reports/junit.xml' }]]
    : [['list']],
  outputDir: 'reports/artifacts',

  use: {
    baseURL: BASE_URL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: process.env.CI ? 'retain-on-failure' : 'off',
    actionTimeout: 10_000,
    navigationTimeout: 15_000,
    // iPhone 15 Pro · plan §6 + spec §11
    viewport: { width: 393, height: 852 },
    deviceScaleFactor: 3,
    isMobile: true,
    hasTouch: true,
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
    // X-Timezone header（C9 红线）由 fixture/loginAs* 注入
  },

  projects: [
    {
      name: 'h5-iphone-15-pro',
      testMatch: /.*\.spec\.ts/,
      use: {
        ...devices['iPhone 15 Pro'],
        viewport: { width: 393, height: 852 },
      },
    },
    // 注：微信小程序 e2e 用 miniprogram-automator，不在本 config
    // 见 e2e/miniprogram/* · 由 `pnpm e2e:miniapp` 单独跑
  ],

  // metadata 注入用于 reporter 区分轨道
  metadata: {
    track: TRACK,
    baseUrl: BASE_URL,
  },
});
