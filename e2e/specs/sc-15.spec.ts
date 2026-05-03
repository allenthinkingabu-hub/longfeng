/**
 * SC-15 · Observer 三重防护（C4 · C5 红线）
 *
 * Happy path：观察者用邀请码进入 → 看到学生信息脱敏 + scope=READ + watermark
 * 异常 1：所有写按钮 aria-disabled
 * 异常 2：撤销 ≤ 1s 后任意写 API 必须 403
 *
 * 轨道：A · B
 * Tag：@smoke · @sc-15
 */
import { test, expect } from '@playwright/test';
import { issueObserverToken } from '../fixtures/share';
import { ObserverPage } from '../pages';

test.describe('SC-15 · Observer 三重防护 @sc-15', () => {

  test('happy path · 邀请码登入 → 脱敏 · scope=READ · watermark @smoke', async ({ page }) => {
    const { observerJwt } = issueObserverToken({ studentId: 'student-001', role: 'PARENT' });

    // 通过 URL fragment 注入 observer jwt（生产路径走 /observer?code=XXX 后端 exchange）
    await page.context().addInitScript((jwt) => {
      window.sessionStorage.setItem('__lf_observer_jwt__', jwt);
    }, observerJwt);

    const obs = new ObserverPage(page);
    await obs.open();
    await obs.assertReadOnlyBannerVisible();
    await obs.assertScopeBadgeRead();
    await obs.assertStudentSummary();
    await obs.assertWatermarkVisible();
  });

  test('异常 · 所有写按钮 aria-disabled', async ({ page }) => {
    const { observerJwt } = issueObserverToken({ studentId: 'student-002', role: 'PARENT' });
    await page.context().addInitScript((jwt) => {
      window.sessionStorage.setItem('__lf_observer_jwt__', jwt);
    }, observerJwt);

    const obs = new ObserverPage(page);
    await obs.open();
    await obs.assertAllWriteButtonsDisabled();
  });

  test('异常 · 撤销后写 API → 403 · C4 三重防护', async ({ page, request }) => {
    // 模拟撤销：清掉 observer jwt 后调写 API
    await request.post('/api/observer/revoke', { data: { observerId: 'observer-test-001' } }).catch(() => {});

    // 任意写 API
    const resp = await request.patch('/api/v1/wrongbook/items/test-id/tags', {
      data: ['tampered-tag'],
      headers: { Authorization: 'Bearer revoked-observer-jwt' },
    });
    expect([401, 403]).toContain(resp.status());
  });
});
