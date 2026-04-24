import type { Meta, StoryObj } from '@storybook/react';
import { Badge } from './Badge';

const meta: Meta<typeof Badge> = {
  title: 'Sd.2/Badge',
  component: Badge,
  tags: ['autodocs'],
  args: { testIdPrefix: 'demo', children: <span style={{ padding: 12 }}>消息</span> },
};
export default meta;
type Story = StoryObj<typeof Badge>;

export const Count: Story = { args: { count: 5 } };
export const Dot: Story = { args: { dot: true } };
export const Overflow: Story = { args: { count: 120 } };
export const WrappedAvatar: Story = {
  args: {
    count: 3,
    children: (
      <span
        style={{
          display: 'inline-block',
          width: 40,
          height: 40,
          borderRadius: '50%',
          background: '#ddd',
        }}
      />
    ),
  },
};
