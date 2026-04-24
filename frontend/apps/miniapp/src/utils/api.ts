// S7 miniapp · HTTP 层 · 封装 wx.request 等价 api-contracts.httpClient · 禁直接 wx.request 散调
// 双端同语义：H5 fetch ↔ miniapp wx.request · ADR 0014

const BASE_URL = 'https://api.longfeng.local/api/v1';

interface ReqOpt {
  params?: Record<string, unknown>;
  headers?: Record<string, string>;
}

function buildQuery(params?: Record<string, unknown>): string {
  if (!params) return '';
  const parts: string[] = [];
  for (const k in params) {
    const v = params[k];
    if (v === undefined || v === null) continue;
    if (Array.isArray(v)) v.forEach((x) => parts.push(`${k}=${encodeURIComponent(String(x))}`));
    else parts.push(`${k}=${encodeURIComponent(String(v))}`);
  }
  return parts.length ? '?' + parts.join('&') : '';
}

function request<T>(method: string, path: string, body?: unknown, opt?: ReqOpt): Promise<T> {
  return new Promise((resolve, reject) => {
    const token = wx.getStorageSync('access_token') || '';
    wx.request({
      url: BASE_URL + path + buildQuery(opt?.params),
      method: method as 'GET' | 'POST' | 'PATCH' | 'DELETE',
      data: body,
      header: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(opt?.headers || {}),
      },
      success: (res) => {
        if (res.statusCode >= 400) return reject(res.data);
        resolve(res.data as T);
      },
      fail: (e) => reject(e),
    });
  });
}

export const api = {
  get: <T>(path: string, opt?: ReqOpt) => request<T>('GET', path, undefined, opt),
  post: <T>(path: string, body?: unknown, opt?: ReqOpt) => request<T>('POST', path, body, opt),
  patch: <T>(path: string, body?: unknown, opt?: ReqOpt) => request<T>('PATCH', path, body, opt),
};

// 类型（与 api-contracts 对齐 · 本地 re-declare 避免小程序构建拉 workspace 包）
export interface WrongItemVO {
  id: string;
  subject: string;
  stem_text: string;
  tags: string[];
  status: 'pending' | 'analyzing' | 'completed' | 'error';
  mastery: number;
  image_url?: string;
  created_at: string;
  version: number;
}

export interface WrongItemListResponse {
  items: WrongItemVO[];
  next_cursor?: string;
  has_more?: boolean;
}
