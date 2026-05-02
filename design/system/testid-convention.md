# testid 命名规约 v2.0 · kebab-case

> **更新于 2026-05-02**：与 `design/system/DESIGN.md` §4.3 对齐。
> 旧版（点分隔 `<screen>.<region>.<element>`）已废弃。kebab-case 是 W3C / Playwright / Storybook / a11y 测试的事实标准，且无 CSS 选择器转义问题。
> 历史 19 张 mockup HTML 经 grep 验证均未注入 testid，因此本次切换无破坏成本。

## 1. 命名规则

```
{block-id-kebab}[-{role}][-{index}]
```

- **block-id-kebab**：取自该页 spec.md §3 Block 清单的 `testid root` 字段
- **role**（可选）：子元素角色，如 `name` / `icon` / `button` / `input` / `progress` / `count` / `value` / `chip`
- **index**（可选）：列表项序号，从 1 起

**合法示例**：

```
greeting-hero                              # block root
greeting-hero-name                         # 子元素
greeting-hero-streak-icon                  # 子元素
today-review-card                          # block root
today-review-card-circle-progress          # 子元素
today-review-card-start-all-btn            # CTA 按钮
week-strip                                 # block root
week-strip-day-1                           # 索引
week-strip-day-1-tlevel-T2                 # 复合（索引 + role）
question-list-card-1                       # 列表 root（带索引）
question-list-card-1-thumbnail             # 列表内子元素
mastery-status-card-forgot                 # 状态变体作为 role
confetti-burst-particle-1                  # 装饰但仍标 testid（用于动效断言）
```

**非法示例**：

```
greetingHeroName            # ❌ 驼峰
greeting_hero_name          # ❌ 下划线
greeting.hero.name          # ❌ 点分隔（旧规约 · 已废弃）
GREETING-HERO               # ❌ 全大写
greeting-hero-中文           # ❌ 中文
```

## 2. 命名规则细则

1. 全部 **kebab-case**（小写 + 连字符）
2. block-id-kebab 必须 = 该页 spec.md `§3 Block 清单` 中的 `testid root` 字段，**禁止自创**
3. 子元素拼 `-{role}`；嵌套子元素继续拼 `-{role}`
4. 列表项拼 `-{index}`，**索引从 1 开始**（不从 0）
5. 列表项内子元素：`-{index}-{role}`，例 `question-list-card-1-thumbnail`
6. 同一页面内全局唯一
7. 与该页 spec.md `§8 AC 覆盖表` 双向锚定 —— 每条 AC 至少绑定 1 个 testid
8. 改名需走 PR Design Reviewer 审批 + 同步更新 spec.md + 全量回归 Playwright

## 3. 必须带 testid 的元素清单

- 所有 `<button>` · `<a role="button">` · `<input>` · `<select>` · `<textarea>`
- 所有 `<a>` 链接（可点击）
- 所有 Toast / Dialog / Drawer / Sheet 的**根节点** + 主 CTA
- 所有状态节点（loading / empty / error / success / READY 容器）
- 所有列表 item 的**根**（拼 `-{index}` 变体）
- 所有 spec.md `§3 Block 清单`中列出的 block root
- 所有需要在 AC 表 §8 验证的元素

**可豁免**：纯装饰 · 非交互 `<div>` / `<span>` / `<img>` 且不在 AC 表中

## 4. ESLint Rule（实施阶段补，本设计阶段先记录）

- 静态扫描 JSX/TSX：任何 `<button>` `<a>` `<input>` 等列出标签必须带 `data-testid`
- testid 值必须匹配正则 `^[a-z][a-z0-9]*(-[a-z0-9]+)*$`（kebab-case）
- 违规 PR 阻断
- 实施时机：Sd 阶段 ui-kit Storybook 设置完成后

## 5. 组件库 testIdPrefix prop

```tsx
<Button testIdPrefix="today-review-card-start-all">开始全部</Button>
// 渲染为 <button data-testid="today-review-card-start-all-btn">开始全部</button>
```

- 每个 ui-kit 组件导出 `testIdPrefix?: string` prop
- 内部逻辑：`data-testid={\`${testIdPrefix}-${baseElement}\`}` （注意是连字符不是点）
- Storybook 默认注入 `testIdPrefix` 防遗漏

## 6. 与 DESIGN.md 的关系

本规约是 DESIGN.md §4.3 (testid 命名规范) 的扩展实施细则。

- DESIGN.md §4.3：定义命名格式与铁律
- 本文件：定义元素清单、ESLint 规则、组件库 prop 约定
- 各页 spec.md §3 / §8：列出本页所有 testid 实例及其 AC 绑定

任意冲突以 DESIGN.md 为准。

## 7. 变更日志

| 日期 | 版本 | 变更 |
|---|---|---|
| 2026-05-02 | v2.0 | 全面切换 kebab-case · 与 DESIGN.md v1.0 对齐 · 明确与 spec.md §3 / §8 锚定 · 旧点分隔规约废弃 |
| 早期 | v1.x | 点分隔 `<screen>.<region>.<element>`（已废弃） |
