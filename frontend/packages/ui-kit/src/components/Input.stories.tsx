import type { Meta, StoryObj } from '@storybook/react';
import { Input } from './Input';

const meta: Meta<typeof Input> = {
  title: 'Sd.2/Input',
  component: Input,
  tags: ['autodocs'],
  args: { placeholder: '请输入', testIdPrefix: 'demo' },
};
export default meta;
type Story = StoryObj<typeof Input>;

export const Default: Story = {};
export const Filled: Story = { args: { defaultValue: '三角函数' } };
export const Errored: Story = { args: { error: '长度需 ≥ 2' } };
export const Disabled: Story = { args: { disabled: true, defaultValue: '不可编辑' } };
