/**
 * S9 · 游客身份 fixture · SC-11 / SC-12 用
 *
 * 设备指纹生成（D4-1 决策：IndexedDB + Canvas + UA 三路 + sha256 hash）：
 *   本 fixture 给 e2e 测试用稳定的可控指纹（plan §6.5 不允许直接调 API · 必须真走 UI 注入）
 *
 * 注入时机：browser context 启动后 + page.goto('/welcome') 前
 * 通过 addInitScript 在 IndexedDB 中预置 deviceFp，让前端 useDeviceFingerprint hook 直接读到
 */
import { type BrowserContext } from '@playwright/test';

export interface GuestDevice {
  fp: string;             // sha256-like 8byte hex
  ua: string;             // user-agent override (optional)
  quotaPerDay: number;    // 默认 1
}

let guestSeq = 0;

/**
 * 生成新的设备指纹 · 唯一性保证（多 test 不冲突）。
 */
export function newDeviceFingerprint(prefix = 'qa-guest'): GuestDevice {
  guestSeq += 1;
  const seed = `${prefix}-${Date.now()}-${guestSeq}`;
  // 简易 hash（非加密用途）
  let h = 5381;
  for (let i = 0; i < seed.length; i++) h = ((h << 5) + h + seed.charCodeAt(i)) >>> 0;
  const fp = h.toString(16).padStart(8, '0').repeat(2); // 16 hex chars
  return { fp, ua: 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 QA-E2E', quotaPerDay: 1 };
}

/**
 * 把 deviceFp 注入到 context 的 IndexedDB（前端 hook 优先读 IndexedDB）
 * 必须在 page.goto 之前调用。
 */
export async function injectDeviceFingerprint(context: BrowserContext, device: GuestDevice): Promise<void> {
  await context.addInitScript((fp) => {
    // 模拟 useDeviceFingerprint hook 的 cache 路径
    try {
      // sessionStorage 是兜底（plan §3.4 D4-1 三路指纹的最后一路）
      window.sessionStorage.setItem('__lf_device_fp__', fp);
      // localStorage 长期 cache
      window.localStorage.setItem('__lf_device_fp__', fp);
    } catch (e) { /* private mode 等 */ }
  }, device.fp);
}

/**
 * 模拟"游客额度耗尽" · SC-11 第 2 条 · 让限流命中
 * 通过 X-Forwarded-For header 复用同一 IP，触发 30/min IP 限流
 */
export async function setGuestIp(context: BrowserContext, ip: string): Promise<void> {
  await context.setExtraHTTPHeaders({ 'X-Forwarded-For': ip });
}
