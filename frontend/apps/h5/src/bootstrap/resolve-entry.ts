// S7 · bootstrap/resolve-entry.ts
// PRD §2A.3.1 · 登录态决策树（冷启动 / 深链 / 分享链统一入口）
//
// 决策树（顺序严格 · 上优先）：
//   1. 持有合法 JWT → TabShell（直达 deeplink 或 P-HOME）
//   2. URL 含 /s/:shareToken → AnonymousShell → P-SHARED
//   3. URL 含 /observer/:code → ObserverShell
//   4. 设备指纹命中（P1占位）→ AnonymousShell → P-WELCOMEBACK
//   5. 默认 → AnonymousShell → P-LANDING

import type { EntryResult, JwtPayload, ObserverJwtPayload } from '../types/shell';

const TOKEN_KEY = 'lf:token' as const;

// ── §1 JWT 工具 ─────────────────────────────────────────────────────────────

/**
 * 从 localStorage 取 JWT 并解析 payload（不做签名验证，仅本地快速判断）。
 * 真正的签名验证在 gateway 网关层完成。
 */
function readLocalToken(): string | null {
  try {
    return typeof localStorage !== 'undefined'
      ? localStorage.getItem(TOKEN_KEY)
      : null;
  } catch {
    return null;
  }
}

/**
 * Base64url → JSON。JWT payload 是 base64url 编码。
 */
function decodeJwtPayload(token: string): JwtPayload | null {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return null;
    // base64url → base64 → atob
    const b64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = b64 + '=='.slice((b64.length % 4) || 4);
    return JSON.parse(atob(padded)) as JwtPayload;
  } catch {
    return null;
  }
}

/**
 * 判断 JWT 是否有效（未过期）。
 * 留 30s 余量防止时钟偏差。
 */
function isTokenValid(payload: JwtPayload): boolean {
  return payload.exp > Math.floor(Date.now() / 1000) + 30;
}

/**
 * 清除本地 token（登出 / token 过期时使用）。
 */
export function clearLocalToken(): void {
  try {
    if (typeof localStorage !== 'undefined') {
      localStorage.removeItem(TOKEN_KEY);
    }
  } catch {
    /* ignore */
  }
}

/**
 * 保存 token 到 localStorage。
 */
export function saveLocalToken(token: string): void {
  try {
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem(TOKEN_KEY, token);
    }
  } catch {
    /* ignore */
  }
}

// ── §2 URL 解析工具 ──────────────────────────────────────────────────────────

/** 正则：/s/<shareToken> */
const SHARE_PATH_RE = /^\/s\/([A-Za-z0-9_\-%.]+)$/;

/** 正则：/observer/<code> */
const OBSERVER_PATH_RE = /^\/observer\/([A-Za-z0-9_\-%.]+)$/;

interface ParsedUrl {
  shareToken: string | null;
  observerCode: string | null;
}

function parseCurrentUrl(): ParsedUrl {
  if (typeof window === 'undefined') {
    return { shareToken: null, observerCode: null };
  }
  const path = window.location.pathname;

  const shareMatch = SHARE_PATH_RE.exec(path);
  if (shareMatch) {
    return { shareToken: decodeURIComponent(shareMatch[1]), observerCode: null };
  }

  const observerMatch = OBSERVER_PATH_RE.exec(path);
  if (observerMatch) {
    return { shareToken: null, observerCode: decodeURIComponent(observerMatch[1]) };
  }

  return { shareToken: null, observerCode: null };
}

// ── §3 设备指纹（P1 占位）────────────────────────────────────────────────────

/**
 * P1 占位：检查设备指纹是否命中已注册账号（P0 阶段直接返回 false）。
 * P1 实现时：调用 GET /api/session/resolve?fp=xxx 返回 { matched: bool, student_id? }。
 */
async function checkDeviceFingerprint(): Promise<boolean> {
  // P0: 设备指纹识别为 P1 功能，直接跳过
  return false;
}

// ── §4 主决策函数 ─────────────────────────────────────────────────────────────

/**
 * resolveEntry · PRD §2A.3.1 决策树入口。
 *
 * @returns EntryResult · 决定使用哪个 Shell + 初始路由
 *
 * 调用时机：App 挂载时（BrowserRouter 内），每次冷启动执行一次。
 * 非首次跳转由路由守卫（PrivateRoute / ObserverRoute）负责。
 */
export async function resolveEntry(): Promise<EntryResult> {
  // ── 节点 1: 持有合法 JWT ─────────────────────────────────────────────────
  const rawToken = readLocalToken();
  if (rawToken) {
    const payload = decodeJwtPayload(rawToken);
    if (payload && isTokenValid(payload)) {
      // Observer scope 的 token → ObserverShell（observer code 从 token 中取）
      if (payload.scope === 'OBSERVER') {
        const obsPayload = payload as ObserverJwtPayload;
        return {
          shell: 'observer',
          code: obsPayload.observer_code ?? '',
        };
      }
      // 普通已登录用户 → TabShell
      // deeplink = 当前 pathname（若是 /s/ 或 /observer/ 则下面的逻辑已处理 share/obs；
      // 但 token 有效优先，说明用户在已登录状态点击分享链，展示时可在 TabShell 内渲染）
      const deeplink =
        window.location.pathname !== '/' ? window.location.pathname + window.location.search : undefined;
      return { shell: 'tab', deeplink };
    }
    // Token 存在但已过期 → 清除，继续判断
    clearLocalToken();
  }

  // ── 节点 2: URL 含 shareToken / observerCode ─────────────────────────────
  const { shareToken, observerCode } = parseCurrentUrl();
  if (shareToken) {
    return { shell: 'anon', route: 'shared', shareToken };
  }
  if (observerCode) {
    return { shell: 'observer', code: observerCode };
  }

  // ── 节点 3 (P1): 设备指纹命中 ────────────────────────────────────────────
  const fpMatch = await checkDeviceFingerprint();
  if (fpMatch) {
    return { shell: 'anon', route: 'welcomeback' };
  }

  // ── 节点 4: 默认 → P-LANDING ──────────────────────────────────────────────
  return { shell: 'anon', route: 'landing' };
}

/**
 * 快速同步版（不含 fp 异步检查）：供 SSR / 首帧占位使用。
 * 仅判断 JWT + URL，不做 fp 检查。
 */
export function resolveEntrySync(): EntryResult {
  const rawToken = readLocalToken();
  if (rawToken) {
    const payload = decodeJwtPayload(rawToken);
    if (payload && isTokenValid(payload)) {
      if (payload.scope === 'OBSERVER') {
        const obsPayload = payload as ObserverJwtPayload;
        return { shell: 'observer', code: obsPayload.observer_code ?? '' };
      }
      return { shell: 'tab' };
    }
    clearLocalToken();
  }

  const { shareToken, observerCode } = parseCurrentUrl();
  if (shareToken) return { shell: 'anon', route: 'shared', shareToken };
  if (observerCode) return { shell: 'observer', code: observerCode };

  return { shell: 'anon', route: 'landing' };
}
