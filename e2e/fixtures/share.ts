/**
 * S9 · 分享 token + 观察者邀请码 fixture · SC-09 / SC-13 / SC-15 用
 *
 * - HS256 share token（家长分享考试日 / 学生分享错题）
 * - 观察者一次性邀请码（6 位大写）
 *
 * 注意：本 fixture 仅生成 mock token 形状（B 轨），
 * A 轨上必须由 staging 后端 issue 真实签名 token（DevOps 在 §S5 准备）。
 */
import { createHmac, randomBytes } from 'node:crypto';

export type ShareKind = 'EXAM_DAY' | 'QUESTION' | 'REVIEW_NODE';

export interface SharePayload {
  kind: ShareKind;
  sub: string;           // shared subject id (qid / event id)
  iss: string;           // issuer user id
  exp: number;           // unix sec
  scope: 'READ_PREVIEW';
  sharerNickMasked: string;
}

const MOCK_SECRET = 'qa-mock-share-secret-do-not-use-in-prod';

/**
 * 生成 mock share token · 仅 B 轨 / mock-b 模式可用
 */
export function issueShareToken(payload: Omit<SharePayload, 'exp' | 'scope'> & { ttlSec?: number }): string {
  const exp = Math.floor(Date.now() / 1000) + (payload.ttlSec ?? 7 * 24 * 3600);
  const fullPayload: SharePayload = {
    kind: payload.kind,
    sub: payload.sub,
    iss: payload.iss,
    exp,
    scope: 'READ_PREVIEW',
    sharerNickMasked: payload.sharerNickMasked,
  };
  const headerB64 = base64Url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const payloadB64 = base64Url(JSON.stringify(fullPayload));
  const sig = createHmac('sha256', MOCK_SECRET).update(`${headerB64}.${payloadB64}`).digest();
  const sigB64 = base64Url(sig);
  return `${headerB64}.${payloadB64}.${sigB64}`;
}

/**
 * 篡改 share token（SC-13 验：篡改后必须 403）
 */
export function tamperShareToken(token: string): string {
  const [h, p, s] = token.split('.');
  // 改 payload 里的 sub 字段，但保持 sig 不变 → 后端必须 reject
  const payloadStr = Buffer.from(p, 'base64url').toString('utf-8');
  const payload = JSON.parse(payloadStr);
  payload.sub = 'tampered-sub-id';
  const tamperedPayload = base64Url(JSON.stringify(payload));
  return `${h}.${tamperedPayload}.${s}`;
}

/**
 * 生成观察者邀请码（SC-15）· 6 位大写字母+数字
 */
export function issueObserverToken(opts: { studentId: string; role: 'PARENT' | 'TEACHER'; ttlDays?: 30 | 90 }): {
  inviteCode: string;
  observerJwt: string;
  ttlSec: number;
} {
  const ttlSec = (opts.ttlDays ?? 30) * 86400;
  const inviteCode = randomBytes(4).toString('hex').toUpperCase().slice(0, 6).padEnd(6, 'X');
  const observerJwt = issueShareToken({
    kind: 'REVIEW_NODE', // 复用 jwt 形状
    sub: opts.studentId,
    iss: opts.studentId,
    sharerNickMasked: '张*',
    ttlSec,
  });
  return { inviteCode, observerJwt, ttlSec };
}

function base64Url(input: string | Buffer): string {
  return Buffer.from(input).toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
