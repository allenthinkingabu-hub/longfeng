import type { Meta, StoryObj } from '@storybook/react';
import { Avatar } from './Avatar';

const meta: Meta<typeof Avatar> = {
  title: 'Sd.2/Avatar',
  component: Avatar,
  tags: ['autodocs'],
  args: { testIdPrefix: 'demo', name: '王小明' },
};
export default meta;
type Story = StoryObj<typeof Avatar>;

export const Initial: Story = {};
export const Small: Story = { args: { size: 24 } };
export const Large: Story = { args: { size: 72 } };
export const Empty: Story = { args: { name: '' } };
