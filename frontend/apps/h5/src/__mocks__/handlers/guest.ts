/**
 * MSW · anonymous-service guest mock（B 轨 · 给 SC-11/SC-12/SC-14 用）
 */
import { http, HttpResponse } from 'msw';

const QUOTA_PER_DAY = 1;

export const guestHandlers = [
  // Guest presign (file upload · SC-12 processCapture step 1)
  http.post('/api/file/presign', () => HttpResponse.json({
    url: 'https://mock-oss.example.com/upload-guest',
    image_url: 'https://mock-oss.example.com/guest-image.jpg',
  })),

  // Mock OSS upload (catch-all PUT to mock-oss · SC-12 step 2)
  http.put('https://mock-oss.example.com/upload-guest', () => new HttpResponse(null, { status: 200 })),

  // Analytics events (fire-and-forget · must not 404)
  http.post('/api/analytics/event', () => new HttpResponse(null, { status: 204 })),

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
    analyzedTotal: 1_204_312,
    retention7d: 0.47,
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
