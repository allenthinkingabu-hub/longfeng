// S7 · V-S7-10 · List page · jest-axe 0 violation + testid 出现
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { I18nextProvider } from 'react-i18next';
import { axe, toHaveNoViolations } from 'jest-axe';
import { ListPage } from './index';
import { i18n } from '../../i18n';
import { TEST_IDS } from '@longfeng/testids';

expect.extend(toHaveNoViolations);

// Mock wrongbookClient · avoid real fetch
vi.mock('@longfeng/api-contracts', async () => {
  const actual = await vi.importActual<typeof import('@longfeng/api-contracts')>('@longfeng/api-contracts');
  return {
    ...actual,
    wrongbookClient: {
      list: vi.fn().mockResolvedValue({
        items: [
          { id: '1', subject: 'math', stem_text: '题干 1', tags: [], status: 'completed', mastery: 50, created_at: '2026-04-24T00:00:00Z', version: 0 },
          { id: '2', subject: 'physics', stem_text: '题干 2', tags: ['力学'], status: 'analyzing', mastery: 20, created_at: '2026-04-24T00:00:00Z', version: 0 },
        ],
        has_more: false,
        next_cursor: undefined,
      }),
    },
  };
});

function renderList() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <I18nextProvider i18n={i18n}>
      <QueryClientProvider client={qc}>
        <MemoryRouter>
          <ListPage />
        </MemoryRouter>
      </QueryClientProvider>
    </I18nextProvider>,
  );
}

describe('ListPage · SC-08.AC-1', () => {
  beforeEach(() => vi.clearAllMocks());

  it('renders root + active tab + archive tab testid', async () => {
    const { findByTestId } = renderList();
    expect(await findByTestId(TEST_IDS.wrongbookList.root)).toBeInTheDocument();
    expect(await findByTestId(TEST_IDS.wrongbookList['active-tab'])).toBeInTheDocument();
    expect(await findByTestId(TEST_IDS.wrongbookList['archive-tab'])).toBeInTheDocument();
  });

  it('a11y · 0 axe violations on initial load', async () => {
    const { container, findByTestId } = renderList();
    await findByTestId(TEST_IDS.wrongbookList.root);
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
