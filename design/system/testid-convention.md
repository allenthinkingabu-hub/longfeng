# testid 规约 · Sd.6

> 落地计划 §16.2 Sd.6 · ESLint rule + verify-testid.sh 硬门禁.
>
> 目的：Playwright / 小程序 E2E / 单元快照测试 **selector 稳定** · 不被 class 重构打崩.

## 1. 命名规则

`<screen>.<region>.<element>[-{variant}]`

- `screen`：页面 P 编号映射 · 小写 · 如 `login` / `home` / `capture` / `analyzing` / `result` / `wronglist` / `wrongdetail` / `reviewtoday` / `reviewexec` / `reviewdone` / `calendarmonth` / `eventdetail` / `notifications` / `settings` / `landing` / `guestcapture` / `shared` / `welcomeback` / `observer`
- `region`：页面内语义分区 · 如 `hero` · `fab` · `tabbar` · `timeline` · `filter` · `sheet` · `modal`
- `element`：组件/DOM 角色 · 如 `btn` · `input` · `link` · `card` · `chip` · `dot` · `spinner` · `value`
- `variant`（可选）：如 `{plan_id}` / `{subject}` / `{n}` · **动态值用下划线分隔** · 不用点

**合法示例**：
```
login.phone.input
login.submit.btn
home.fab.capture
home.tabbar.tab-home
reviewtoday.due.card-123456
wronglist.filter.subject-math
observer.child.name-child-789
```

**非法示例**：
```
loginPhoneInput       // 无 . 分隔
login.phoneinput      // element 未用 btn/input 等词
home_fab              // _ 替 . 不允许
```

## 2. 必须带 testid 的元素清单

- 所有 `<button>` · `<a role="button">` · `<input>` · `<select>` · `<textarea>`
- 所有 `<a>` 链接（可点击）
- 所有 Toast / Dialog / Drawer / Sheet 的**根节点** + 主 CTA
- 所有状态节点 · 如 loading / empty / error / success 的**容器**
- 所有列表 item 的**根**（支持 `-{id}` 变体）

**可豁免**：纯装饰 · 非交互 `<div>` / `<span>` / `<img>`

## 3. ESLint rule · `enforce-testid`

- 静态扫描 JSX/TSX · 任何 `<button>` `<a>` `<input>` 等上列标签必须带 `data-testid`
- 违规 PR 阻断
- 规则实现留 Sd.2 Storybook 完成后补（依赖 ESLint 工作 · 本会话骨架）

## 4. 19 张 mockup HTML 已全量注入 `data-testid`

- 参见 `design/specs/P01..P19.md` · 每份"3. testid" 小节
- mockup HTML 文件内 `data-testid` 属性已填（历史 mockup 产出时 AI 已注入）
- 新增前端组件必须复用同一 testid · 禁止改名（破坏 Playwright spec）

## 5. 组件库 `testIdPrefix` prop

```tsx
<Button testIdPrefix="login.submit">提交</Button>
// 渲染为 <button data-testid="login.submit.btn">提交</button>
```

- 每个 ui-kit 组件导出 `testIdPrefix?: string` prop
- 内部逻辑：`data-testid={`${testIdPrefix}.${baseElement}`}`
- Storybook 默认注入 `testIdPrefix` · 防止遗漏（Sd.2 实现）

## 6. 变更审批

- testid 命名 **不可随意改** · 改 = 破坏既有 Playwright spec
- 如需改 · PR 描述列出 "before / after" · Design Reviewer 审批 · spec 同 PR 更新
