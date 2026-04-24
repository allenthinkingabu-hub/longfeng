import type { Meta, StoryObj } from '@storybook/react';
import { Empty } from './Empty';

const meta: Meta<typeof Empty> = {
  title: 'Sd.2/Empty',
  component: Empty,
  tags: ['autodocs'],
  args: { testIdPrefix: 'demo' },
};
export default meta;
type Story = StoryObj<typeof Empty>;

export const Default: Story = {};
export const WithDescription: Story = { args: { description: '还没有任何错题，拍一张试试' } };
export const WithAction: Story = {
  args: { description: '拍照添加第一题', action: <button>去拍照</button> },
};
export const CustomTitle: Story = { args: { title: '暂无待复习' } };
