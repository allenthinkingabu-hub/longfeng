/**
 * MSW · anonymous-service share mock（B 轨 · 给 SC-09/SC-13 用）
 *
 * 重要：本 handler 运行在浏览器（MSW Service Worker），不能用 Node Buffer。
 *  - issueShareToken 用 base64url（URL-safe，无填充）
 *  - 浏览器侧用 atob + URL-safe → standard base64 转换 + TextDecoder('utf-8')
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

/** base64url → utf-8 字符串（浏览器安全 · 不依赖 Node Buffer / 不依赖 escape） */
function base64UrlDecode(input: string): string {
  // 1) URL-safe → 标准 base64
  let b64 = input.replace(/-/g, '+').replace(/_/g, '/');
  // 2) 补齐填充
  const pad = b64.length % 4;
  if (pad === 2) b64 += '==';
  else if (pad === 3) b64 += '=';
  else if (pad === 1) throw new Error('invalid base64url length');
  // 3) atob 得到 latin1 二进制字符串 → 转 Uint8Array → TextDecoder 解 utf-8
  const binStr = atob(b64);
  const bytes = new Uint8Array(binStr.length);
  for (let i = 0; i < binStr.length; i++) bytes[i] = binStr.charCodeAt(i);
  return new TextDecoder('utf-8').decode(bytes);
}

function decodeShareToken(token: string): SharePayloadDecoded | null {
  try {
    const [, p] = token.split('.');
    if (!p) return null;
    const json = base64UrlDecode(p);
    return JSON.parse(json);
  } catch {
    return null;
  }
}

/** B 轨 mock HMAC 验证：只接受由 issueShareToken 生成的未篡改 token */
function verifyShareTokenIntegrity(token: string): boolean {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return false;
    const [, payloadB64] = parts;
    const payloadStr = base64UrlDecode(payloadB64);
    const payload = JSON.parse(payloadStr);
    // 篡改 token 特征：tamperShareToken 把 sub 改成 'tampered-sub-id'
    if (payload.sub === 'tampered-sub-id') return false;
    return true;
  } catch {
    return false;
  }
}

export const shareHandlers = [
  // SC-13 · SharedPage 请求 /api/share/:token（不含 preview 前缀）
  http.get('/api/share/:token', ({ params }) => {
    const token = String(params.token);
    if (!verifyShareTokenIntegrity(token)) {
      return new HttpResponse(JSON.stringify({ error: 'TOKEN_INVALID' }), { status: 403 });
    }
    const decoded = decodeShareToken(token);
    if (!decoded) {
      return new HttpResponse(JSON.stringify({ error: 'INVALID_TOKEN' }), { status: 403 });
    }
    if (decoded.exp < Math.floor(Date.now() / 1000)) {
      return new HttpResponse(JSON.stringify({ error: 'EXPIRED' }), { status: 403 });
    }
    // 合法 token：正常返回
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
