import { http, HttpResponse } from 'msw';
import fixture from '../fixtures/wrongbook-list.json';

// SC-01: module-level 持久化数组 · 让 list +1 在 POST 后立刻可见
// 初始化：从 fixture 复制种子 items（深拷贝避免污染 fixture）
type WBItem = {
  id: string;
  subject: string;
  stem_text?: string;
  tags?: string[];
  status?: string;
  mastery?: number;
  image_url?: string | null;
  created_at?: string;
  version?: number;
  [k: string]: unknown;
};

// SC-01: 持久化到 sessionStorage 让 list state 跨 page.goto() (full reload) 保留
// e2e POM 用 page.goto('/wrongbook') 完整 reload · 模块重新 init · 之前必丢 push
const WRONGBOOK_STORAGE_KEY = 'msw:wrongbook:list';

function loadWrongbookList(): WBItem[] {
  try {
    if (typeof sessionStorage === 'undefined') {
      return JSON.parse(JSON.stringify(fixture.listResponse.items)) as WBItem[];
    }
    const raw = sessionStorage.getItem(WRONGBOOK_STORAGE_KEY);
    if (raw) return JSON.parse(raw) as WBItem[];
    const seed = JSON.parse(JSON.stringify(fixture.listResponse.items)) as WBItem[];
    sessionStorage.setItem(WRONGBOOK_STORAGE_KEY, JSON.stringify(seed));
    return seed;
  } catch {
    return JSON.parse(JSON.stringify(fixture.listResponse.items)) as WBItem[];
  }
}

function saveWrongbookList(items: WBItem[]): void {
  try {
    if (typeof sessionStorage !== 'undefined') {
      sessionStorage.setItem(WRONGBOOK_STORAGE_KEY, JSON.stringify(items));
    }
  } catch { /* ignore quota */ }
}

export const WRONGBOOK_LIST: WBItem[] = loadWrongbookList();

// 给 capture / share / 其他 handler 调用以注入新 item（按需 push 顶部）
export function pushWrongbookItem(item: WBItem): void {
  // 去重 by id
  const existing = WRONGBOOK_LIST.findIndex((i) => i.id === item.id);
  if (existing >= 0) {
    WRONGBOOK_LIST[existing] = item;
  } else {
    WRONGBOOK_LIST.unshift(item); // 最新在顶部
  }
  saveWrongbookList(WRONGBOOK_LIST);
}

export const wrongbookHandlers = [
  http.get('/api/v1/wrongbook/items', () => {
    return HttpResponse.json({
      ...fixture.listResponse,
      items: WRONGBOOK_LIST,
      total: WRONGBOOK_LIST.length,
    });
  }),

  http.get('/api/v1/wrongbook/tags', () => {
    return HttpResponse.json(fixture.tagsResponse);
  }),

  http.get('/api/v1/wrongbook/items/:id', ({ params }) => {
    const item = WRONGBOOK_LIST.find((i) => i.id === params.id);
    if (!item) return new HttpResponse(null, { status: 404 });
    return HttpResponse.json(item);
  }),

  // SC-01: POST /api/v1/wrongbook/items → push 一个 item · 立即可见 in GET list
  // 注：capture.ts 也注册了同一路径 · MSW 按 handler 注册顺序匹配 · 把这条放最后
  // 改用 PUT/save 端点避免冲突 · 但前端实际可能 POST 同路径 · 先并存
  http.post('/api/v1/wrongbook/save', async ({ request }) => {
    const body = await request.json().catch(() => ({})) as Partial<WBItem>;
    const newItem: WBItem = {
      id: body.id ?? `item-msw-${Date.now()}`,
      subject: body.subject ?? 'math',
      stem_text: body.stem_text ?? '（MSW save）新增错题',
      tags: body.tags ?? [],
      status: body.status ?? 'completed',
      mastery: body.mastery ?? 0,
      image_url: body.image_url ?? null,
      created_at: new Date().toISOString(),
      version: 1,
    };
    pushWrongbookItem(newItem);
    return HttpResponse.json(newItem, { status: 201 });
  }),

  // SC-01: Result page 实际 POST /api/wb/questions/:id/save (Result/index.tsx:152) · 必须 mock
  http.post('/api/wb/questions/:id/save', async ({ request, params }) => {
    const body = await request.json().catch(() => ({})) as Record<string, unknown>;
    // SC-01: id 加 timestamp 避免与 seed 'mock-qid-001' 冲突 → pushWrongbookItem 走 unshift
    const uniqueId = `${String(params.id ?? 'q')}-${Date.now()}`;
    const newItem: WBItem = {
      id: uniqueId,
      subject: (body.subject as string) ?? 'math',
      stem_text: (body.stem_text as string) ?? '（MSW save）新增错题',
      tags: (body.tags as string[]) ?? [],
      status: 'completed',
      mastery: 0,
      image_url: (body.image_url as string) ?? null,
      created_at: new Date().toISOString(),
      version: 1,
    };
    pushWrongbookItem(newItem);
    return HttpResponse.json({ ok: true, id: newItem.id, plan_id: `plan-${newItem.id}` }, { status: 200 });
  }),
];
