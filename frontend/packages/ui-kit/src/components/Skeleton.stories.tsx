import type { Meta, StoryObj } from '@storybook/react';
import { Skeleton } from './Skeleton';

const meta: Meta<typeof Skeleton> = {
  title: 'Sd.2/Skeleton',
  component: Skeleton,
  tags: ['autodocs'],
  args: { testIdPrefix: 'demo' },
};
export default meta;
type Story = StoryObj<typeof Skeleton>;

export const Text: Story = { args: { width: 200, height: 14 } };
export const Block: Story = { args: { width: 280, height: 80 } };
export const Circle: Story = { args: { width: 48, height: 48, circle: true } };
export const MultiLine: Story = {
  render: (args) => (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8, width: 280 }}>
      <Skeleton {...args} height={16} />
      <Skeleton {...args} height={16} width={220} />
      <Skeleton {...args} height={16} width={180} />
    </div>
  ),
};
