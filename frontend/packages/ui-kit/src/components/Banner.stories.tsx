import type { Meta, StoryObj } from '@storybook/react';
import { Banner } from './Banner';

const meta: Meta<typeof Banner> = {
  title: 'Sd.2/Banner',
  component: Banner,
  tags: ['autodocs'],
  args: { testIdPrefix: 'demo', message: '系统将于今晚维护' },
};
export default meta;
type Story = StoryObj<typeof Banner>;

export const Info: Story = { args: { type: 'info' } };
export const Success: Story = { args: { type: 'success', message: '同步完成' } };
export const Warning: Story = { args: { type: 'warning', title: '网络波动', message: '上传可能较慢' } };
export const ErrorClosable: Story = {
  args: { type: 'error', message: '上传失败', closable: true, onClose: () => {} },
};
