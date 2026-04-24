// S7 · V-S7-10 · Detail page · jest-axe + testid · SC-02 + SC-03
import { describe, it, expect, vi } from 'vitest';
import { render } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { I18nextProvider } from 'react-i18next';
import { axe, toHaveNoViolations } from 'jest-axe';
import { DetailPage } from './index';
import { i18n } from '../../i18n';
import { TEST_IDS } from '@longfeng/testids';

expect.extend(toHaveNoViolations);

vi.mock('@longfeng/api-contracts', async () => {
  const actual = await vi.importActual<typeof import('@longfeng/api-contracts')>('@longfeng/api-contracts');
  return {
    ...actual,
    wrongbookClient: {
      get: vi.fn().mockResolvedValue({
        id: '1',
        subject: 'math',
        stem_text: '已知函数 f(x)=2sin(ωx+π/6)，求 ω = ?',
        tags: [],
        status: 'completed',
        mastery: 60,
        created_at: '2026-04-24T00:00:00Z',
        version: 0,
      }),
      updateTags: vi.fn().mockResolvedValue(undefined),
    },
    analysisClient: {
      similar: vi.fn().mockResolvedValue({ items: [] }),
      explainStream: vi.fn(() => () => {}),
    },
  };
});

function renderDetail() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <I18nextProvider i18n={i18n}>
      <QueryClientProvider client={qc}>
        <MemoryRouter initialEntries={['/wrongbook/1']}>
          <Routes>
            <Route path="/wrongbook/:id" element={<DetailPage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </I18nextProvider>,
  );
}

describe('DetailPage · SC-02 + SC-03', () => {
  it('renders root + tag-sheet button + stem-text testid', async () => {
    const { findByTestId } = renderDetail();
    expect(await findByTestId(TEST_IDS.wrongbookDetail.root)).toBeInTheDocument();
    expect(await findByTestId(TEST_IDS.wrongbookDetail['tag-sheet'])).toBeInTheDocument();
    expect(await findByTestId(TEST_IDS.wrongbookDetail['stem-text'])).toBeInTheDocument();
  });

  it('a11y · 0 axe violations', async () => {
    const { container, findByTestId } = renderDetail();
    await findByTestId(TEST_IDS.wrongbookDetail.root);
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
