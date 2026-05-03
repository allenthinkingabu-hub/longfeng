/**
 * MSW · observer mock（B 轨 · 给 SC-15 用）· C4/C5 红线
 */
import { http, HttpResponse } from 'msw';

const REVOKED_OBSERVERS = new Set<string>();

export const observerHandlers = [
  // 邀请码 → observer jwt
  http.post('/api/observer/exchange', async ({ request }) => {
    const body = await request.json() as { inviteCode?: string; purpose?: string };
    if (!body?.inviteCode || body.inviteCode.length !== 6) {
      return new HttpResponse(JSON.stringify({ error: 'INVALID_CODE' }), { status: 400 });
    }
    return HttpResponse.json({
      observerJwt: `mock-observer-jwt-${body.inviteCode}`,
      studentIdHash: 'hash-student-001',
      role: body.purpose === 'TEACHER_VIEW' ? 'TEACHER' : 'PARENT',
      ttlSeconds: 30 * 86400,
      maskedNick: '张*',
    });
  }),

  // overview · 必须脱敏
  http.get('/api/observer/overview', ({ request }) => {
    const auth = request.headers.get('authorization') ?? '';
    const jwt = auth.replace(/^Bearer\s+/, '');
    if (REVOKED_OBSERVERS.has(jwt)) {
      return new HttpResponse(JSON.stringify({ error: 'REVOKED' }), { status: 403 });
    }
    return HttpResponse.json({
      student: {
        nickMasked: '张*',
        avatarSeed: 'Z',
        // C5：不返回 email / chat_id / 原图
      },
      stats: {
        last7dReviewCount: 18,
        masteryPct: 68,
        subjectDist: { math: 8, physics: 5, chemistry: 3, english: 2 },
      },
      recentItems: [
        { qidHash: 'h-1', subject: 'math',    stemPreview: '已知函数 f(x)…（前 50 字）', tLevel: 'T2', nextDueAt: '2026-05-04' },
        { qidHash: 'h-2', subject: 'physics', stemPreview: '一物体匀速运动…',           tLevel: 'T1', nextDueAt: '2026-05-03' },
      ],
    });
  }),

  // 撤销
  http.post('/api/observer/revoke', async ({ request }) => {
    const body = await request.json() as { observerId?: string };
    if (body?.observerId) REVOKED_OBSERVERS.add(`mock-observer-jwt-${body.observerId}`);
    return HttpResponse.json({ revoked: true, ts: new Date().toISOString() });
  }),

  // 任意写 API · 必须 403（SC-15 异常 2）
  http.patch('/api/v1/wrongbook/items/:id/tags', ({ request }) => {
    const auth = request.headers.get('authorization') ?? '';
    if (auth.includes('observer') || auth.includes('revoked')) {
      return new HttpResponse(JSON.stringify({ error: 'OBSERVER_READ_ONLY' }), { status: 403 });
    }
    return HttpResponse.json({ tags: [] });
  }),
];
