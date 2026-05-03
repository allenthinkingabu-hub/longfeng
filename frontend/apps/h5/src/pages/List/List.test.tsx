// S7 · FE-03 · WrongbookList (P05) · B-轨 mock 单测
// AC 覆盖: AC-WB-LIST-001 ~ AC-WB-LIST-010
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { I18nextProvider } from 'react-i18next';
import { axe, toHaveNoViolations } from 'jest-axe';
import { ListPage } from './index';
import { i18n } from '../../i18n';
import { TEST_IDS } from '@longfeng/testids';

expect.extend(toHaveNoViolations as never);

const mockItems = [
  {
    id: 'q001', subject: 'math', stem_text: '已知函数 f(x)=x²−4x+3，求其顶点坐标与对称轴方程。',
    tags: ['二次函数', '配方法'], status: 'completed' as const,
    mastery: 20, created_at: '2026-04-24T00:00:00Z', version: 1,
  },
  {
    id: 'q002', subject: 'physics', stem_text: '两电阻并联接 12V，求总电流。',
    tags: ['欧姆定律', '并联'], status: 'analyzing' as const,
    mastery: 50, created_at: '2026-04-23T00:00:00Z', version: 0,
  },
  {
    id: 'q003', subject: 'english', stem_text: '"By the time he arrived, the meeting ___ already started."',
    tags: ['时态', 'past perfect'], status: 'completed' as const,
    mastery: 80, created_at: '2026-04-22T00:00:00Z', version: 2,
  },
];

vi.mock('@longfeng/api-contracts', async () => {
  const actual = await vi.importActual<typeof import('@longfeng/api-contracts')>('@longfeng/api-contracts');
  return {
    ...actual,
    wrongbookClient: {
      list: vi.fn().mockResolvedValue({
        items: mockItems,
        has_more: false,
        next_cursor: undefined,
      }),
    },
  };
});

function renderList(initialEntries = ['/wrongbook']) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <I18nextProvider i18n={i18n}>
      <QueryClientProvider client={qc}>
        <MemoryRouter initialEntries={initialEntries}>
          <ListPage />
        </MemoryRouter>
      </QueryClientProvider>
    </I18nextProvider>,
  );
}

describe('P05 WrongbookList · B-轨 mock 单测', () => {
  beforeEach(() => vi.clearAllMocks());

  // ── Legacy compat ────────────────────────────────────────────
  it('renders root + active-tab + archive-tab testid (legacy compat)', async () => {
    const { findByTestId } = renderList();
    expect(await findByTestId(TEST_IDS.wrongbookList.root)).toBeInTheDocument();
    expect(await findByTestId(TEST_IDS.wrongbookList['active-tab'])).toBeInTheDocument();
    expect(await findByTestId(TEST_IDS.wrongbookList['archive-tab'])).toBeInTheDocument();
  });

  // ── AC-WB-LIST-001 ───────────────────────────────────────────
  it('AC-WB-LIST-001 · 大标题"错题本" + 搜索框', async () => {
    const { findByTestId, findByText } = renderList();
    const title = await findByTestId('p05-page-header-title');
    expect(title).toBeInTheDocument();
    expect(await findByText('错题本')).toBeInTheDocument();
    const search = await findByTestId('p05-page-header-search');
    expect(search).toBeInTheDocument();
  });

  // ── AC-WB-LIST-002 ───────────────────────────────────────────
  it('AC-WB-LIST-002 · 学科 chips 横滚显示计数', async () => {
    const { findByTestId, findByText } = renderList();
    // math chip exists with count 52
    const mathChip = await findByTestId('subject-chip-math');
    expect(mathChip).toBeInTheDocument();
    expect(mathChip).toHaveTextContent('52');
  });

  it('AC-WB-LIST-002 · 点击学科 chip 后 aria-pressed=true', async () => {
    const { findByTestId } = renderList();
    const mathChip = await findByTestId('subject-chip-math');
    fireEvent.click(mathChip);
    await waitFor(() => {
      expect(mathChip).toHaveAttribute('aria-pressed', 'true');
    });
  });

  // ── AC-WB-LIST-003 ───────────────────────────────────────────
  it('AC-WB-LIST-003 · 3 张 MasteryStatusCard 横排', async () => {
    const { findByTestId } = renderList();
    expect(await findByTestId('mastery-status-card-forgot')).toBeInTheDocument();
    expect(await findByTestId('mastery-status-card-partial')).toBeInTheDocument();
    expect(await findByTestId('mastery-status-card-mastered')).toBeInTheDocument();
  });

  it('AC-WB-LIST-003 · mastery 数字 = 各桶计数', async () => {
    const { findByTestId } = renderList();
    // q001 mastery=20 → low=forgot, q002=50 → mid=partial, q003=80 → high=mastered
    const forgotCard = await findByTestId('mastery-status-card-forgot');
    expect(forgotCard).toHaveTextContent('1');
    const masteredCard = await findByTestId('mastery-status-card-mastered');
    expect(masteredCard).toHaveTextContent('1');
  });

  // ── AC-WB-LIST-004 ───────────────────────────────────────────
  it('AC-WB-LIST-004 · mastery card 点击切换 aria-checked', async () => {
    const { findByTestId } = renderList();
    const forgotCard = await findByTestId('mastery-status-card-forgot');
    fireEvent.click(forgotCard);
    await waitFor(() => {
      expect(forgotCard).toHaveAttribute('aria-checked', 'true');
    });
    // re-click to deselect
    fireEvent.click(forgotCard);
    await waitFor(() => {
      expect(forgotCard).toHaveAttribute('aria-checked', 'false');
    });
  });

  // ── AC-WB-LIST-005 ───────────────────────────────────────────
  it('AC-WB-LIST-005 · QuestionListCard 含缩略图 + 6 段 stage + due', async () => {
    const { findByTestId } = renderList();
    const thumb = await findByTestId('question-list-card-1-thumbnail');
    expect(thumb).toBeInTheDocument();
    // 6 stage dots (index 0-5)
    for (let i = 0; i < 6; i++) {
      const dot = await findByTestId(`question-list-card-1-stage-${i}`);
      expect(dot).toBeInTheDocument();
    }
    const due = await findByTestId('question-list-card-1-due');
    expect(due).toBeInTheDocument();
  });

  // ── AC-WB-LIST-007 ───────────────────────────────────────────
  it('AC-WB-LIST-007 · FAB 存在 + position fixed', async () => {
    const { findByTestId } = renderList();
    const fab = await findByTestId('p05-fab-capture');
    expect(fab).toBeInTheDocument();
    // check aria-label
    expect(fab).toHaveAttribute('aria-label', '拍照录入新题');
  });

  // ── AC-WB-LIST-008 · EMPTY state ─────────────────────────────
  it('AC-WB-LIST-008 · EMPTY 态显示空态 + 拍题入口', async () => {
    const { wrongbookClient } = await import('@longfeng/api-contracts');
    (wrongbookClient.list as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      items: [],
      has_more: false,
      next_cursor: undefined,
    });
    const { findByTestId } = renderList();
    const empty = await findByTestId('p05-empty-state');
    expect(empty).toBeInTheDocument();
    const captureBtn = await findByTestId('p05-empty-capture-btn');
    expect(captureBtn).toBeInTheDocument();
  });

  // ── AC-WB-LIST-010 ───────────────────────────────────────────
  it('AC-WB-LIST-010 · AI 语义 Badge 存在', async () => {
    const { findByTestId } = renderList();
    const badge = await findByTestId('p05-page-header-semantic-badge');
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveAttribute('data-active', 'false');
    // click to activate
    fireEvent.click(badge);
    await waitFor(() => {
      expect(badge).toHaveAttribute('data-active', 'true');
      expect(badge).toHaveAttribute('aria-pressed', 'true');
    });
  });

  // ── a11y ─────────────────────────────────────────────────────
  it('a11y · 0 axe violations on initial load', async () => {
    const { container, findByTestId } = renderList();
    await findByTestId(TEST_IDS.wrongbookList.root);
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
