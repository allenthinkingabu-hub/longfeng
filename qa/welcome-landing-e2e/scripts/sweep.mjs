#!/usr/bin/env node
/**
 * sweep.mjs · 一键跑欢迎页全漏斗 E2E sweep
 *
 * Usage:
 *   node sweep.mjs                # 跑全部
 *   node sweep.mjs --grep smoke   # 只跑 smoke
 *   node sweep.mjs --skip mockup-diff   # 跳过某 phase
 *
 * 输出：
 *   - 控制台 PASS/FAIL 汇总
 *   - 每个 TC 的 trace.zip → ../../e2e/reports/html/
 *   - 失败时截图 → ../../e2e/reports/artifacts/
 */
import { execSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { resolve } from 'node:path';

const E2E_DIR = resolve(import.meta.dirname, '../../../e2e');
if (!existsSync(E2E_DIR)) {
  console.error(`✘ e2e dir not found: ${E2E_DIR}`);
  process.exit(1);
}

const args = process.argv.slice(2);
const grep = args.find((a, i) => args[i - 1] === '--grep');
const skip = args.find((a, i) => args[i - 1] === '--skip')?.split(',') ?? [];

const PHASES = [
  // [name, env, command suffix]
  ['Phase A · sc-11 (P-LANDING)',          'mock-b', 'specs/sc-11.spec.ts'],
  ['Phase A · sc-11-extended (深度)',      'mock-b', 'specs/sc-11-extended.spec.ts'],
  ['Phase I · sc-12 (P-GUEST-CAPTURE)',    'mock-b', 'specs/sc-12.spec.ts'],
  ['Phase I · sc-12-extended (深度)',      'mock-b', 'specs/sc-12-extended.spec.ts'],
  ['Phase J · sc-p00 (P00 登录)',          'mock-b', 'specs/sc-p00.spec.ts'],
  ['Phase K · sc-funnel (跨页全漏斗)',     'mock-b', 'specs/sc-funnel.spec.ts'],
  ['Phase D · mockup-diff (3 页)',         'design', '--grep "@mockup-diff (P-LANDING|P-GUEST-CAPTURE)"'],
  ['Phase D · vrt-multi-check (3 页)',     'vrt',    '--grep "vrt-multi.*(P-LANDING|P-GUEST-CAPTURE|P00) "'],
];

const results = [];
let totalPass = 0, totalFail = 0;

for (const [name, track, target] of PHASES) {
  if (skip.includes(track) || skip.some((s) => name.includes(s))) {
    console.log(`⏭  跳过: ${name}`);
    continue;
  }
  if (grep && !name.toLowerCase().includes(grep.toLowerCase())) {
    continue;
  }

  console.log(`\n▶ 开始: ${name}`);
  const cmd = `E2E_TRACK=${track} BASE_URL=http://localhost:5173 npx playwright test ${target} --reporter=list 2>&1 | tail -5`;
  try {
    const out = execSync(cmd, { cwd: E2E_DIR, encoding: 'utf8' });
    // 解析 "X passed (Y.Zs)" / "X failed"
    const passMatch = out.match(/(\d+)\s+passed/);
    const failMatch = out.match(/(\d+)\s+failed/);
    const pass = passMatch ? parseInt(passMatch[1], 10) : 0;
    const fail = failMatch ? parseInt(failMatch[1], 10) : 0;
    totalPass += pass;
    totalFail += fail;
    const status = fail === 0 ? '✓' : '✘';
    results.push({ name, pass, fail, status });
    console.log(`${status} ${name}: ${pass} PASS · ${fail} FAIL`);
  } catch (err) {
    console.error(`✘ ${name}: 整体执行失败`);
    results.push({ name, pass: 0, fail: -1, status: '✘' });
    totalFail += 1;
  }
}

console.log('\n=== Sweep 汇总 ===');
console.log(`PASS: ${totalPass}`);
console.log(`FAIL: ${totalFail}`);
results.forEach((r) => console.log(`  ${r.status} ${r.name}: ${r.pass}/${r.pass + r.fail}`));
process.exit(totalFail > 0 ? 1 : 0);
