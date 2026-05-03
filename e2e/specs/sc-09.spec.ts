/**
 * SC-09 · 家长分享考试日
 *
 * Happy path：学生分享 EXAM_DAY → 家长打开 → P-SHARED 脱敏 → 推送送达
 *
 * 轨道：A · B
 * Tag：@sc-09
 */
import { test, expect } from '@playwright/test';
import { issueShareToken } from '../fixtures/share';
import { SharedPage } from '../pages';

test.describe('SC-09 · 家长分享考试日 @sc-09', () => {

  test('happy path · 家长打开分享 → 脱敏预览 + upgrade CTA', async ({ page }) => {
    const token = issueShareToken({
      kind: 'EXAM_DAY',
      sub: 'exam-2026-06-07-gaokao',
      iss: 'student-001',
      sharerNickMasked: '小明** 同学',
      ttlSec: 7 * 86400,
    });

    const shared = new SharedPage(page);
    await shared.openWithToken(token);

    await shared.assertSharerBanner();
    await shared.assertMaskedQuestionVisible();
    await shared.assertMemoryCurvePreviewGray();

    // upgrade CTA 必须可见
    await expect(page.getByTestId('upgrade-cta-fixed')).toBeVisible();
  });
});
