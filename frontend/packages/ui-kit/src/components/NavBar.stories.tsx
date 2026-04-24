import type { Meta, StoryObj } from '@storybook/react';
import { NavBar } from './NavBar';

const meta: Meta<typeof NavBar> = {
  title: 'Sd.2/NavBar',
  component: NavBar,
  tags: ['autodocs'],
  args: { title: '错题详情', testIdPrefix: 'demo' },
};
export default meta;
type Story = StoryObj<typeof NavBar>;

export const Default: Story = {};
export const WithBack: Story = { args: { onBack: () => {} } };
export const WithRight: Story = { args: { onBack: () => {}, right: <button>分享</button> } };
export const LongTitle: Story = { args: { onBack: () => {}, title: '高二数学 · 三角函数图像变换综合题解析' } };
