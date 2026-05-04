/**
 * Phase D4 · D 机制 · PR 自动三联截图
 *
 * 用法 (在 GH Actions runner 上):
 *   pnpm tsx scripts/pr-screenshots.ts <PR_NUMBER>
 *
 * 步骤:
 *   1. gh pr diff <PR> 拿改动文件列表 · 找改的 H5 page (frontend/apps/h5/src/pages/[A-Z]*)
 *   2. page dir → page_id 映射 (同 design-precommit.sh)
 *   3. 对每个改动 page 跑 mockup-vs-impl + vrt-multi · 收集截图
 *   4. 生成 markdown 表 · 写到 e2e/reports/pr-screenshots.md
 *   5. CI workflow 用 gh api comment 上传到 PR
 *
 * Plan: docs/DESIGN-AUDIT-SYSTEM-PLAN.md §Layer3 D
 */
import { execSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

const REPO_ROOT = path.resolve(__dirname, '..');
const REPORT_FILE = path.join(REPO_ROOT, 'e2e/reports/pr-screenshots.md');

const PAGE_MAP: Record<string, { id: string; mockup: string; route: string }> = {
  Landing:        { id: 'P-LANDING',       mockup: '_archive/14_landing.html',          route: '/welcome' },
  Home:           { id: 'P-HOME',          mockup: '_archive/01_home.html',             route: '/' },
  Capture:        { id: 'P02',             mockup: '_archive/02_capture.html',          route: '/capture' },
  Analyzing:      { id: 'P03',             mockup: '_archive/03_analyzing.html',        route: '/analyzing/demo' },
  Result:         { id: 'P04',             mockup: '_archive/04_result.html',           route: '/result/demo' },
  List:           { id: 'P05',             mockup: '_archive/05_wrongbook_list.html',   route: '/wrongbook' },
  Detail:         { id: 'P06',             mockup: '_archive/06_wrongbook_detail.html', route: '/wrongbook/demo' },
  ReviewToday:    { id: 'P07',             mockup: '_archive/07_review_today.html',     route: '/review' },
  ReviewExec:     { id: 'P08',             mockup: '_archive/08_review_exec.html',      route: '/review/demo/exec' },
  ReviewDone:     { id: 'P09',             mockup: '_archive/09_review_done.html',      route: '/review/demo/done' },
  CalendarMonth:  { id: 'P10',             mockup: '_archive/10_calendar_month.html',   route: '/calendar' },
  EventDetail:    { id: 'P11',             mockup: '_archive/11_event_detail.html',     route: '/event/demo' },
  Notifications:  { id: 'P12',             mockup: '_archive/12_notifications.html',    route: '/notifications' },
  Settings:       { id: 'P13',             mockup: '_archive/13_settings.html',         route: '/me/settings' },
  GuestCapture:   { id: 'P-GUEST-CAPTURE', mockup: '_archive/15_guest_capture.html',    route: '/guest/capture' },
  Shared:         { id: 'P-SHARED',        mockup: '_archive/16_shared.html',           route: '/s/demo-token' },
};

const prNumber = process.argv[2];
if (!prNumber) {
  console.error('Usage: pnpm tsx scripts/pr-screenshots.ts <PR_NUMBER>');
  process.exit(1);
}

console.log(`📸 Generating PR #${prNumber} design screenshots...`);

// Step 1 · 拿改动文件
let changedFiles: string[];
try {
  changedFiles = execSync(`gh pr diff ${prNumber} --name-only`, { encoding: 'utf8' })
    .split('\n').filter(Boolean);
} catch (e) {
  console.error(`❌ gh pr diff failed: ${(e as Error).message}`);
  process.exit(1);
}

// Step 2 · 提取 changed page dir
const changedPages = new Set<string>();
const PAGE_RE = /^frontend\/apps\/h5\/src\/pages\/([A-Z][^/]+)\//;
for (const f of changedFiles) {
  const m = f.match(PAGE_RE);
  if (m) changedPages.add(m[1]);
}

if (changedPages.size === 0) {
  console.log('No H5 page changes detected · skip');
  fs.mkdirSync(path.dirname(REPORT_FILE), { recursive: true });
  fs.writeFileSync(REPORT_FILE, '# PR Design Screenshots\n\n_No H5 page changes_\n');
  process.exit(0);
}

console.log(`Changed pages: ${[...changedPages].join(', ')}`);

// Step 3 · 跑 mockup-diff (假定 vite 已启 · CI workflow setup) · 截图存到 e2e/reports/mockup-diff/
console.log('Running mockup-vs-impl + vrt-multi...');
try {
  execSync('cd e2e && pnpm e2e:mockup-diff', { stdio: 'inherit' });
} catch {
  console.warn('mockup-diff returned non-zero (expected for fail pages)');
}

// Step 4 · 生成 markdown 报告
const lines: string[] = [];
lines.push('# 🎨 Design Audit · PR Screenshots');
lines.push('');
lines.push(`**PR**: #${prNumber} · **Pages changed**: ${changedPages.size}`);
lines.push('');
lines.push('Per CLAUDE.md §2.11 自检 design-review · D 机制自动生成下表 · 设计师/PM review 三联截图后打 `designer-approved` label。');
lines.push('');

for (const dir of changedPages) {
  const meta = PAGE_MAP[dir];
  if (!meta) {
    lines.push(`## ${dir} (unknown · 跳过 audit)`);
    lines.push('');
    continue;
  }
  lines.push(`## ${meta.id} · ${dir} (${meta.route})`);
  lines.push('');
  lines.push('| 截图 | 路径 |');
  lines.push('|---|---|');
  lines.push(`| 实现 (impl) | \`e2e/reports/mockup-diff/${meta.id}-impl.png\` |`);
  lines.push(`| 高保真 (mockup) | \`design/mockups/wrongbook/${meta.mockup}\` |`);
  lines.push(`| Pixel diff | \`e2e/reports/mockup-diff/${meta.id}-diff.png\` |`);
  lines.push('');
  lines.push(`> 详细 vision 报告: \`e2e/reports/design-review/${meta.id}.json\` (跑 design-reviewer agent 生成)`);
  lines.push('');
  lines.push('---');
  lines.push('');
}

lines.push('## Sign-off Checklist (设计师/PM 必勾)');
lines.push('');
lines.push('- [ ] 实现页跟 mockup HTML 视觉一致 (允许 5% pixel diff)');
lines.push('- [ ] iPad/desktop viewport 不嵌套 mockup chrome');
lines.push('- [ ] 文案/CTA/block 顺序对齐 mockup');
lines.push('- [ ] 颜色 token 用 STYLE-TRUTH.md 新 iOS HIG 体系');
lines.push('- [ ] a11y · prefers-reduced-motion 兜底');
lines.push('');
lines.push('全部勾完 · 打 `designer-approved` label · 否则 G 机制阻断 merge。');
lines.push('紧急情况: `design-emergency-bypass` label 可绕过 (留 audit 痕迹)。');

fs.mkdirSync(path.dirname(REPORT_FILE), { recursive: true });
fs.writeFileSync(REPORT_FILE, lines.join('\n'));
console.log(`✓ Report written to ${REPORT_FILE}`);
