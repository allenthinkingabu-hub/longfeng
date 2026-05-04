/**
 * MSW · auth-service mock（B 轨 · 给 SC-12/P00 wechat-login 用）
 * spec: design/system/pages/P00.spec.md §4 §5
 */
import { http, HttpResponse } from 'msw';

export const authHandlers = [
  // POST /api/auth/wechat-login · 微信登录（B 轨 happy path）
  // dev_code_alice → NORMAL user · dev_code_vip → VIP user
  http.post('/api/auth/wechat-login', async ({ request }) => {
    const body = await request.json() as {
      wx_code?: string;
      device_fp?: string;
      consent_accepted?: boolean;
    };

    if (!body?.consent_accepted) {
      return new HttpResponse(
        JSON.stringify({ error: 'CONSENT_REQUIRED' }),
        { status: 400, headers: { 'Content-Type': 'application/json' } },
      );
    }

    const wxCode = body?.wx_code ?? '';
    const isVipPlus = wxCode.includes('vipplus');
    const isVip = !isVipPlus && wxCode.includes('vip');
    const tier = isVipPlus ? 'VIP_PLUS' : isVip ? 'VIP' : 'NORMAL';
    const studentId = `mock-student-${tier.toLowerCase()}`;

    // Forge a minimal JWT-shaped token (not real signature · dev/mock only)
    const b64u = (s: string) =>
      btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
    const header = b64u('{"alg":"HS256","typ":"JWT"}');
    const payload = b64u(JSON.stringify({
      sub: studentId,
      scope: 'USER',
      tier,
      exp: Math.floor(Date.now() / 1000) + 24 * 3600,
    }));
    const accessToken = `${header}.${payload}.mocksig`;

    return HttpResponse.json({
      access_token: accessToken,
      refresh_token: `mock-refresh-${studentId}`,
      student_id: studentId,
      is_new_user: wxCode === 'dev_code_new_user',
      expires_at: Math.floor(Date.now() / 1000) + 24 * 3600,
    });
  }),
];
