import type { Meta, StoryObj } from '@storybook/react';
import { TabBar } from './TabBar';

const meta: Meta<typeof TabBar> = {
  title: 'Sd.2/TabBar',
  component: TabBar,
  tags: ['autodocs'],
  args: {
    activeKey: 'home',
    onChange: () => {},
    testIdPrefix: 'demo',
    items: [
      { key: 'home', label: '首页' },
      { key: 'book', label: '错题' },
      { key: 'plan', label: '复习' },
      { key: 'me', label: '我的' },
    ],
  },
};
export default meta;
type Story = StoryObj<typeof TabBar>;

export const HomeActive: Story = {};
export const BookActive: Story = { args: { activeKey: 'book' } };
export const WithBadge: Story = {
  args: {
    items: [
      { key: 'home', label: '首页' },
      { key: 'book', label: '错题', badge: 3 },
      { key: 'plan', label: '复习', badge: 12 },
      { key: 'me', label: '我的' },
    ],
  },
};
export const ThreeItems: Story = {
  args: {
    items: [
      { key: 'a', label: '错题' },
      { key: 'b', label: '复习' },
      { key: 'c', label: '我的' },
    ],
    activeKey: 'b',
  },
};
