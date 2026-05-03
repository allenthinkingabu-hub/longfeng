// S7 · bootstrap/deeplink-router.ts
// wb:// scheme 深链解析器
//
// 支持 4 类深链（PRD §2A.3.1 + 业务与技术解决方案 §2A.3.1）：
//   wb://capture            → /capture（拍题）
//   wb://review/exec/:id    → /review/exec/:id（复习执行）
//   wb://s/:shareToken      → /s/:shareToken（分享预览）
//   wb://observer/:code     → /observer/:code（观察者邀请）
//   wb://home               → /（首页）
//   wb://home?focus=review  → /?focus=review
//   wb://review/today       → /review（今日复习）
//   wb://calendar           → /calendar/month
//   wb://event/:id          → /event/:id
//   wb://notifications      → /notifications
//   wb://me                 → /me
//   wb://wrongbook          → /wrongbook
//   wb://wrongbook/:qid     → /wrongbook/:qid

import type { DeeplinkRoute } from '../types/shell';

const WB_SCHEME = 'wb://';

/**
 * parseDeeplink · 解析 wb:// scheme 为结构化路由对象。
 *
 * @param url - 完整深链 URL，如 `wb://review/exec/node-123`
 * @returns DeeplinkRoute
 */
export function parseDeeplink(url: string): DeeplinkRoute {
  if (!url.startsWith(WB_SCHEME)) {
    return { type: 'unknown', raw: url };
  }

  // 剥离 scheme，得到 path + query
  const withoutScheme = url.slice(WB_SCHEME.length);
  const [pathStr, queryStr = ''] = withoutScheme.split('?');
  const segments = pathStr.split('/').filter(Boolean);

  // 解析 query string
  const params = new URLSearchParams(queryStr);

  const [seg0, seg1, seg2] = segments;

  // wb://capture
  if (seg0 === 'capture' && !seg1) {
    return { type: 'capture' };
  }

  // wb://review/exec/:nodeId
  if (seg0 === 'review' && seg1 === 'exec' && seg2) {
    return { type: 'review-exec', nodeId: decodeURIComponent(seg2) };
  }

  // wb://review/today 或 wb://review
  if (seg0 === 'review' && (!seg1 || seg1 === 'today')) {
    return { type: 'review-today' };
  }

  // wb://s/:shareToken
  if (seg0 === 's' && seg1) {
    return { type: 'shared', shareToken: decodeURIComponent(seg1) };
  }

  // wb://observer/:code
  if (seg0 === 'observer' && seg1) {
    return { type: 'observer', code: decodeURIComponent(seg1) };
  }

  // wb://home[?focus=review]
  if (seg0 === 'home' || !seg0) {
    const focus = params.get('focus') ?? undefined;
    return { type: 'home', focus };
  }

  // wb://calendar
  if (seg0 === 'calendar') {
    return { type: 'calendar' };
  }

  // wb://event/:eventId
  if (seg0 === 'event' && seg1) {
    return { type: 'event', eventId: decodeURIComponent(seg1) };
  }

  // wb://notifications
  if (seg0 === 'notifications') {
    return { type: 'notifications' };
  }

  // wb://me
  if (seg0 === 'me') {
    return { type: 'me' };
  }

  // wb://wrongbook/:qid 或 wb://wrongbook
  if (seg0 === 'wrongbook') {
    if (seg1) {
      return { type: 'wrongbook-detail', qid: decodeURIComponent(seg1) };
    }
    return { type: 'wrongbook' };
  }

  return { type: 'unknown', raw: url };
}

/**
 * deeplinkToPath · 将 DeeplinkRoute 转为 H5 路由路径。
 *
 * @param route - 解析后的深链路由对象
 * @returns H5 路径字符串（如 `/review/exec/node-123`）
 */
export function deeplinkToPath(route: DeeplinkRoute): string {
  switch (route.type) {
    case 'capture':
      return '/capture';
    case 'review-exec':
      return `/review/exec/${encodeURIComponent(route.nodeId)}`;
    case 'review-today':
      return '/review';
    case 'shared':
      return `/s/${encodeURIComponent(route.shareToken)}`;
    case 'observer':
      return `/observer/${encodeURIComponent(route.code)}`;
    case 'home':
      return route.focus ? `/?focus=${encodeURIComponent(route.focus)}` : '/';
    case 'calendar':
      return '/calendar/month';
    case 'event':
      return `/event/${encodeURIComponent(route.eventId)}`;
    case 'notifications':
      return '/notifications';
    case 'me':
      return '/me';
    case 'wrongbook':
      return '/wrongbook';
    case 'wrongbook-detail':
      return `/wrongbook/${encodeURIComponent(route.qid)}`;
    case 'unknown':
      return '/';
  }
}

/**
 * handleWebDeeplink · 处理 H5 环境下的深链（通常来自通知/分享的 URL 参数）。
 *
 * 使用场景：
 *   1. APP 内 WebView 通过 `window.__WB_DEEPLINK__` 注入
 *   2. 微信公众号消息点击 → H5 URL 带 `?deeplink=wb://...`
 *   3. 推送点击 → URL 参数
 *
 * @returns 若有深链则返回 H5 路径，否则返回 null
 */
export function handleWebDeeplink(): string | null {
  if (typeof window === 'undefined') return null;

  // 方式 1: WebView 注入
  const injected = (window as Record<string, unknown>).__WB_DEEPLINK__;
  if (typeof injected === 'string' && injected.startsWith(WB_SCHEME)) {
    const route = parseDeeplink(injected);
    return deeplinkToPath(route);
  }

  // 方式 2: URL query 参数 ?deeplink=wb://...
  const urlParams = new URLSearchParams(window.location.search);
  const dlParam = urlParams.get('deeplink');
  if (dlParam && dlParam.startsWith(WB_SCHEME)) {
    const route = parseDeeplink(dlParam);
    return deeplinkToPath(route);
  }

  return null;
}
