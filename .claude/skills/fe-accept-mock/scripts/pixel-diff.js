#!/usr/bin/env node
/**
 * Pixel diff: mockup HTML vs live dev server
 * Usage: node pixel-diff.js <mockup_html_path> <impl_url> [output_dir]
 * Output: JSON to stdout
 *
 * Requires: playwright, pixelmatch, pngjs
 * Install:  pnpm add -D @playwright/test pixelmatch pngjs
 */

const path = require('path');
const fs = require('fs');

const [, , mockupPath, implUrl, outputDir = 'design/tasks/acceptance'] = process.argv;

if (!mockupPath || !implUrl) {
  console.error('Usage: node pixel-diff.js <mockup_html_path> <impl_url> [output_dir]');
  process.exit(1);
}

const VIEWPORT = { width: 390, height: 844 };
const SETTLE_MS = 800;

async function run() {
  const { chromium } = require('@playwright/test');
  const { PNG } = require('pngjs');
  const pixelmatch = require('pixelmatch').default;

  fs.mkdirSync(outputDir, { recursive: true });

  const browser = await chromium.launch();

  async function capture(url) {
    const page = await browser.newPage();
    await page.setViewportSize(VIEWPORT);
    await page.goto(url, { waitUntil: 'networkidle', timeout: 30000 });
    await page.waitForTimeout(SETTLE_MS);
    const buf = await page.screenshot({ fullPage: false });
    await page.close();
    return buf;
  }

  const mockupUrl = `file://${path.resolve(mockupPath)}`;
  const [mockupBuf, implBuf] = await Promise.all([
    capture(mockupUrl),
    capture(implUrl),
  ]);

  await browser.close();

  const mockupPng = PNG.sync.read(mockupBuf);
  const implPng = PNG.sync.read(implBuf);

  const width = Math.min(mockupPng.width, implPng.width);
  const height = Math.min(mockupPng.height, implPng.height);

  const diff = new PNG({ width, height });

  const numDiffPixels = pixelmatch(
    mockupPng.data,
    implPng.data,
    diff.data,
    width,
    height,
    { threshold: 0.1 }
  );

  const totalPixels = width * height;
  const diffPct = parseFloat(((numDiffPixels / totalPixels) * 100).toFixed(2));

  const mockupOut = path.join(outputDir, 'mockup.png');
  const implOut = path.join(outputDir, 'impl.png');
  const diffOut = path.join(outputDir, 'diff.png');

  fs.writeFileSync(mockupOut, mockupBuf);
  fs.writeFileSync(implOut, implBuf);
  fs.writeFileSync(diffOut, PNG.sync.write(diff));

  console.log(JSON.stringify({
    diff_pct: diffPct,
    diff_pixels: numDiffPixels,
    total_pixels: totalPixels,
    viewport: VIEWPORT,
    screenshots: {
      mockup: mockupOut,
      impl: implOut,
      diff: diffOut,
    },
  }, null, 2));
}

run().catch(err => {
  console.error('pixel-diff failed:', err.message);
  process.exit(1);
});
