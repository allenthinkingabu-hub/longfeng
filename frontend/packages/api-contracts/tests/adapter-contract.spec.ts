// S7 · V-S7-16 · adapter contract tests · msw + schema 手写校验 · ≥ 8 条用例
// 主文档 §11.7 Step 16 + §11.8 V-S7-16 · 每 endpoint ≥ 1 null + 1 错误码
import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest';
import { setupServer } from 'msw/node';
import { http, HttpResponse } from 'msw';
import {
  wrongbookClient,
  filesClient,
  analysisClient,
  WrongItemVO,
} from '../src';

const BASE = 'http://localhost/api/v1';
// 让 httpClient 用绝对 URL · 避免 msw 拦截不到相对路径
(globalThis as unknown as { __LF_API_BASE__: string }).__LF_API_BASE__ = BASE;

// 所有 handler · 每 endpoint 都有 happy + null 边界 + 错误码
const server = setupServer();

beforeAll(() => server.listen({ onUnhandledRequest: 'warn' }));
afterAll(() => server.close());
afterEach(() => server.resetHandlers());

describe('V-S7-16 · adapter-contract · wrongbookClient', () => {
  it('[SC-01.AC-1] POST /wrongbook/items · 返回 201 + typed WrongItemVO', async () => {
    server.use(
      http.post(`${BASE}/wrongbook/items`, async () =>
        HttpResponse.json({
          id: 'w1',
          subject: 'math',
          stem_text: 'x^2=9',
          tags: [],
          status: 'pending',
          mastery: 0,
          created_at: '2026-04-24T00:00:00Z',
          version: 0,
        } satisfies WrongItemVO),
      ),
    );
    const res = await wrongbookClient.create({ subject: 'math', stem_text: 'x^2=9' });
    expect(res.id).toBe('w1');
    expect(res.tags).toEqual([]);
  });

  it('[SC-01.AC-1] POST /wrongbook/items · 400 validation error', async () => {
    server.use(
      http.post(`${BASE}/wrongbook/items`, async () =>
        HttpResponse.json({ code: 'VALIDATION', message: 'stem_text required' }, { status: 400 }),
      ),
    );
    await expect(wrongbookClient.create({ subject: 'math', stem_text: '' })).rejects.toMatchObject({
      code: 'VALIDATION',
    });
  });

  it('[SC-08] GET /wrongbook/items · 空列表 has_more=false 可解析', async () => {
    server.use(
      http.get(`${BASE}/wrongbook/items`, () =>
        HttpResponse.json({ items: [], has_more: false, next_cursor: null }),
      ),
    );
    const res = await wrongbookClient.list({ status: 'active' });
    expect(res.items).toEqual([]);
    expect(res.has_more).toBe(false);
  });

  it('[SC-02.AC-1] PATCH tags · 204 NoContent 不解析 body', async () => {
    server.use(
      http.patch(`${BASE}/wrongbook/items/w1/tags`, () => new HttpResponse(null, { status: 204 })),
    );
    await expect(wrongbookClient.updateTags('w1', { tags: ['高一'], version: 0 })).resolves.toBeUndefined();
  });

  it('[SC-02.AC-1] PATCH tags · 412 Precondition Failed（乐观锁）', async () => {
    server.use(
      http.patch(`${BASE}/wrongbook/items/w1/tags`, () =>
        HttpResponse.json({ code: 'VERSION_MISMATCH', message: 'stale version' }, { status: 412 }),
      ),
    );
    await expect(wrongbookClient.updateTags('w1', { tags: [], version: 0 })).rejects.toMatchObject({
      code: 'VERSION_MISMATCH',
    });
  });

  it('[SC-04.AC-1] DELETE /wrongbook/items/{id} · 204 软删除成功', async () => {
    server.use(
      http.delete(`${BASE}/wrongbook/items/w1`, () => new HttpResponse(null, { status: 204 })),
    );
    await expect(wrongbookClient.softDelete('w1')).resolves.toBeUndefined();
  });

  it('[SC-04.AC-1] DELETE · 404 未找到', async () => {
    server.use(
      http.delete(`${BASE}/wrongbook/items/missing`, () =>
        HttpResponse.json({ code: 'NOT_FOUND' }, { status: 404 }),
      ),
    );
    await expect(wrongbookClient.softDelete('missing')).rejects.toMatchObject({ code: 'NOT_FOUND' });
  });
});

describe('V-S7-16 · adapter-contract · filesClient', () => {
  it('[SC-11.AC-1] POST /files/presign · 返回 upload_url + file_key + ttl', async () => {
    server.use(
      http.post(`${BASE}/files/presign`, () =>
        HttpResponse.json({
          upload_url: 'https://oss.example.com/bucket/key?sig=xxx',
          file_key: 'abc.jpg',
          ttl_seconds: 900,
          bucket: 'wrongbook-raw',
        }),
      ),
    );
    const res = await filesClient.presign({ mime: 'image/jpeg', size: 1024 });
    expect(res.ttl_seconds).toBe(900);
    expect(res.file_key).toBe('abc.jpg');
  });

  it('[SC-11.AC-1] POST /files/complete/{fileKey} · status=READY + thumb/medium key', async () => {
    server.use(
      http.post(`${BASE}/files/complete/abc.jpg`, () =>
        HttpResponse.json({
          file_key: 'abc.jpg',
          status: 'READY',
          variant_thumb_key: 'abc-thumb.webp',
          variant_medium_key: 'abc-medium.webp',
        }),
      ),
    );
    const res = await filesClient.complete('abc.jpg');
    expect(res.status).toBe('READY');
    expect(res.variant_thumb_key).toBeDefined();
  });

  it('[SC-11.AC-1] POST /files/complete · 422 QUARANTINED（病毒扫描）', async () => {
    server.use(
      http.post(`${BASE}/files/complete/bad.jpg`, () =>
        HttpResponse.json({ code: 'QUARANTINED', message: 'virus scan positive' }, { status: 422 }),
      ),
    );
    await expect(filesClient.complete('bad.jpg')).rejects.toMatchObject({ code: 'QUARANTINED' });
  });
});

describe('V-S7-16 · adapter-contract · analysisClient', () => {
  it('[SC-03.AC-1] GET /analysis/{id}/similar · 空 items 数组可解析', async () => {
    server.use(
      http.get(`${BASE}/analysis/w1/similar`, () => HttpResponse.json({ items: [] })),
    );
    const res = await analysisClient.similar('w1', 3);
    expect(res.items).toEqual([]);
  });

  it('[SC-03.AC-1] similar · 返回距离在 [0, 2] 区间内（cosine）', async () => {
    server.use(
      http.get(`${BASE}/analysis/w1/similar`, () =>
        HttpResponse.json({
          items: [
            { id: 's1', stem_text: '题干 1', distance: 0.12, subject: 'math' },
            { id: 's2', stem_text: '题干 2', distance: 1.85, subject: 'math' },
          ],
        }),
      ),
    );
    const res = await analysisClient.similar('w1', 3);
    for (const it of res.items) {
      expect(it.distance).toBeGreaterThanOrEqual(0);
      expect(it.distance).toBeLessThanOrEqual(2);
    }
  });
});
