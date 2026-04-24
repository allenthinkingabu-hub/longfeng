import type { Meta, StoryObj } from '@storybook/react';
import { Sheet } from './Sheet';

const meta: Meta<typeof Sheet> = {
  title: 'Sd.2/Sheet',
  component: Sheet,
  tags: ['autodocs'],
  args: { open: true, onClose: () => {}, testIdPrefix: 'demo', children: '底部抽屉内容' },
};
export default meta;
type Story = StoryObj<typeof Sheet>;

export const Default: Story = {};
export const WithTitle: Story = { args: { title: '筛选' } };
export const LongContent: Story = {
  args: {
    title: '学科列表',
    children: Array.from({ length: 30 })
      .map((_, i) => `选项 ${i + 1}`)
      .join('\n'),
  },
};
export const ShortList: Story = { args: { title: '操作', children: '分享 · 收藏 · 删除' } };
