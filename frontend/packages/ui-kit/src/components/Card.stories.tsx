import type { Meta, StoryObj } from '@storybook/react';
import { Card } from './Card';

const meta: Meta<typeof Card> = {
  title: 'Sd.2/Card',
  component: Card,
  tags: ['autodocs'],
  args: { children: '错题内容示例', testIdPrefix: 'demo' },
};
export default meta;
type Story = StoryObj<typeof Card>;

export const Default: Story = {};
export const Elevated: Story = { args: { variant: 'elevated' } };
export const Compact: Story = { args: { padding: 8 } };
export const Long: Story = {
  args: {
    children: '这是一道关于三角函数图像变换的错题。考察学生对 y=A sin(ωx+φ)+B 中各参数的理解与灵活运用。',
  },
};
