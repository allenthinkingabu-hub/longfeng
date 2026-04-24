import type { Meta, StoryObj } from '@storybook/react';
import { Switch } from './Switch';

const meta: Meta<typeof Switch> = {
  title: 'Sd.2/Switch',
  component: Switch,
  tags: ['autodocs'],
  args: { testIdPrefix: 'demo', checked: false, onChange: () => {} },
};
export default meta;
type Story = StoryObj<typeof Switch>;

export const Off: Story = {};
export const On: Story = { args: { checked: true } };
export const DisabledOff: Story = { args: { disabled: true } };
export const DisabledOnLabeled: Story = { args: { checked: true, disabled: true, label: '夜间模式' } };
