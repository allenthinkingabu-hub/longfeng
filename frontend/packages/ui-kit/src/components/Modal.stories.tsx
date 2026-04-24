import type { Meta, StoryObj } from '@storybook/react';
import { Modal } from './Modal';

const meta: Meta<typeof Modal> = {
  title: 'Sd.2/Modal',
  component: Modal,
  tags: ['autodocs'],
  args: { open: true, onClose: () => {}, testIdPrefix: 'demo', children: '确认删除此错题？' },
};
export default meta;
type Story = StoryObj<typeof Modal>;

export const Default: Story = {};
export const WithTitle: Story = { args: { title: '删除确认' } };
export const WithFooter: Story = {
  args: {
    title: '确认',
    footer: (
      <>
        <button>取消</button>
        <button>确定</button>
      </>
    ),
  },
};
export const LongContent: Story = {
  args: {
    title: '条款',
    children: Array.from({ length: 20 })
      .map((_, i) => `第 ${i + 1} 行说明文字。`)
      .join('\n'),
  },
};
