import { defineConfig, devices } from '@playwright/test';

// S0 skeleton · Phase S9 将补齐 15 SC 用例 + fixtures
export default defineConfig({
  testDir: './specs',
  timeout: 30_000,
  retries: process.env.CI ? 2 : 0,
  reporter: process.env.CI ? [['html'], ['github']] : [['list']],
  use: {
    baseURL: process.env.BASE_URL ?? 'http://localhost:5173',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure'
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } }
    // S9 补齐 iOS Safari / WeChat webview
  ]
});
