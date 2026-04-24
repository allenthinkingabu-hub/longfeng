// Sd.2 · Storybook preview · 注入 tokens.css · 默认 1440×900 · a11y on
import type { Preview } from '@storybook/react';
import '../src/tokens.css';

const preview: Preview = {
  parameters: {
    actions: { argTypesRegex: '^on[A-Z].*' },
    controls: {
      matchers: {
        color: /(background|color)$/i,
        date: /Date$/,
      },
    },
    a11y: {
      config: {
        rules: [
          // Sd.7 G3 · 0 violations 硬门闸
        ],
      },
      options: {},
      manual: false,
    },
    layout: 'centered',
    viewport: {
      viewports: {
        h5: { name: 'H5 (375×667)', styles: { width: '375px', height: '667px' } },
        desktop: { name: 'Desktop (1440×900)', styles: { width: '1440px', height: '900px' } },
      },
      defaultViewport: 'h5',
    },
  },
};

export default preview;
