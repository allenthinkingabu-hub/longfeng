// S7 · V-S7-10 · Capture page · jest-axe + testid · SC-01.AC-1 + SC-07.AC-1
import { describe, it, expect } from 'vitest';
import { render, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { I18nextProvider } from 'react-i18next';
import { axe, toHaveNoViolations } from 'jest-axe';
import { CapturePage } from './index';
import { i18n } from '../../i18n';
import { TEST_IDS } from '@longfeng/testids';

expect.extend(toHaveNoViolations as never);

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
  it('renders root + camera/manual mode tabs + gallery btn + submit on manual', () => {
    const { getByTestId } = renderPage();
    expect(getByTestId(TEST_IDS.capture.root)).toBeInTheDocument();
    // Sd.3 重写：camera/manual 是 mode tabs · gallery 是控制按钮（默认 photo mode）
    expect(getByTestId(TEST_IDS.capture.camera.btn)).toBeInTheDocument();
    expect(getByTestId(TEST_IDS.capture.gallery.btn)).toBeInTheDocument();
    expect(getByTestId(TEST_IDS.capture.manual.btn)).toBeInTheDocument();
    // 切到 manual mode · submit 按钮才渲染（原生 button · 不带 .btn 后缀）
    fireEvent.click(getByTestId(TEST_IDS.capture.manual.btn));
    expect(getByTestId(TEST_IDS.capture.form.submit)).toBeInTheDocument();
  });

  it('a11y · 0 axe violations', async () => {
    const { container } = renderPage();
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
