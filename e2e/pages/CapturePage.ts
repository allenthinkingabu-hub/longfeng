/**
 * P02 · 拍题相机（spec/P02-capture.spec.md）
 * Mood C dark-camera · 78px 圆形快门
 */
import { type Page, expect } from '@playwright/test';
import { BasePage } from './_base';

export type Subject = 'math' | 'physics' | 'chemistry' | 'english';

export class CapturePage extends BasePage {
  readonly rootTestId = 'p02-root';

  static route = '/capture';

  constructor(page: Page) { super(page); }

  async open() { await this.goto(CapturePage.route); }

  async selectSubject(s: Subject) {
    await this.byTestId(`p02-subject-${s}`).click();
  }

  /** 触发拍照（fileChooser 模拟） */
  async triggerShutter(filePath: string) {
    const fileChooserPromise = this.page.waitForEvent('filechooser');
    await this.byTestId('p02-shutter-btn').click();
    const chooser = await fileChooserPromise;
    await chooser.setFiles(filePath);
  }

  /** 上传进度可见 */
  async assertUploadProgressVisible() {
    await expect(this.byTestId('p02-upload-progress')).toBeVisible({ timeout: 10_000 });
  }

  /** 边缘检测 badge 可见（视觉信号） */
  async assertDetectBadgeVisible() {
    await expect(this.byTestId('p02-detect-badge')).toBeVisible();
  }

  /** Mood C 全屏暗色 */
  async assertMoodC() {
    await expect(this.byTestId(this.rootTestId)).toHaveAttribute('data-mood', /C|dark/);
  }
}
