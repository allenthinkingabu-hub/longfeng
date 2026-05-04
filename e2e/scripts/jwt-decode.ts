#!/usr/bin/env tsx
/**
 * JWT decode util · 命令行 + spec 复用
 *
 * 用法：
 *   tsx e2e/scripts/jwt-decode.ts <token>
 *   或在 spec 里 import { decodeJwt } from '../scripts/jwt-decode'
 *
 * 不验签 · 仅解 base64url payload · 用于 hybrid 联调验 BE 真签的 claim 内容
 */

export function decodeJwt(token: string): {
  header: Record<string, unknown>;
  payload: Record<string, unknown>;
  signaturePart: string;
} | null {
  const parts = token.split('.');
  if (parts.length !== 3) return null;

  const b64urlDecode = (s: string): string => {
    const pad = s.length % 4 === 0 ? '' : '='.repeat(4 - (s.length % 4));
    const b64 = (s + pad).replace(/-/g, '+').replace(/_/g, '/');
    return Buffer.from(b64, 'base64').toString('utf8');
  };

  try {
    const header = JSON.parse(b64urlDecode(parts[0])) as Record<string, unknown>;
    const payload = JSON.parse(b64urlDecode(parts[1])) as Record<string, unknown>;
    return { header, payload, signaturePart: parts[2] };
  } catch {
    return null;
  }
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const token = process.argv[2];
  if (!token) {
    console.error('Usage: tsx e2e/scripts/jwt-decode.ts <token>');
    process.exit(1);
  }
  const decoded = decodeJwt(token);
  if (!decoded) {
    console.error('[FAIL] not a valid JWT (3-part dot-separated)');
    process.exit(2);
  }
  console.log(JSON.stringify(decoded, null, 2));
}
