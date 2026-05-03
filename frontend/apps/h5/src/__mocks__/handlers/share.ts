/**
 * MSW · anonymous-service share mock（B 轨 · 给 SC-09/SC-13 用）
 */
import { http, HttpResponse } from 'msw';

interface SharePayloadDecoded {
  kind: 'EXAM_DAY' | 'QUESTION' | 'REVIEW_NODE';
  sub: string;
  iss: string;
  exp: number;
  scope: string;
  sharerNickMasked: string;
}

function decodeShareToken(token: string): SharePayloadDecoded | null {
  try {
    const [, p] = token.split('.');
    const json = Buffer.from(p, 'base64').toString('utf-8');
    return JSON.parse(json);
  } catch {
    return null;
  }
}

export const shareHandlers = [
  http.get('/api/share/preview/:token', ({ params }) => {
    const decoded = decodeShareToken(String(params.token));
    if (!decoded) {
      return new HttpResponse(JSON.stringify({ error: 'INVALID_TOKEN' }), { status: 403 });
    }
    if (decoded.exp < Math.floor(Date.now() / 1000)) {
      return new HttpResponse(JSON.stringify({ error: 'EXPIRED' }), { status: 403 });
    }
    // mock: 真实后端会校验 HMAC sig · MSW 端只看是否能 base64 解出 payload
    return HttpResponse.json({
      type: decoded.kind,
      signature_valid: true,
      ttl_sec: decoded.exp - Math.floor(Date.now() / 1000),
      sharer_nick: decoded.sharerNickMasked,
      sharer_avatar_url: 'https://mock.cdn/avatar.png',
      shared_at: new Date(Date.now() - 3600_000).toISOString(),
      masked_payload: {
        qid_hash: 'hash-' + decoded.sub,
        subject: 'math',
        stem_preview: '已知函数 f(x)=x²……（注册查看完整）',
        thumbnail_url_masked: 'https://mock.cdn/thumb-masked.png',
        review_count: 3,
        node_stage_preview: 2,
      },
      upgrade_cta: {
        text: '注册查看 + 拥有自己的错题本',
        target_route: `/auth?redirect=/s/${params.token}`,
        can_claim: false,
      },
    });
  }),

  // SC-13 红线：匿名写 → 403
  http.post('/api/v1/wrongbook/items', ({ request }) => {
    const auth = request.headers.get('authorization');
    if (!auth || auth.includes('anonymous')) {
      return new HttpResponse(JSON.stringify({ error: 'ANONYMOUS_WRITE_FORBIDDEN' }), { status: 403 });
    }
    return HttpResponse.json({ id: 'item-ok' }, { status: 201 });
  }),
];
