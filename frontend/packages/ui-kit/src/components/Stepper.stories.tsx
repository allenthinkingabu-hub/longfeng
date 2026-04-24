import type { Meta, StoryObj } from '@storybook/react';
import { Stepper } from './Stepper';

const meta: Meta<typeof Stepper> = {
  title: 'Sd.2/Stepper',
  component: Stepper,
  tags: ['autodocs'],
  args: { testIdPrefix: 'demo', value: 3, onChange: () => {} },
};
export default meta;
type Story = StoryObj<typeof Stepper>;

export const Default: Story = {};
export const AtMin: Story = { args: { value: 0, min: 0 } };
export const AtMax: Story = { args: { value: 9, min: 0, max: 9 } };
export const BigStep: Story = { args: { value: 15, step: 5, min: 0, max: 50 } };
