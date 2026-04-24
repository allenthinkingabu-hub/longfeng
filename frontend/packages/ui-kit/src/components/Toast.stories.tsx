import type { Meta, StoryObj } from '@storybook/react';
import { Toast } from './Toast';

const meta: Meta<typeof Toast> = {
  title: 'Sd.2/Toast',
  component: Toast,
  tags: ['autodocs'],
  args: { testIdPrefix: 'demo' },
};
export default meta;
type Story = StoryObj<typeof Toast>;

export const Info: Story = { args: { type: 'info', message: '已复制到剪贴板' } };
export const Success: Story = { args: { type: 'success', message: '保存成功' } };
export const Warning: Story = { args: { type: 'warning', message: '网络较慢' } };
export const ErrorWithAction: Story = {
  args: { type: 'error', message: '上传失败', action: { label: '重试', onClick: () => {} } },
};
