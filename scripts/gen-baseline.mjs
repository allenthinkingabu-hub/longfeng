// Sd.10 · 视觉 baseline 生成 · Playwright headless chromium 1440×900
// 输出：design/system/screenshots/baseline/P-XX-name.png
//      design/system/screenshots/baseline/manifest.yml
import { chromium } from 'playwright';
import { promises as fs } from 'fs';
import { createHash } from 'crypto';
import path from 'path';

const MOCKUP_DIR = '../design/mockups/wrongbook';
const BASELINE_DIR = '../design/system/screenshots/baseline';

// P-XX 映射：mockup HTML → screen_id / sc_ref / category / threshold
// category: list|rich_interactive · threshold 0.03|0.08（§16.2 Sd.10 + 决策 C）
const MANIFEST = [
  { screen_id: 'P-01-login',             file: '00_login.html',             category: 'list',             threshold: 0.03, sc_ref: ['SC-01'] },
  { screen_id: 'P-02-home',              file: '01_home.html',              category: 'rich_interactive', threshold: 0.08, sc_ref: ['SC-07', 'SC-09'] },
  { screen_id: 'P-03-capture',           file: '02_capture.html',           category: 'rich_interactive', threshold: 0.08, sc_ref: ['SC-01', 'SC-11'] },
  { screen_id: 'P-04-analyzing',         file: '03_analyzing.html',         category: 'rich_interactive', threshold: 0.08, sc_ref: ['SC-05', 'SC-06'] },
  { screen_id: 'P-05-result',            file: '04_result.html',            category: 'rich_interactive', threshold: 0.08, sc_ref: ['SC-05'] },
  { screen_id: 'P-06-wrongbook-list',    file: '05_wrongbook_list.html',    category: 'list',             threshold: 0.03, sc_ref: ['SC-02'] },
  { screen_id: 'P-07-wrongbook-detail',  file: '06_wrongbook_detail.html',  category: 'rich_interactive', threshold: 0.08, sc_ref: ['SC-03', 'SC-04'] },
  { screen_id: 'P-08-review-today',      file: '07_review_today.html',      category: 'list',             threshold: 0.03, sc_ref: ['SC-07'] },
  { screen_id: 'P-09-review-exec',       file: '08_review_exec.html',       category: 'rich_interactive', threshold: 0.08, sc_ref: ['SC-08'] },
  { screen_id: 'P-10-review-done',       file: '09_review_done.html',       category: 'rich_interactive', threshold: 0.08, sc_ref: ['SC-08'] },
  { screen_id: 'P-11-calendar-month',    file: '10_calendar_month.html',    category: 'rich_interactive', threshold: 0.08, sc_ref: ['SC-10'] },
  { screen_id: 'P-12-event-detail',      file: '11_event_detail.html',      category: 'list',             threshold: 0.03, sc_ref: ['SC-10'] },
  { screen_id: 'P-13-notifications',     file: '12_notifications.html',     category: 'list',             threshold: 0.03, sc_ref: [] },
  { screen_id: 'P-14-settings',          file: '13_settings.html',          category: 'list',             threshold: 0.03, sc_ref: [] },
  { screen_id: 'P-15-landing',           file: '14_landing.html',           category: 'rich_interactive', threshold: 0.08, sc_ref: ['SC-12'] },
  { screen_id: 'P-16-guest-capture',     file: '15_guest_capture.html',     category: 'rich_interactive', threshold: 0.08, sc_ref: ['SC-12'] },
  { screen_id: 'P-17-shared',            file: '16_shared.html',            category: 'rich_interactive', threshold: 0.08, sc_ref: [] },
  { screen_id: 'P-18-welcomeback',       file: '17_welcomeback.html',       category: 'rich_interactive', threshold: 0.08, sc_ref: [] },
  { screen_id: 'P-19-observer',          file: '18_observer.html',          category: 'rich_interactive', threshold: 0.08, sc_ref: ['SC-09', 'SC-14'] },
];

async function sha256File(filePath) {
  const buf = await fs.readFile(filePath);
  return createHash('sha256').update(buf).digest('hex');
}

async function ensureDir(dir) {
  await fs.mkdir(dir, { recursive: true });
}

async function main() {
  await ensureDir(BASELINE_DIR);
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    deviceScaleFactor: 1,
    colorScheme: 'light',
  });
  const page = await context.newPage();

  const manifest = { schema_version: '1.0', generated_at: new Date().toISOString(), screenshots: [] };

  for (const item of MANIFEST) {
    const htmlPath = path.resolve(MOCKUP_DIR, item.file);
    const url = 'file://' + htmlPath;
    const outPath = path.join(BASELINE_DIR, `${item.screen_id}.png`);
    try {
      await page.goto(url, { waitUntil: 'networkidle', timeout: 15000 });
      await page.waitForTimeout(500); // 等动画稳定
      await page.screenshot({ path: outPath, fullPage: true });
      const sha = await sha256File(outPath);
      manifest.screenshots.push({
        screen_id: item.screen_id,
        path: `baseline/${item.screen_id}.png`,
        category: item.category,
        threshold: item.threshold,
        sc_ref: item.sc_ref,
        sha256: sha,
      });
      console.log(`✓ ${item.screen_id} · ${sha.slice(0, 12)}...`);
    } catch (err) {
      console.error(`✗ ${item.screen_id} · ${err.message}`);
      manifest.screenshots.push({
        screen_id: item.screen_id,
        path: `baseline/${item.screen_id}.png`,
        category: item.category,
        threshold: item.threshold,
        sc_ref: item.sc_ref,
        sha256: '',
        error: err.message,
      });
    }
  }

  await browser.close();

  // YAML manifest 写入
  const yaml = [
    '# Sd.10 · 视觉 baseline manifest',
    '# 落地计划 §16.2 Sd.10 + §4.6 v1.8',
    `# 由 scripts/gen-baseline.mjs 自动生成 · DO NOT HAND EDIT`,
    `schema_version: "${manifest.schema_version}"`,
    `generated_at: "${manifest.generated_at}"`,
    'screenshots:',
    ...manifest.screenshots.flatMap((s) => [
      `  - screen_id: "${s.screen_id}"`,
      `    path: "${s.path}"`,
      `    category: ${s.category}`,
      `    threshold: ${s.threshold}`,
      `    sc_ref: [${s.sc_ref.map((x) => `"${x}"`).join(', ')}]`,
      `    sha256: "${s.sha256}"`,
      ...(s.error ? [`    error: "${s.error}"`] : []),
    ]),
  ].join('\n');
  await fs.writeFile(path.join(BASELINE_DIR, 'manifest.yml'), yaml + '\n');
  console.log(`manifest: ${BASELINE_DIR}/manifest.yml`);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
