import type { Meta, StoryObj } from '@storybook/react';
import { Tag } from './Tag';

const meta: Meta<typeof Tag> = {
  title: 'Sd.2/Tag',
  component: Tag,
  tags: ['autodocs'],
  args: { testIdPrefix: 'demo', children: '数学' },
};
export default meta;
type Story = StoryObj<typeof Tag>;

export const Default: Story = {};
export const Primary: Story = { args: { color: 'primary', children: '高二' } };
export const Success: Story = { args: { color: 'success', children: '已掌握' } };
export const DangerClosable: Story = {
  args: { color: 'danger', children: '易错', closable: true, onClose: () => {} },
};
