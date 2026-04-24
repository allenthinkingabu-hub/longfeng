import type { Meta, StoryObj } from '@storybook/react';
import { Picker } from './Picker';

const opts = [
  { label: '数学', value: 'math' },
  { label: '物理', value: 'physics' },
  { label: '化学', value: 'chem' },
  { label: '英语', value: 'english' },
];

const meta: Meta<typeof Picker> = {
  title: 'Sd.2/Picker',
  component: Picker,
  tags: ['autodocs'],
  args: {
    testIdPrefix: 'demo',
    options: opts,
    value: 'math',
    onChange: () => {},
  },
};
export default meta;
type Story = StoryObj<typeof Picker>;

export const Default: Story = {};
export const WithLabel: Story = { args: { label: '学科' } };
export const Physics: Story = { args: { label: '学科', value: 'physics' } };
export const LongList: Story = {
  args: {
    label: '年级',
    value: 'g8',
    options: Array.from({ length: 12 }).map((_, i) => ({ label: `${i + 1} 年级`, value: `g${i + 1}` })),
  },
};
