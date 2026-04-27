# @longfeng/ui-kit 组件参考

> 项目路径：`frontend/packages/ui-kit/src/components/`
> 所有组件通过 `testIdPrefix` prop 注入 `data-testid`，格式 `{testIdPrefix}.{component}`

## 组件选型速查

| 场景 | 优先选用 | 备注 |
|---|---|---|
| 顶部标题栏（返回 + 标题 + 右侧图标） | `NavBar` | sticky top · 44pt 高 |
| 底部 Tab 导航 | `TabBar` | fixed bottom · role=tablist |
| 卡片容器（错题卡/统计卡） | `Card` | variant: default/elevated |
| 底部弹出面板（标签选择/筛选） | `Sheet` | 半透明遮罩 + slide-up |
| 全屏弹窗（删除确认） | `Modal` | 居中 dialog |
| 按钮 | `Button` | variant: primary/ghost/danger · size: sm/md/lg |
| 输入框 | `Input` | 含 label / error 状态 |
| 骨架屏 | `Skeleton` | 加载占位 |
| 空状态 | `Empty` | icon + 描述文本 |
| 角标 | `Badge` | 数字 / dot |
| 头像 | `Avatar` | 圆形 · size: sm/md/lg |
| 分割线 | `Divider` | horizontal / vertical |
| 标签/Chip | `Tag` | variant: default/primary/success/warning/danger |
| 进度条 | `Progress` | 百分比 |
| 步进器 | `Stepper` | 数字加减 |
| 开关 | `Switch` | boolean |
| Toast 提示 | `Toast` | 全局提示 · 不在 JSX 树中 |
| 横幅提示 | `Banner` | 内联警告/信息条 |

## 关键组件 Props

### NavBar
```tsx
interface NavBarProps {
  title: string;
  onBack?: () => void;       // 有则渲染返回按钮
  right?: React.ReactNode;   // 右侧自定义内容
  testIdPrefix?: string;     // 生成 {prefix}.navbar / {prefix}.navbar.back
}
```

### TabBar
```tsx
interface TabBarItem { key: string; label: string; icon?: ReactNode; badge?: number }
interface TabBarProps {
  items: TabBarItem[];
  activeKey: string;
  onChange: (key: string) => void;
  testIdPrefix?: string;     // 生成 {prefix}.tabbar / {prefix}.tabbar.{key}
}
```

### Card
```tsx
interface CardProps extends HTMLAttributes<HTMLElement> {
  variant?: 'default' | 'elevated';
  padding?: number;          // 默认 16
  testIdPrefix?: string;     // 生成 {prefix}.card
}
```

### Sheet
```tsx
interface SheetProps {
  open: boolean;
  onClose: () => void;
  title?: string;
  children: ReactNode;
  testIdPrefix?: string;
}
```

### Modal
```tsx
interface ModalProps {
  open: boolean;
  onClose: () => void;
  title?: string;
  children: ReactNode;
  footer?: ReactNode;
  testIdPrefix?: string;
}
```

### Button
```tsx
type ButtonVariant = 'primary' | 'ghost' | 'danger';
type ButtonSize = 'sm' | 'md' | 'lg';
interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;   // 默认 primary
  size?: ButtonSize;         // 默认 md
  loading?: boolean;
  fullWidth?: boolean;
  testIdPrefix?: string;
}
```

### Tag
```tsx
type TagVariant = 'default' | 'primary' | 'success' | 'warning' | 'danger';
interface TagProps {
  variant?: TagVariant;
  size?: 'sm' | 'md';
  onClick?: () => void;
  children: ReactNode;
}
```

## 无对应组件 → 自定义 CSS 的场景

以下场景 ui-kit 没有直接对应组件，需自定义（在 `build-spec.json` 中标 `"ui_kit_component": "custom"`）：

- **学科筛选 chip 横向滚动栏**（类似 Tag 但可滚动，ui-kit Tag 不含滚动容器）
- **错题卡片左侧彩色竖条**（Card 不含 left-bar slot）
- **掌握度统计三格（未/部分/已）**（无对应聚合组件）
- **相机取景框 + 扫描动画**（纯自定义 CSS animation）
- **FAB 圆形拍照按钮**（Button 无 FAB 变体）
- **AI 讲解流式文本区**（无 streaming text 组件）
- **艾宾浩斯节点进度小方块组**（无 segment-bar 组件）

自定义区块必须：
1. CSS Module 所有色值引用 `var(--tkn-*)` · 禁硬编码
2. 间距引用 `var(--tkn-spacing-*)` · 禁硬编码 px
3. 带 `data-testid`（即使 ui-kit 不注入）
