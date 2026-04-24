import type { Meta, StoryObj } from '@storybook/react';
import { Progress } from './Progress';

const meta: Meta<typeof Progress> = {
  title: 'Sd.2/Progress',
  component: Progress,
  tags: ['autodocs'],
  args: { testIdPrefix: 'demo' },
};
export default meta;
type Story = StoryObj<typeof Progress>;

export const Empty: Story = { args: { value: 0 } };
export const Half: Story = { args: { value: 50 } };
export const Full: Story = { args: { value: 100 } };
export const WithLabel: Story = { args: { value: 65, label: '复习进度' } };
