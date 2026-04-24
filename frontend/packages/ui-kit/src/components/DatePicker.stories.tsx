import type { Meta, StoryObj } from '@storybook/react';
import { DatePicker } from './DatePicker';

const meta: Meta<typeof DatePicker> = {
  title: 'Sd.2/DatePicker',
  component: DatePicker,
  tags: ['autodocs'],
  args: { testIdPrefix: 'demo', value: '2026-04-24', onChange: () => {} },
};
export default meta;
type Story = StoryObj<typeof DatePicker>;

export const Default: Story = {};
export const WithLabel: Story = { args: { label: '复习日期' } };
export const WithMinMax: Story = { args: { label: '选择', min: '2026-04-01', max: '2026-04-30' } };
export const Empty: Story = { args: { value: '' } };
