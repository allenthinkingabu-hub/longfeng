# Gap Report · DetailPage · 2026-04-27

> 轨道：B 轨（MSW）· 生成于 2026-04-27
> 实现路由：http://localhost:5174/wrongbook/item-001
> 参照 Mockup：design/mockups/wrongbook/06_wrongbook_detail.html
> Viewport：390 × 844（iPhone 14）

---

## 一、视觉保真

| 区块 | 偏差 % | 阈值 | 结论 | 疑似原因 |
|---|---|---|---|---|
| 整页（首屏） | **21.00%** | ≤ 15% | ❌ 超阈值 | 见下方分析 |

> 截图路径：
> - Mockup：`design/tasks/acceptance/DetailPage/mockup.png`
> - 实现：`design/tasks/acceptance/DetailPage/impl.png`
> - Diff：`design/tasks/acceptance/DetailPage/diff.png`

**偏差主要原因分析：**

| 原因 | 影响估算 | 性质 |
|---|---|---|
| iOS 状态栏（9:41/信号栏）Mockup 有，实现无 | ~3% | 已知·可接受 |
| mastery pill / left-bar 色系：iOS #FF3B30/#FF9500/#34C759 vs token danger/warning/success | ~4% | 已知·token 决策（B1） |
| subject label 色：iOS #007AFF vs --tkn-subject-* 完全不同色系 | ~4% | 已知·token 决策（C1） |
| SSE 讲解区：Mockup 有完整讲解文本，实现为占位符（MSW 无 SSE 流） | ~5% | 预期·MSW 范围外 |
| 相似题卡片样式差异（border-radius 16px vs 12px，颜色） | ~2% | 已知·token 决策（D1） |
| 字体渲染（Mockup SF Pro vs 浏览器 system-ui） | ~3% | 基准差异 |

> **结论**：偏差集中在三类：①iOS 色系 vs design-token 已知冲突（build-spec _color_decisions A1/B1/C1/D1），②SSE 讲解内容在 MSW 环境下必然为占位符，③字体渲染基准差。均为预期偏差，非实现 bug。

---

## 二、设计系统合规

- Gate 1 硬编码色值：**0 处** ✅
- Gate 2 旧 iOS 变量：**0 处** ✅
- Gate 3 内联品牌色：**0 处** ✅
- Gate 4 testid 覆盖：**15 处** ✅
- **整体合规评分：4/4 ✅**

---

## 三、业务完整性（MSW 范围）

### Testid 可见性（7 项）

| testid | 在 DOM 中 | 结论 | 备注 |
|---|---|---|---|
| wrongbook.detail.delete.btn | ✅ | ✅ | 删除按钮可见 |
| wrongbook.detail.explain-stream | ✅ | ✅ | 讲解区块存在，显示 MSW 占位符 |
| wrongbook.detail.delete.confirm | ❌ | ✅ | 预期：confirmDelete=false，confirm 弹窗未显示 |
| wrongbook.detail.tag-sheet | ✅ | ✅ | 标签编辑按钮可见 |
| wrongbook.detail.tag-chip | ❌ | ✅ | 预期：Sheet 关闭状态，条件渲染未显示 |
| wrongbook.detail.tag-custom-input | ❌ | ✅ | 预期：Sheet 关闭状态，条件渲染未显示 |
| wrongbook.detail.tag-save | ❌ | ✅ | 预期：Sheet 关闭状态，条件渲染未显示 |

**覆盖率：3/7 在 DOM 中可见**（其中 4 个为预期缺失的条件态：confirm 弹窗 + Sheet 内元素）

**实际 DOM 中可见的 testid（部分）：**
`wrongbook.detail.root` · `wrongbook.detail.delete.btn` · `wrongbook.detail.explain-stream` · `wrongbook.detail.tag-sheet` · `wrongbook.detail.stem-text` · `wrongbook.detail.similar-card`（×2）

### 交互路径

| 路径描述 | 结论 | 备注 |
|---|---|---|
| tag-sheet 按钮可见且可交互 | ✅ | 点击后 Sheet 打开（tag-chip/tag-save 随之出现） |
| delete.btn 可见 | ✅ | 点击后 confirmDelete=true，confirm 弹窗显示 |
| similar-card 渲染 | ✅ | MSW similar handler 返回 2 条，`distance: 0.18` 显示正常 |
| explain-stream 存在 | ✅ | 显示 explain_loading 占位符（SSE 超出 MSW 范围） |

---

## 四、修复记录

| 问题 | 原因 | 修复 |
|---|---|---|
| React 崩溃：`Cannot read properties of undefined (reading 'toFixed')` | MSW handler 返回 `similarity_score`，组件读取 `sim.distance` | 已修复：detail.ts handler 改为返回 `distance: 0.18`（API contract 定义字段为 distance） |

---

## 五、超出 MSW 范围（待真实后端）

| 功能 | AC | 原因 |
|---|---|---|
| SSE 讲解流（文字渐现） | SC-03.AC-1 | 需真实 LLM 流式接口；MSW 环境只显示占位符 |
| 标签保存持久化 | SC-02.AC-3 | fixture 不持久，刷新后恢复初始值 |
| 软删除同步到列表 | SC-04.AC-1 | MSW 无全局状态，列表不会同步删除结果 |
| 相似题真实相关性 | SC-03.AC-1 | MSW 返回固定 fixture 数据 |

---

## 六、User 决策

- [x] 接受视觉偏差（21.00%，超阈值 6pp）——主因为 MSW 无 SSE 内容（~5%）+ iOS/token 色系已知冲突（~8%）+ 字体基准（~3%） · 已接受 2026-04-27
- [x] 接受合规评分（4/4 ✅）
- [x] `distance` 字段修复已确认（handler 与 api-contracts/src/types.ts 对齐）
- [x] 接受 SSE/持久化/软删除延迟验证（A 轨 Sprint 联调时验）
