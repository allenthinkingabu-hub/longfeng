#!/usr/bin/env node
/**
 * L1 静态对账 · FE fetch endpoint × BE @RequestMapping × gateway route 三方对齐检查
 *
 * 用法: node qa/scripts/check-be-coverage.mjs [--write report-path]
 *
 * 出参:
 *   stdout 4 栏表 (FE caller / BE controller / gateway route / 状态)
 *   exit 0 = 全绿 · exit 1 = 任一栏缺/错
 *   --write 选项写入 markdown 报告
 */
import { readFileSync, writeFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';

const ROOT = process.cwd();

// ── 1. FE fetch endpoints (从 src/pages/ 提取) ─────────────────────
function gatherFeFetchEndpoints() {
  const pagesDir = join(ROOT, 'frontend/apps/h5/src/pages');
  const endpoints = new Map(); // path -> { method, callers: [file:line] }

  function walk(dir) {
    for (const entry of readdirSync(dir)) {
      const full = join(dir, entry);
      const st = statSync(full);
      if (st.isDirectory()) walk(full);
      else if (/\.(tsx?|jsx?)$/.test(entry)) scanFile(full);
    }
  }

  function scanFile(file) {
    const lines = readFileSync(file, 'utf8').split('\n');
    lines.forEach((line, idx) => {
      // 匹配 fetch('/api/...') 或 fetch(`/api/...`)
      const m = line.match(/fetch\(['"`]([^'"`]*\/api\/[^'"`?]+)/);
      if (!m) return;
      let path = m[1].split('?')[0];
      // 把 :id ${var} {qid} 等参数换成 :param
      path = path.replace(/\$\{[^}]+\}/g, ':id').replace(/:\w+/g, ':id');

      // 推断 method (粗略 · 看相邻 5 行有 method:)
      const ctx = lines.slice(idx, Math.min(idx + 6, lines.length)).join(' ');
      let method = 'GET';
      const mm = ctx.match(/method:\s*['"]([A-Z]+)['"]/);
      if (mm) method = mm[1];

      const key = `${method} ${path}`;
      if (!endpoints.has(key)) endpoints.set(key, { method, path, callers: [] });
      endpoints.get(key).callers.push(`${file.replace(ROOT + '/', '')}:${idx + 1}`);
    });
  }

  walk(pagesDir);
  return endpoints;
}

// ── 2. BE controller @RequestMapping (从 backend/*/controller/) ────
function gatherBeControllers() {
  const services = ['anonymous-service', 'file-service', 'ai-analysis-service',
                    'wrongbook-service', 'review-plan-service'];
  const controllers = []; // { service, file, classMapping, methods: [{verb, path}] }

  for (const svc of services) {
    const ctrlDir = join(ROOT, `backend/${svc}/src/main/java/com/longfeng/${svc.replace('-service','').replace('-','')}/controller`);
    let entries;
    try { entries = readdirSync(ctrlDir); } catch { continue; }
    for (const entry of entries) {
      if (!entry.endsWith('Controller.java')) continue;
      const file = join(ctrlDir, entry);
      const content = readFileSync(file, 'utf8');
      const classM = content.match(/@RequestMapping\(['"]([^'"]+)['"]\)/);
      const classMapping = classM ? classM[1] : '';

      const methods = [];
      const methodRegex = /@(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping)(?:\(([^)]*)\))?/g;
      let m;
      while ((m = methodRegex.exec(content))) {
        const verb = m[1].replace('Mapping', '').toUpperCase();
        let pathArg = m[2] ?? '';
        const pathM = pathArg.match(/['"]([^'"]+)['"]/);
        const path = pathM ? pathM[1] : '';
        const fullPath = (classMapping + path).replace(/\/+/g, '/').replace(/\/$/, '') || '/';
        methods.push({ verb, path: fullPath.replace(/\{[^}]+\}/g, ':id') });
      }
      controllers.push({ service: svc, file: file.replace(ROOT + '/', ''), classMapping, methods });
    }
  }
  return controllers;
}

// ── 3. Gateway routes (从 gateway/application.yml) ────────────────
function gatherGatewayRoutes() {
  const file = join(ROOT, 'backend/gateway/src/main/resources/application.yml');
  const content = readFileSync(file, 'utf8');
  const routes = []; // { id, uri, predicate }
  const routeRegex = /-\s+id:\s+(\S+)[\s\S]*?uri:\s+(\S+)[\s\S]*?predicates:\s*\n\s*-\s+Path=(\S+)/g;
  let m;
  while ((m = routeRegex.exec(content))) {
    routes.push({ id: m[1], uri: m[2], predicate: m[3] });
  }
  return routes;
}

// ── 4. Match FE endpoint to BE controller and gateway route ────────
function matchEndpoint(feKey, fePath, controllers, routes) {
  // BE match: any controller method with same verb + path == feKey
  let beHit = null;
  for (const ctrl of controllers) {
    for (const meth of ctrl.methods) {
      if (`${meth.verb} ${meth.path}` === feKey) {
        beHit = `${ctrl.service.replace('-service','')} · ${ctrl.file.split('/').pop()} ${meth.path}`;
        break;
      }
    }
    if (beHit) break;
  }
  // Gateway match: predicate /api/{prefix}/** matches fePath /api/{prefix}/...
  let gwHit = null;
  for (const r of routes) {
    const prefix = r.predicate.replace(/\/\*\*$/, '');
    if (fePath.startsWith(prefix + '/') || fePath === prefix) {
      gwHit = `${r.id} → ${r.uri}`;
      break;
    }
  }
  return { beHit, gwHit };
}

// ── 5. Render report ──────────────────────────────────────────────
function render(endpoints, controllers, routes) {
  const lines = [];
  lines.push('# L1 静态对账报告 · FE × BE × Gateway');
  lines.push('');
  lines.push(`生成时间: ${new Date().toISOString()}`);
  lines.push('');
  lines.push('| FE endpoint | BE controller | Gateway route | 状态 |');
  lines.push('|---|---|---|---|');

  let okCount = 0, missCount = 0;
  const sorted = Array.from(endpoints.values()).sort((a, b) => `${a.method} ${a.path}`.localeCompare(`${b.method} ${b.path}`));
  for (const ep of sorted) {
    const key = `${ep.method} ${ep.path}`;
    const { beHit, gwHit } = matchEndpoint(key, ep.path, controllers, routes);
    const status = beHit && gwHit ? '✅ 完整' : (beHit ? '⚠️ BE 有 · GW 缺' : (gwHit ? '⚠️ GW 有 · BE 缺' : '❌ 全缺'));
    if (beHit && gwHit) okCount++; else missCount++;
    lines.push(`| ${key} | ${beHit ?? '❌ 缺'} | ${gwHit ?? '❌ 缺'} | ${status} |`);
  }
  lines.push('');
  lines.push(`**汇总**: ${okCount} ✅ 完整 · ${missCount} ❌/⚠️ 待修`);
  lines.push('');
  lines.push('## FE Caller 详情');
  for (const ep of sorted) {
    lines.push(`- \`${ep.method} ${ep.path}\` · 调用方:`);
    for (const c of ep.callers) lines.push(`  - ${c}`);
  }
  lines.push('');
  lines.push('## BE Controller 全景');
  for (const ctrl of controllers) {
    lines.push(`- **${ctrl.service}** · ${ctrl.file}`);
    for (const m of ctrl.methods) lines.push(`  - \`${m.verb} ${m.path}\``);
  }
  lines.push('');
  lines.push('## Gateway Routes');
  for (const r of routes) lines.push(`- \`${r.predicate}\` → ${r.uri} (${r.id})`);

  return { text: lines.join('\n'), okCount, missCount };
}

// ── Main ──────────────────────────────────────────────────────────
const endpoints = gatherFeFetchEndpoints();
const controllers = gatherBeControllers();
const routes = gatherGatewayRoutes();
const { text, okCount, missCount } = render(endpoints, controllers, routes);

console.log(text);

const writeIdx = process.argv.indexOf('--write');
if (writeIdx !== -1 && process.argv[writeIdx + 1]) {
  writeFileSync(process.argv[writeIdx + 1], text);
  console.error(`\n[written] ${process.argv[writeIdx + 1]}`);
}

process.exit(missCount === 0 ? 0 : 1);
