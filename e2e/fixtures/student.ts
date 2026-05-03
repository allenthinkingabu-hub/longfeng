/**
 * S9 · 学生身份 fixture
 *
 * 三个 tier（plan §6.2 SC-16）：
 *   - NORMAL    · 默认 / 锁 AI 模型选择
 *   - VIP       · 解锁基础模型选择
 *   - VIP_PLUS  · 解锁实验池模型 + 成本/延迟显示
 *
 * 红线（plan §6.5）：禁 localStorage.setItem('token') 跳登录
 *   → 本 fixture 通过真实走 P00 登录页 + dev-only mock auth endpoint 完成
 *   → A 轨：staging 上必须用真实账号 + 真 OAuth flow（DevOps 准备 4 个测试号）
 *   → B 轨：MSW handler 拦截 /api/auth/login 返回对应 tier 的 jwt
 */
import { test as base, type Page, type BrowserContext } from '@playwright/test';

export type UserTier = 'NORMAL' | 'VIP' | 'VIP_PLUS';

export interface TestUser {
  tier: UserTier;
  account: string;
  password: string;
  expectedNickPrefix: string;
}

export const TEST_USERS: Record<UserTier, TestUser> = {
  NORMAL:   { tier: 'NORMAL',   account: 'qa-normal@longfeng.test',   password: 'Qa!Normal2026',   expectedNickPrefix: '同学' },
  VIP:      { tier: 'VIP',      account: 'qa-vip@longfeng.test',      password: 'Qa!Vip2026',      expectedNickPrefix: 'VIP' },
  VIP_PLUS: { tier: 'VIP_PLUS', account: 'qa-vipplus@longfeng.test',  password: 'Qa!VipPlus2026', expectedNickPrefix: 'VIP+' },
};

/**
 * 通过 P00 登录页真实 UI 操作完成登录。
 * 不允许 localStorage 短路。
 */
export async function loginAs(page: Page, tier: UserTier): Promise<TestUser> {
  const user = TEST_USERS[tier];
  await page.goto('/auth');
  await page.waitForLoadState('networkidle');

  // P00 同意条款
  await page.getByTestId('p00-consent-bar-checkbox').check();

  // dev mode: 走"其他登录方式" → 账密表单（避免 staging 真实微信扫码）
  await page.getByTestId('p00-other-methods-link').click();
  await page.waitForLoadState('networkidle');

  // 通用账密表单 testid（dev/staging 共用 · spec 未覆盖，登记为已知占位）
  await page.getByTestId('auth-form-account').fill(user.account);
  await page.getByTestId('auth-form-password').fill(user.password);
  await page.getByTestId('auth-form-submit').click();

  // 等待跳转到登录态主页 (TabShell 任一 route · / 即 P-HOME)
  await page.waitForURL((url) => !url.pathname.startsWith('/auth'), { timeout: 15_000 });
  await page.waitForLoadState('networkidle');

  return user;
}

export const loginAsStudent  = (page: Page) => loginAs(page, 'NORMAL');
export const loginAsVip      = (page: Page) => loginAs(page, 'VIP');
export const loginAsVipPlus  = (page: Page) => loginAs(page, 'VIP_PLUS');

/**
 * Playwright fixture: 注入登录态学生（按 tier 选）。
 * 用法：
 *   import { test } from '../fixtures/student';
 *   test('xxx', async ({ studentPage }) => { ... });
 */
export const test = base.extend<{
  studentPage: Page;
  vipPage: Page;
  vipPlusPage: Page;
}>({
  studentPage: async ({ page }, use) => {
    await loginAsStudent(page);
    await use(page);
  },
  vipPage: async ({ page }, use) => {
    await loginAsVip(page);
    await use(page);
  },
  vipPlusPage: async ({ page }, use) => {
    await loginAsVipPlus(page);
    await use(page);
  },
});

export { expect } from '@playwright/test';
