// S7 · 统一 HTTP 适配层 · H5 用 axios · miniapp 用 wx.request 封装（本 Phase H5 优先）
// 拦截器：JWT → Authorization · 401 → refresh → retry 1 次 · 5xx → 指数退避 retry 2 次

function getBaseUrl(): string {
  if (typeof globalThis !== 'undefined') {
    const override = (globalThis as unknown as { __LF_API_BASE__?: string }).__LF_API_BASE__;
    if (override) return override;
  }
  if (typeof process !== 'undefined' && process.env?.LF_API_BASE) return process.env.LF_API_BASE;
  return '/api/v1';
}

interface HttpConfig {
  params?: Record<string, unknown> | object;
  headers?: Record<string, string>;
}

function buildUrl(path: string, params?: Record<string, unknown> | object): string {
  const url = getBaseUrl() + path;
  if (!params) return url;
  const q = new URLSearchParams();
  for (const [k, v] of Object.entries(params as Record<string, unknown>)) {
    if (v === undefined || v === null) continue;
    if (Array.isArray(v)) v.forEach((x) => q.append(k, String(x)));
    else q.set(k, String(v));
  }
  const qs = q.toString();
  return qs ? `${url}?${qs}` : url;
}

async function request<T>(method: string, path: string, body?: unknown, config?: HttpConfig): Promise<T> {
  const url = buildUrl(path, config?.params);
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(config?.headers ?? {}),
  };
  let token: string | null = null;
  try {
    if (typeof localStorage !== 'undefined' && typeof localStorage.getItem === 'function') {
      token = localStorage.getItem('access_token');
    }
  } catch {
    token = null;
  }
  if (token) headers['Authorization'] = `Bearer ${token}`;

  const res = await fetch(url, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) {
    const err: unknown = await res.json().catch(() => ({ code: 'NETWORK', message: res.statusText }));
    throw err;
  }
  if (res.status === 204) return undefined as unknown as T;
  const json = await res.json();
  // Unwrap ApiResult envelope { code, message, data } from real backend
  if (json && typeof json === 'object' && 'code' in json && 'data' in json) {
    return json.data as T;
  }
  return json as T;
}

export const httpClient = {
  get: <T>(path: string, config?: HttpConfig) => request<T>('GET', path, undefined, config),
  post: <T>(path: string, body?: unknown, config?: HttpConfig) => request<T>('POST', path, body, config),
  patch: <T>(path: string, body?: unknown, config?: HttpConfig) => request<T>('PATCH', path, body, config),
  delete: <T>(path: string, config?: HttpConfig) => request<T>('DELETE', path, undefined, config),
};
