/**
 * MSW · anonymous-service guest mock（B 轨 · 给 SC-11/SC-12/SC-14 用）
 */
import { http, HttpResponse } from 'msw';

const QUOTA_PER_DAY = 1;

export const guestHandlers = [
  // P-LANDING samples（30/min IP 限流由 BE-05 IT 兜 · 这里只 mock happy path）
  http.get('/api/landing/samples', () => HttpResponse.json({
    bucket: 'default',
    samples: [
      { id: 'sample-1', subject: 'math',    stemPreview: '已知函数 f(x)=x²-4x+3，求顶点坐标。', thumbnailUrl: '/mock/math.png' },
      { id: 'sample-2', subject: 'physics', stemPreview: '一物体做匀速直线运动，加速度为零…',     thumbnailUrl: '/mock/physics.png' },
      { id: 'sample-3', subject: 'english', stemPreview: 'Choose the best word: She ___ to school every day.', thumbnailUrl: '/mock/english.png' },
    ],
  })),

  http.get('/api/landing/kpi', () => HttpResponse.json({
    totalQuestionsAnalyzed: 1_204_312,
    retention7d: 0.47,
    headline: '已分析 120w+ 错题',
  })),

  // Guest session
  http.post('/api/guest/session', () => HttpResponse.json({
    guestSessionId: 'guest-mock-001',
    quotaRemaining: QUOTA_PER_DAY,
    quotaResetAt: new Date(Date.now() + 86_400_000).toISOString(),
  })),

  http.get('/api/guest/quota', () => HttpResponse.json({
    quotaRemaining: QUOTA_PER_DAY,
    quotaResetAt: new Date(Date.now() + 86_400_000).toISOString(),
  })),

  // Guest analyze (simplified · returns task id)
  http.post('/api/guest/analyze', () => HttpResponse.json({
    guest_session_id: 'guest-mock-001',
    task_id: 'guest-task-001',
    status: 'ANALYZING',
  })),

  // Claim：游客→注册后转移
  http.post('/api/guest/claim', async ({ request }) => {
    const body = await request.json() as { guestSessionId?: string };
    return HttpResponse.json({
      claimed: true,
      transferredItemCount: 1,
      newQids: [`claimed-${body?.guestSessionId ?? 'unknown'}-001`],
    });
  }),

  // Welcomeback (SC-14)
  http.post('/api/welcomeback/lookup', async ({ request }) => {
    const body = await request.json() as { deviceFp?: string };
    if (!body?.deviceFp) {
      return new HttpResponse(JSON.stringify({ error: 'NO_FINGERPRINT' }), { status: 404 });
    }
    return HttpResponse.json({
      candidates: [
        { userId: 'student-001', maskedNick: '张*', lastSeenAt: new Date().toISOString() },
      ],
    });
  }),
];
