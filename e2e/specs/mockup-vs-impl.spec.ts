/**
 * Phase D1 · B 机制 · Mockup HTML vs 实现页 像素 diff
 *
 * 跑法:
 *   pnpm e2e:mockup-diff           # 跑 19 页 · 容差 5%
 *   pnpm e2e:mockup-diff -- --grep P-LANDING
 *
 * 原理:
 *   1. 截 mockup HTML (file://) · 取 [data-page-content] 区域 (跳过 mockup chrome)
 *   2. 截 实现页 (http://localhost:5173) · 取 [data-testid="landing-page"] 区域 (page root)
 *   3. pixelmatch 对比 · 容差 5% (允许 React vs static HTML 字体微差)
 *   4. 失败 attach 三方截图到报告
 *
 * F 治本依赖:
 *   - mockup HTML 必须含 [data-page-content] (Phase D1 仅 P-LANDING 已加)
 *   - 缺 attr 的页 → 跳过该 test (verdict=AMBIGUOUS · log 不报 fail)
 *
 * Tag: @mockup-diff
 * Plan: docs/DESIGN-AUDIT-SYSTEM-PLAN.md §Layer1 B
 */
import { test, expect } from '@playwright/test';
import pixelmatch from 'pixelmatch';
import { PNG } from 'pngjs';
import path from 'node:path';
import fs from 'node:fs';

const REPO_ROOT = path.resolve(__dirname, '../..');
const MOCKUP_DIR = path.join(REPO_ROOT, 'design/mockups/wrongbook');
const REPORTS_DIR = path.join(__dirname, '../reports/mockup-diff');
const BASE_URL = process.env.BASE_URL ?? 'http://localhost:5173';

interface PageDef {
  id: string;
  mockup: string;        // 14_landing.html
  route: string;         // /welcome
  pageRootSelector: string; // implementation page root testid
  tolerance: number;     // 0.05 = 5% pixel diff allowed
}

// CLAUDE.md 项目铁律: archive 已有的页面 → _archive 是 canonical 真权威 (current 文件可能是 PM 后期改的偏离版本)
const PAGES: PageDef[] = [
  { id: 'P-LANDING',     mockup: '_archive/14_landing.html',  route: '/welcome',           pageRootSelector: '[data-testid="landing-page"]', tolerance: 0.05 },
  // Phase D1: 仅启用 P-LANDING 验证机制
  // Phase D2: 批量 enable 19 页（每页 mockup 加 data-page-content 后）
  // 后续 page 也用 _archive/ 路径作 mockup canonical
];

test.beforeAll(() => {
  fs.mkdirSync(REPORTS_DIR, { recursive: true });
});

test.describe('@mockup-diff · 实现页 vs 高保真 mockup', () => {
  for (const p of PAGES) {
    test(`@mockup-diff ${p.id} · ${p.mockup} vs ${p.route}`, async ({ page }) => {
      const mockupPath = path.join(MOCKUP_DIR, p.mockup);
      if (!fs.existsSync(mockupPath)) {
        test.skip(true, `mockup HTML 不存在: ${mockupPath}`);
        return;
      }

      // 1. 截 mockup HTML (file://) · 隐藏 [data-mockup-chrome] 装饰元素后全屏截
      await page.setViewportSize({ width: 393, height: 852 });
      await page.goto(`file://${mockupPath}`);
      await page.waitForLoadState('domcontentloaded');
      await page.waitForTimeout(500); // 静态资源稳定

      // F 机制: 注入 CSS 隐藏 mockup chrome 装饰 (iPhone 边框/statusbar/notch/homebar)
      // 这样 pixel diff 只比 page content
      const chromeCount = await page.locator('[data-mockup-chrome]').count();
      if (chromeCount > 0) {
        await page.addStyleTag({ content: '[data-mockup-chrome]{display:none !important;}' });
        await page.waitForTimeout(200);
      } else {
        console.warn(`⚠ ${p.id}: mockup 缺 [data-mockup-chrome] attr · 全屏对比含 chrome (verdict=AMBIGUOUS)`);
      }

      const mockupShot = await page.screenshot({ fullPage: true, animations: 'disabled' });

      // 2. 截 impl page · 取 page root selector
      await page.goto(`${BASE_URL}${p.route}`);
      await page.waitForLoadState('networkidle', { timeout: 15_000 });

      const implRoot = page.locator(p.pageRootSelector).first();
      const implExists = await implRoot.count() > 0;
      const implShot = implExists
        ? await implRoot.screenshot({ animations: 'disabled' })
        : await page.screenshot({ fullPage: true, animations: 'disabled' });

      // 3. pixelmatch 对比
      const mockupPng = PNG.sync.read(mockupShot);
      const implPng = PNG.sync.read(implShot);

      const width = Math.min(mockupPng.width, implPng.width);
      const height = Math.min(mockupPng.height, implPng.height);
      const totalPixels = width * height;
      const diff = new PNG({ width, height });

      // 裁剪到共同尺寸（避免不同大小报错）
      const cropPng = (src: PNG, w: number, h: number): PNG => {
        if (src.width === w && src.height === h) return src;
        const out = new PNG({ width: w, height: h });
        for (let y = 0; y < h; y++) {
          for (let x = 0; x < w; x++) {
            const srcIdx = (y * src.width + x) * 4;
            const dstIdx = (y * w + x) * 4;
            out.data[dstIdx]     = src.data[srcIdx];
            out.data[dstIdx + 1] = src.data[srcIdx + 1];
            out.data[dstIdx + 2] = src.data[srcIdx + 2];
            out.data[dstIdx + 3] = src.data[srcIdx + 3];
          }
        }
        return out;
      };

      const mockupCropped = cropPng(mockupPng, width, height);
      const implCropped = cropPng(implPng, width, height);

      const diffPixels = pixelmatch(
        mockupCropped.data,
        implCropped.data,
        diff.data,
        width,
        height,
        { threshold: 0.1 }
      );

      const diffRatio = diffPixels / totalPixels;

      // 4. 输出报告 + attach
      const reportFile = path.join(REPORTS_DIR, `${p.id}-mockup.png`);
      const implFile   = path.join(REPORTS_DIR, `${p.id}-impl.png`);
      const diffFile   = path.join(REPORTS_DIR, `${p.id}-diff.png`);
      fs.writeFileSync(reportFile, mockupShot);
      fs.writeFileSync(implFile, implShot);
      fs.writeFileSync(diffFile, PNG.sync.write(diff));

      await test.info().attach(`${p.id} mockup`, { path: reportFile, contentType: 'image/png' });
      await test.info().attach(`${p.id} impl`,   { path: implFile,   contentType: 'image/png' });
      await test.info().attach(`${p.id} diff`,   { path: diffFile,   contentType: 'image/png' });

      const sizeStr = `mockup ${mockupPng.width}x${mockupPng.height} · impl ${implPng.width}x${implPng.height} · compared ${width}x${height}`;
      console.log(`📊 ${p.id}: diff=${(diffRatio * 100).toFixed(2)}% (${diffPixels}/${totalPixels} px) · ${sizeStr}`);

      expect(
        diffRatio,
        `${p.id} pixel diff ${(diffRatio * 100).toFixed(2)}% > ${(p.tolerance * 100).toFixed(0)}% · check ${reportFile} ${implFile} ${diffFile}`
      ).toBeLessThanOrEqual(p.tolerance);
    });
  }
});
