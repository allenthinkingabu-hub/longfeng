#!/usr/bin/env node
/**
 * Check testid visibility in a rendered page
 * Usage: node check-testids.js <impl_url> <testids_json_array>
 * Output: JSON array to stdout
 *
 * Requires: playwright
 * Install:  pnpm add -D @playwright/test
 */

const [, , implUrl, testidsJson] = process.argv;

if (!implUrl || !testidsJson) {
  console.error('Usage: node check-testids.js <impl_url> \'["testid1","testid2"]\'');
  process.exit(1);
}

let testids;
try {
  testids = JSON.parse(testidsJson);
} catch {
  console.error('testids must be a valid JSON array');
  process.exit(1);
}

const VIEWPORT = { width: 390, height: 844 };
const SETTLE_MS = 800;

async function run() {
  const { chromium } = require('@playwright/test');

  const browser = await chromium.launch();
  const page = await browser.newPage();
  await page.setViewportSize(VIEWPORT);
  await page.goto(implUrl, { waitUntil: 'networkidle', timeout: 30000 });
  await page.waitForTimeout(SETTLE_MS);

  const results = [];
  for (const testid of testids) {
    const locator = page.locator(`[data-testid="${testid}"]`);
    const count = await locator.count();
    const visible = count > 0 ? await locator.first().isVisible().catch(() => false) : false;
    results.push({ testid, present: count > 0, visible });
  }

  await browser.close();
  console.log(JSON.stringify(results, null, 2));
}

run().catch(err => {
  console.error('check-testids failed:', err.message);
  process.exit(1);
});
