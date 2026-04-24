// S7 · V-S7-10 · Capture page · jest-axe + testid · SC-01.AC-1 + SC-07.AC-1
import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { I18nextProvider } from 'react-i18next';
import { axe, toHaveNoViolations } from 'jest-axe';
import { CapturePage } from './index';
import { i18n } from '../../i18n';
import { TEST_IDS } from '@longfeng/testids';

expect.extend(toHaveNoViolations);

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <I18nextProvider i18n={i18n}>
      <QueryClientProvider client={qc}>
        <MemoryRouter>
          <CapturePage />
        </MemoryRouter>
      </QueryClientProvider>
    </I18nextProvider>,
  );
}

describe('CapturePage · SC-01 + SC-07', () => {
  it('renders root + 三 Tab testid', () => {
    const { getByTestId } = renderPage();
    expect(getByTestId(TEST_IDS.capture.root)).toBeInTheDocument();
    expect(getByTestId(TEST_IDS.capture.camera.btn)).toBeInTheDocument();
    expect(getByTestId(TEST_IDS.capture.gallery.btn)).toBeInTheDocument();
    expect(getByTestId(TEST_IDS.capture.manual.btn)).toBeInTheDocument();
    // ui-kit Button 生成 {prefix}.btn · submit 按钮 testid = capture.form.submit.btn
    expect(getByTestId(`${TEST_IDS.capture.form.submit}.btn`)).toBeInTheDocument();
  });

  it('a11y · 0 axe violations', async () => {
    const { container } = renderPage();
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
