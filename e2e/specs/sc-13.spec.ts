/**
 * SC-13 · 分享接收
 *
 * Happy path：合法 token → P-SHARED 渲染脱敏内容
 * 异常 1：篡改 token → 403 / error 页
 * 异常 2：匿名写 API → 403（C3 红线）
 *
 * 轨道：A · B
 * Tag：@smoke · @sc-13
 */
import { test, expect } from '@playwright/test';
import { issueShareToken, tamperShareToken } from '../fixtures/share';
import { SharedPage } from '../pages';

test.describe('SC-13 · 分享接收 @sc-13', () => {

  test('happy path · 合法 token → 脱敏渲染 @smoke', async ({ page }) => {
    const token = issueShareToken({
      kind: 'QUESTION',
      sub: 'qid-shared-001',
      iss: 'student-001',
      sharerNickMasked: '小明** 同学',
    });

    const shared = new SharedPage(page);
    await shared.openWithToken(token);
    await shared.assertSharerBanner();
    await shared.assertStemMasked();
  });

  test('异常 · 篡改 token → 403', async ({ page }) => {
    const token = issueShareToken({
      kind: 'QUESTION',
      sub: 'qid-shared-002',
      iss: 'student-001',
      sharerNickMasked: '小明**',
    });
    const tampered = tamperShareToken(token);

    const shared = new SharedPage(page);
    await shared.openWithToken(tampered);
    await shared.assertTamperedTokenRejected();
  });

  test('异常 · 匿名写 API → 403 · C3 红线', async ({ request }) => {
    // 直接走 request fixture（不通过 UI · 这里是 API 红线断言而非 UI 操作）
    const resp = await request.post('/api/v1/wrongbook/items', {
      data: { stem_text: 'tampered-anon-write' },
    });
    expect(resp.status()).toBe(403);
  });
});
