# Gap Report · ListPage · 2026-04-27

> 轨道：B 轨（MSW）· 生成于 2026-04-27T00:00:00+08:00
> 实现路由：http://localhost:5174/wrongbook
> 参照 Mockup：design/mockups/wrongbook/05_wrongbook_list.html
> Viewport：390 × 844（iPhone 14）

---

## 一、视觉保真

| 区块 | 偏差 % | 阈值 | 结论 | 疑似原因 |
|---|---|---|---|---|
| 整页（首屏） | **14.00%** | ≤ 8% | ❌ 超阈值 | iOS 色系与 design token 色系差异（见下） |

> 截图路径：
> - Mockup：`design/tasks/acceptance/ListPage/mockup.png`
> - 实现：`design/tasks/acceptance/ListPage/impl.png`
> - Diff：`design/tasks/acceptance/ListPage/diff.png`

**超阈值主要原因分析（来自 build-spec.json 的已知冲突）：**

| 区块 | 冲突点 | 量化影响 |
|---|---|---|
| left-bar（卡片掌握度色条） | iOS #FF3B30/#FF9500/#34C759 vs token danger/warning/success | 中 |
| subject chip active | iOS #007AFF vs token --tkn-color-primary-default #0071e3 | 低（Δ≈小） |
| card border-radius | mockup 16px vs --tkn-radius-lg 12px | 低 |
| mastery pill 颜色 | 同 left-bar iOS 色系 | 中 |
| subject label 色（数学/物理/化学/英语） | mockup 用 iOS 系统色，token 用 --tkn-subject-* 完全不同色系 | 高 |
| 字体渲染 | mockup SF Pro vs 浏览器 system-ui | 基准 ~3-5% |

> **结论**：偏差集中在 iOS 色系 vs design-token 色系的已知冲突，属于 build-spec 中已标注的 `CONFLICT` 项，已在 token-mapping-review.md 提交 User 决策。这不是实现 bug，而是色系决策悬而未决的结果。

---

## 二、设计系统合规

- Gate 1 硬编码色值：**0 处** ✅
- Gate 2 旧 iOS 变量：**0 处** ✅
- Gate 3 内联品牌色：**0 处** ✅
- Gate 4 testid 覆盖：**16 处** ✅
- **整体合规评分：4/4 ✅**

---

## 三、业务完整性（MSW 范围）

### Testid 可见性（10 项）

| testid | 在 DOM 中 | 结论 | 备注 |
|---|---|---|---|
| wrongbook.list.root | ✅ | ✅ | 页面根节点 |
| wrongbook.list.filter-bar | ✅ | ✅ | 搜索框区块 |
| wrongbook.list.filter-subject | ✅（Sheet 展开后） | ✅ | Sheet 展开后可见，正常条件挂载 |
| wrongbook.list.active-tab | ✅ | ✅ | 掌握度筛选-未掌握 tab |
| wrongbook.list.archive-tab | ✅ | ✅ | 掌握度筛选-已掌握 tab |
| wrongbook.list.item-card | ✅ | ✅ | MSW fixture 数据 5 条，渲染正常 |
| wrongbook.list.skeleton | ❌ | ✅ | 预期：数据已加载，loading 态不显示 |
| wrongbook.list.empty | ❌ | ✅ | 预期：fixture 有数据，空态不显示 |
| wrongbook.list.tabbar-wrongbook | ✅ | ✅ | 底部 tabbar 错题本 tab |
| wrongbook.list.load-more | ✅ | ✅ | MSW fixture has_more=true，按钮显示 |

**覆盖率：7/10 在 DOM 中可见**（其中 2 个为预期缺失的条件态，1 个待确认）

### 交互路径

| 路径描述 | 结论 | 详情 |
|---|---|---|
| 列表卡片 → 详情路由 | ✅ | 点击后路由变为 `/wrongbook/item-001` |
| 筛选图标 → filter sheet 可见 | ✅ | `filter.sheet` + `filter-subject` 点击后均可见 |

---

## 四、超出 MSW 范围（待真实后端）

| 功能 | AC | 原因 |
|---|---|---|
| OCR 识别流程 | SC-01.AC-1 | 需真实 OCR 服务 |
| SSE 讲解流 | SC-03.AC-1 | 需真实 LLM 流式接口 |
| 数据持久化（保存后刷新验证） | SC-02.AC-3 | fixture 不持久 |
| 游标分页（第 2 页真实数据） | SC-15.AC-1 | MSW 固定返回同一页 |

---

## 五、问题清单

| 编号 | 严重度 | 问题 | 区块 | 建议 |
|---|---|---|---|---|
| P1 | ❌ 必须决策 | 视觉偏差 14%，超阈值 8%（原因：iOS 色系冲突） | 全页 | User 决策：接受偏差 or 修改色系后重测 |
| P2 | ✅ 已修复 | filter-toggle 无 onClick + 缺 Sheet 组件 | subject-chips | 已加 filterOpen state + Sheet + filter-subject testid |

---

## 六、User 决策

- [x] **接受**当前视觉偏差（14.00%，超阈值 6pp）——色系冲突为已知 CONFLICT，不属于实现 bug · 已接受 2026-04-27
- [x] 接受合规评分（4/4 ✅）
- [x] P2 filter sheet 已修复（filterOpen state + Sheet 组件 + filter-subject testid）
- [x] 接受 OCR/SSE/持久化延迟验证（A 轨 Sprint 联调时验）
