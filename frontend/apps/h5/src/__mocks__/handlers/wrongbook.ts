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

export const WRONGBOOK_LIST: WBItem[] = JSON.parse(
  JSON.stringify(fixture.listResponse.items),
) as WBItem[];

// 给 capture / share / 其他 handler 调用以注入新 item（按需 push 顶部）
export function pushWrongbookItem(item: WBItem): void {
  // 去重 by id
  const existing = WRONGBOOK_LIST.findIndex((i) => i.id === item.id);
  if (existing >= 0) {
    WRONGBOOK_LIST[existing] = item;
  } else {
    WRONGBOOK_LIST.unshift(item); // 最新在顶部
  }
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
];
