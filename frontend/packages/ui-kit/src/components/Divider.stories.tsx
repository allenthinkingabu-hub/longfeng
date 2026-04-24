import type { Meta, StoryObj } from '@storybook/react';
import { Divider } from './Divider';

const meta: Meta<typeof Divider> = {
  title: 'Sd.2/Divider',
  component: Divider,
  tags: ['autodocs'],
  args: { testIdPrefix: 'demo' },
};
export default meta;
type Story = StoryObj<typeof Divider>;

export const Horizontal: Story = { args: {} };
export const WithText: Story = { args: { text: '更多' } };
export const Vertical: Story = {
  render: (args) => (
    <div>
      上 <Divider {...args} orientation="vertical" /> 中 <Divider {...args} orientation="vertical" /> 下
    </div>
  ),
};
export const InList: Story = {
  render: (args) => (
    <div style={{ width: 280 }}>
      <div style={{ padding: 12 }}>第一项</div>
      <Divider {...args} />
      <div style={{ padding: 12 }}>第二项</div>
    </div>
  ),
};
