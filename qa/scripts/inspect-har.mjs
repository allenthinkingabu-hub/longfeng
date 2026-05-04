#!/usr/bin/env node
/**
 * L4 HAR 抽查 · 验真链路真打 BE · 不是 mock
 *
 * 用法: node qa/scripts/inspect-har.mjs <har-file> [--write report.md]
 *
 * 检查:
 *   1. host 分布: localhost:9880 (gateway) 占比 vs localhost:9873 (FE)
 *   2. response status 分布: 200 vs 404 vs 500
 *   3. 真 BE 响应标志: Server / Date / X-Trace-Id / X-B3-TraceId header
 *   4. 列出每个 /api/* 请求的 (method, path, status, host, server-header)
 *   5. 验关键 endpoint 都打到了 (按本次 SC-11/12 期望)
 */
import { readFileSync, writeFileSync } from 'node:fs';

const harPath = process.argv[2];
if (!harPath) { console.error('Usage: inspect-har.mjs <har-file> [--write report.md]'); process.exit(1); }

const har = JSON.parse(readFileSync(harPath, 'utf8'));
const entries = har.log.entries;

const lines = [];
lines.push('# HAR 抽查报告');
lines.push('');
lines.push(`HAR: ${harPath}`);
lines.push(`Total requests: ${entries.length}`);
lines.push('');

// ── 1. Host 分布 ──────────────────────────────────────────────
const hostCount = new Map();
for (const e of entries) {
  const host = new URL(e.request.url).host;
  hostCount.set(host, (hostCount.get(host) ?? 0) + 1);
}
lines.push('## Host 分布');
lines.push('| host | count |');
lines.push('|---|---|');
for (const [host, c] of [...hostCount.entries()].sort((a, b) => b[1] - a[1])) {
  lines.push(`| ${host} | ${c} |`);
}
lines.push('');

// ── 2. /api/* 请求详情 ────────────────────────────────────────
const apiEntries = entries.filter(e => /\/api\//.test(e.request.url));
lines.push(`## /api/* 请求 (${apiEntries.length} 条)`);
lines.push('| method | path | status | host | content-type | server | trace-id |');
lines.push('|---|---|---|---|---|---|---|');
let realBeCount = 0;
let mockCount = 0;
let endpointHit = new Set();
for (const e of apiEntries) {
  const url = new URL(e.request.url);
  const headers = Object.fromEntries(e.response.headers.map(h => [h.name.toLowerCase(), h.value]));
  const server = headers['server'] ?? '';
  const traceId = headers['x-trace-id'] ?? headers['x-b3-traceid'] ?? '';
  const ct = headers['content-type'] ?? '';
  const isReal = url.host.includes('9880') || /tomcat|netty|undertow/i.test(server);
  if (isReal) realBeCount++;
  if (url.host.includes('9873') && !isReal) mockCount++;
  endpointHit.add(`${e.request.method} ${url.pathname.replace(/\/[a-f0-9-]{8,}/g, '/:id')}`);
  lines.push(`| ${e.request.method} | ${url.pathname} | ${e.response.status} | ${url.host} | ${ct.split(';')[0]} | ${server} | ${traceId.slice(0, 16)} |`);
}
lines.push('');

// ── 3. 真 BE vs mock 占比 ─────────────────────────────────────
lines.push('## 真 BE 占比 (监督铁证 第 1 项)');
const totalApi = apiEntries.length || 1;
const realPct = (realBeCount / totalApi * 100).toFixed(1);
lines.push(`真 BE 响应: ${realBeCount} / ${totalApi} = ${realPct}%`);
lines.push(`Mock-like 响应: ${mockCount} / ${totalApi}`);
lines.push(realPct >= 80 ? '✅ PASS · 真 BE 占比 ≥ 80%' : '❌ FAIL · 真 BE 占比 < 80%');
lines.push('');

// ── 4. 关键 endpoint 覆盖检查 ─────────────────────────────────
const required = [
  'GET /api/landing/samples',
  'GET /api/landing/kpi',
  'POST /api/analytics/event',
  'GET /api/guest/quota',
  'POST /api/guest/analyze',
  'POST /api/file/presign',
  'GET /api/ai/stream/:id',
];
lines.push('## 关键 endpoint 覆盖 (SC-11+12 必打)');
lines.push('| endpoint | hit? |');
lines.push('|---|---|');
let allHit = true;
for (const ep of required) {
  const hit = [...endpointHit].some(e => e.startsWith(ep.replace(':id', '')));
  if (!hit) allHit = false;
  lines.push(`| ${ep} | ${hit ? '✅' : '❌'} |`);
}
lines.push('');
lines.push(allHit ? '✅ PASS · 7 个核心 endpoint 全打' : '❌ FAIL · 有未打');
lines.push('');

// ── 5. 整体 verdict ──────────────────────────────────────────
const verdict = (realPct >= 80 && allHit) ? 'PASS' : 'FAIL';
lines.push(`## OVERALL: ${verdict}`);

const out = lines.join('\n');
console.log(out);
const writeIdx = process.argv.indexOf('--write');
if (writeIdx !== -1 && process.argv[writeIdx + 1]) {
  writeFileSync(process.argv[writeIdx + 1], out);
  console.error(`\n[written] ${process.argv[writeIdx + 1]}`);
}

process.exit(verdict === 'PASS' ? 0 : 1);
