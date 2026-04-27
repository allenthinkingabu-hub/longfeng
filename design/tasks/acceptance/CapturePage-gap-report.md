# Gap Report · CapturePage · 2026-04-27

> 轨道：B 轨（MSW）· 生成于 2026-04-27
> 实现路由：http://localhost:5174/capture
> 参照 Mockup：design/mockups/wrongbook/02_capture.html
> Viewport：390 × 844（iPhone 14）

---

## 一、视觉保真

| 区块 | 偏差 % | 阈值 | 结论 | 疑似原因 |
|---|---|---|---|---|
| 整页（首屏） | **40.07%** | ≤ 20% | ❌ 超阈值 | 见下方分析 |

> 截图路径：
> - Mockup：`design/tasks/acceptance/CapturePage/mockup.png`
> - 实现：`design/tasks/acceptance/CapturePage/impl.png`

**偏差主要原因分析：**

| 原因 | 影响估算 | 性质 |
|---|---|---|
| iOS 状态栏（9:41/信号栏）Mockup 有，实现无 | ~5% | 已知·可接受 |
| 取景框区域：Mockup 有纸张 + 题目 mockup 内容，实现为空取景框 | ~15% | 预期·相机未实时渲染 |
| 标题文字：Mockup「拍下错题」vs 实现「录入错题」 | ~2% | ⚠️ 文案不一致 |
| 模式 Tab：Mockup「相册/拍题/文档」vs 实现「照片/手动」 | ~5% | ⚠️ Tab 项不一致 |
| 底部控制区布局差异（快门样式、图标） | ~8% | 视觉差异 |
| 学科 chip 颜色：active 用 token 蓝 vs mockup 深色半透明 | ~3% | 已知·token 决策 |

> **结论**：取景框空内容（~15%）+ 布局差异（~13%）+ Mockup 静态内容（相机实时画面无法静态截图）是主要原因。其中部分属于相机页的固有特性，两项文案/Tab 不一致值得关注。

---

## 二、设计系统合规

- Gate 1 硬编码色值：**0 处** ✅
- Gate 2 旧 iOS 变量：**0 处** ✅
- Gate 3 内联品牌色：**0 处** ✅
- Gate 4 testid 覆盖：**16 处** ✅
- **整体合规评分：4/4 ✅**

---

## 三、业务完整性（MSW 范围）

### Testid 可见性（4 项）

| testid | 在 DOM 中 | 结论 | 备注 |
|---|---|---|---|
| capture.root | ✅ | ✅ | 页面根节点 |
| capture.form.subject | ❌ | ⚠️ | 实现用单独 testid（`capture.form.subject.math` 等），容器级未注册 |
| capture.camera.btn | ✅ | ✅ | 快门按钮可见 |
| capture.gallery.btn | ✅ | ✅ | 相册按钮可见 |

**实际 DOM 中的 testid（14 项）：**
`capture.root` · `capture.back` · `capture.flash` · `capture.form.subject.math/physics/chemistry/english/chinese` · `capture.camera.btn` · `capture.manual.btn` · `capture.gallery.btn` · `capture.shutter` · `capture.lighting` · `capture.file-input`

### 交互路径

| 路径描述 | 结论 | 备注 |
|---|---|---|
| capture.camera.btn 可见且可交互 | ✅ | 快门按钮正常 |
| capture.gallery.btn 可见 | ✅ | 相册入口正常 |

---

## 四、问题清单

| 编号 | 严重度 | 问题 | 建议 |
|---|---|---|---|
| P1 | ❌ 待决策 | 视觉偏差 40%，超阈值 20% | 分解原因：相机空取景框属固有，文案/Tab 不一致需修复 |
| P2 | ⚠️ 低 | 标题「录入错题」vs Mockup「拍下错题」 | 统一文案，建议改为「拍下错题」 |
| P3 | ⚠️ 低 | 模式 Tab「照片/手动」vs Mockup「相册/拍题/文档」| 确认产品决策：Tab 项目和文案是否对齐 |
| P4 | ℹ️ 低 | `capture.form.subject` 容器 testid 未注册 | build-spec 修正，或实现添加容器 testid |

---

## 五、超出 MSW 范围（待真实后端）

| 功能 | AC | 原因 |
|---|---|---|
| OCR 识别（拍题 → stem_text） | SC-01.AC-1 | 需真实 OCR 服务 |
| 文件直传 OSS | SC-11.AC-1 | `directUpload` PUT 到真实 URL |
| 拍题后 analyzing 状态轮询 | SC-01.AC-2 | 需真实 ai-analysis-service |

---

## 五-A、B 类修复记录（2026-04-27）

| 问题 | 修复 | 状态 |
|---|---|---|
| P2：标题文案「录入错题」→「拍下错题」 | 修改 `src/i18n/zh-CN.json` capture.title | ✅ 已修复 |
| P4：`capture.form.subject` 容器 testid 缺失 | 在 subjects 容器 div 追加 `data-testid={TEST_IDS.capture.form.subject}`，testid 数从 16 → 17 | ✅ 已修复 |
| P3：模式 Tab 文案「照片/手动」vs「相册/拍题/文档」| 产品决策挂起，等 User 确认 | ⏸ 待决策 |

---

## 六、User 决策

- [x] 接受视觉偏差（40.07%，超阈值 20%）——主因为相机取景框固有空态，非实现问题 · 已接受 2026-04-27
- [x] P2：标题文案「录入错题」→「拍下错题」已修复（zh-CN.json）
- [x] P4：`capture.form.subject` 容器 testid 已补（testid 数 16→17）
- [ ] P3：模式 Tab 文案「照片/手动」vs「相册/拍题/文档」——待产品确认
- [x] 接受合规评分（4/4 ✅）
- [x] 接受 OCR/上传延迟验证（A 轨 Sprint 联调时验）
