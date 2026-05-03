/**
 * S9 · 时钟控制 fixture · D-Cancel-Race + 跨时区 + DND 用
 *
 * 用法：
 *   await freezeClock(page, '2026-05-02T10:30:00+08:00');
 *   await advanceTo(page, '2026-05-04T01:00:00+08:00');
 *
 * 实现：Playwright addInitScript 注入 fake Date.now/Date constructor
 * 注意：不替换 setTimeout（仅冻结日期，避免 SSE 心跳挂掉 · plan §6.5 红线"禁关 SSE"）
 */
import type { Page } from '@playwright/test';

export async function freezeClock(page: Page, isoString: string): Promise<void> {
  const frozenMs = new Date(isoString).getTime();
  if (Number.isNaN(frozenMs)) {
    throw new Error(`freezeClock: invalid ISO string: ${isoString}`);
  }
  await page.addInitScript((ms) => {
    const RealDate = Date;
    const offset = ms - RealDate.now();
    class FakeDate extends RealDate {
      constructor(...args: unknown[]) {
        if (args.length === 0) {
          super(RealDate.now() + offset);
        } else {
          // @ts-expect-error spread to RealDate
          super(...args);
        }
      }
      static now() {
        return RealDate.now() + offset;
      }
    }
    // @ts-expect-error assign over global Date
    globalThis.Date = FakeDate;
  }, frozenMs);
}

export async function advanceTo(page: Page, isoString: string): Promise<void> {
  // 重新注入 → 等价于"跳到目标时刻"
  await freezeClock(page, isoString);
  // 触发一次 reload 让页面感知新时间（cursor / 倒计时 等）
  await page.reload();
  await page.waitForLoadState('networkidle');
}

/**
 * 给 SC-08 跨时区用：切换 X-Timezone header（C9 红线：UTC 存储 + 按 X-Timezone 渲染）
 * 注意：不修改浏览器 timezoneId（playwright.config 已锁 Asia/Shanghai 用于默认渲染），
 * 仅模拟用户在 settings 改时区后下一个请求的 header。
 */
export async function setXTimezone(page: Page, tz: 'Asia/Shanghai' | 'America/Los_Angeles' | 'UTC' | string): Promise<void> {
  await page.setExtraHTTPHeaders({ 'X-Timezone': tz });
}
