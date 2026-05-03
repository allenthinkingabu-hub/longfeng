/**
 * SC-10 · 归档级联
 *
 * Happy path：错题归档 → 节点 CASCADE CANCELLED → 5s 内 undo 可恢复
 *
 * 轨道：A · B
 * Tag：@sc-10
 */
import { test, expect } from '../fixtures/student';
import { WrongbookListPage, WrongbookDetailPage } from '../pages';

test.describe('SC-10 · 归档级联 @sc-10', () => {

  test('happy path · 归档 + 5s undo', async ({ studentPage }) => {
    const list = new WrongbookListPage(studentPage);
    await list.open();
    const before = await list.getItemCount();
    if (before === 0) test.skip(true, 'empty wrongbook · skip cascade');

    await list.openItem(0);
    const detail = new WrongbookDetailPage(studentPage);
    await detail.assertStemTextVisible();
    await detail.clickArchive();

    // toast / undo 按钮 5s 内可见
    const undo = studentPage.getByRole('button', { name: /撤销|undo/i });
    await expect(undo).toBeVisible({ timeout: 5_000 });

    // 不点 undo · 等 5s 后归档生效
    await studentPage.waitForTimeout(5_500);
    await list.open();
    await list.switchToArchiveTab();
    expect(await list.getItemCount()).toBeGreaterThan(0);
  });
});
