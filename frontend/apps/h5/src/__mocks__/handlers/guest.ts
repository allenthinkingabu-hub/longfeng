/**
 * MSW · anonymous-service guest mock（B 轨 · 给 SC-11/SC-12/SC-14 用）
 */
import { http, HttpResponse } from 'msw';

const QUOTA_PER_DAY = 1;

export const guestHandlers = [
  // Guest presign (file upload · SC-12 processCapture step 1)
  // BUG-LF-20 fix · 改成 envelope 形态 (跟真 BE response shape 对齐: { code, message, data: {...} })
  // x-e2e-fail-presign=1 → 500 · 验 ERROR overlay 'PRESIGN' 分支
  http.post('/api/file/presign', ({ request }) => {
    if (request.headers.get('x-e2e-fail-presign') === '1') {
      return new HttpResponse(JSON.stringify({ code: 5001, message: 'presign service unavailable' }), {
        status: 500,
        headers: { 'Content-Type': 'application/json' },
      });
    }
    return HttpResponse.json({
      code: 0,
      message: 'OK',
      data: {
        url: 'https://mock-oss.example.com/upload-guest',
        image_url: 'https://mock-oss.example.com/guest-image.jpg',
        object_key: 'wrongbook/0/202605/0/mock-guest.jpg',
        expires_in_sec: '900',
        method: 'PUT',
      },
      trace_id: 'mock-trace-presign',
    });
  }),

  // Mock OSS upload (catch-all PUT to mock-oss · SC-12 step 2)
  // x-e2e-fail-upload=1 → 503 · 验 ERROR overlay 'UPLOAD' 分支
  http.put('https://mock-oss.example.com/upload-guest', ({ request }) => {
    if (request.headers.get('x-e2e-fail-upload') === '1') {
      return new HttpResponse(null, { status: 503 });
    }
    return new HttpResponse(null, { status: 200 });
  }),

  // Analytics events (fire-and-forget · must not 404)
  http.post('/api/analytics/event', () => new HttpResponse(null, { status: 204 })),

  // P-LANDING samples（30/min IP 限流由 BE-05 IT 兜 · 这里只 mock happy path）
  // SC-11 异常 · header x-e2e-samples-fail=1 → 返 500 · 触发 DEGRADED 文案
  http.get('/api/landing/samples', ({ request }) => {
    const cookie = request.headers.get('cookie') ?? '';
    const headerFail = request.headers.get('x-e2e-samples-fail') === '1';
    const cookieFail = /(?:^|;\s*)lf_e2e_samples_fail=1/.test(cookie);
    if (headerFail || cookieFail) {
      return new HttpResponse(JSON.stringify({ error: 'samples_failed' }), {
        status: 500,
        headers: { 'Content-Type': 'application/json' },
      });
    }
    // Round 4 富化 (BUG-LF-10) · 8 字段 + aiAnalysisMock (匹配 Landing.tsx SampleCard interface)
    return HttpResponse.json({
      bucket: 'default',
      samples: [
        {
          id: 'sample-1',
          subject: 'math',
          stemPreview: '已知函数 f(x)=x²-4x+3，求顶点坐标',
          thumbnailUrl: '/mock/math.png',
          formula: 'f(x)=x²-4x+3',
          errorReason: '错因 · 配方法符号错',
          kpLabel: '知识点 · 二次函数顶点式',
          tagLabel: 'T1 · 1h 后复习',
          aiAnalysisMock: {
            reason: '配方法时常数项符号易丢 · 应配 (x-2)²-1 · 顶点 (2,-1)',
            stepsCount: 3,
            hint: '记忆口诀: 一移二配三还原',
          },
        },
        {
          id: 'sample-2',
          subject: 'physics',
          stemPreview: '斜面 θ=30° 滑块沿斜面下滑，求加速度',
          thumbnailUrl: '/mock/physics.png',
          formula: 'a = g(sinθ - μcosθ)',
          errorReason: '错因 · 分解方向选错',
          kpLabel: '知识点 · 共点力 / 斜面受力分解',
          tagLabel: 'T2 · 1d 后复习',
          aiAnalysisMock: {
            reason: '应沿斜面方向分解重力 · 分量 mg·sinθ 才是下滑力',
            stepsCount: 4,
            hint: '画力图先分解后列方程',
          },
        },
        {
          id: 'sample-3',
          subject: 'english',
          stemPreview: 'If I ___ you, I would take the chance.',
          thumbnailUrl: '/mock/english.png',
          formula: 'If I ___ you, I would ...',
          errorReason: '错因 · 虚拟语气时态',
          kpLabel: '知识点 · 虚拟语气 / 与现在事实相反',
          tagLabel: 'T3 · 3d 后复习',
          aiAnalysisMock: {
            reason: '与现在事实相反 · be 动词统一用 were · 答案是 were',
            stepsCount: 2,
            hint: '虚拟语气主从呼应: were / would / could',
          },
        },
      ],
    });
  }),

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

  // SC-12 异常 · header x-e2e-quota-out=1 → quotaRemaining=0 触发"额度耗尽"文案
  http.get('/api/guest/quota', ({ request }) => {
    const cookie = request.headers.get('cookie') ?? '';
    const headerOut = request.headers.get('x-e2e-quota-out') === '1';
    const cookieOut = /(?:^|;\s*)lf_e2e_quota_out=1/.test(cookie);
    const quotaOut = headerOut || cookieOut;
    return HttpResponse.json({
      quotaRemaining: quotaOut ? 0 : QUOTA_PER_DAY,
      quotaResetAt: new Date(Date.now() + 86_400_000).toISOString(),
    });
  }),

  // Guest analyze (simplified · returns task id)
  // x-e2e-fail-analyze=1 → 503 · 验 ERROR overlay 'ANALYZE' 分支
  http.post('/api/guest/analyze', ({ request }) => {
    if (request.headers.get('x-e2e-fail-analyze') === '1') {
      return new HttpResponse(JSON.stringify({ code: 5003, message: 'ai-analysis service unavailable' }), {
        status: 503,
        headers: { 'Content-Type': 'application/json' },
      });
    }
    return HttpResponse.json({
      guest_session_id: 'guest-mock-001',
      task_id: 'guest-task-001',
      status: 'ANALYZING',
    });
  }),

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
