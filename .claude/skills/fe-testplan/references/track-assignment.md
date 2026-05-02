# 轨道分配决策树（A / B / C）

每条 TC 必须分配 1-3 条轨道。轨道分配错误会导致：A 轨跑了不需要后端的 TC（浪费）、B 轨跑了需要真后端的 TC（永远失败）。

## 三轨能力矩阵

| 能力 | A 轨（e2e + 真后端） | B 轨（MSW + Vite） | C 轨（仅截图 diff） |
|---|:---:|:---:|:---:|
| 视觉 pixel diff | ✅ | ✅ | ✅ |
| testid 可见性 | ✅ | ✅ | ⚪（无数据时部分区块不可见） |
| 路由跳转 | ✅ | ✅ | ❌（无交互） |
| 网络错误态 | ✅ | ✅（可 mock） | ❌ |
| 空态/边界 fixture | ✅ | ✅ | ❌（fixture 来源不存在） |
| OCR 真实流 | ✅ | ❌ | ❌ |
| SSE 流式响应 | ✅ | ⚪（mock 有限） | ❌ |
| 数据持久化（保存后刷新） | ✅ | ❌ | ❌ |
| 后端业务规则（next_review_at 重算等） | ✅ | ❌ | ❌ |
| 跨服务事件流（消息发布） | ✅ | ❌ | ❌ |

## 分配决策树（按 category）

```
┌── happy_path
│   ├── 涉及真后端独有行为？（OCR / SSE / DB 状态变更 / 跨服务）
│   │   ├── 是 → tracks = ["A"]（B/C 无法验）
│   │   └── 否 → tracks = ["A", "B"]（A 完整跑、B 用 MSW 同样能验）
│   └── 仅纯 UI 行为（无 API 调用）
│       └── tracks = ["B", "C"]（无需后端）
│
├── error_path
│   └── tracks = ["B"]
│       理由：mock 后端错误是 B 轨强项；A 轨触发后端错误代价高（可加但默认不加）
│
├── boundary
│   ├── 边界靠 fixture 触发（空数组/最大值/特殊枚举）
│   │   └── tracks = ["B"]（fixture 是 MSW 的本职）
│   └── 边界靠时区/locale/网络条件触发
│       └── tracks = ["A", "B"]（两边都验）
│
├── visual
│   └── tracks = ["B", "C"]
│       理由：pixel diff 是 C 轨核心；B 轨在跑业务的同时顺便截图
│
└── observable
    └── tracks = ["A"]
        理由：observable 即"只有真后端能验的副作用"——按定义不可能在 B/C 跑
```

## 调整规则

如果默认分配明显不合理，可调整。常见调整场景：

| 场景 | 默认 | 调整为 | 理由 |
|---|---|---|---|
| happy_path 但需要 OCR | `["A","B"]` | `["A"]` | B 轨 mock 不了 OCR 文件上传响应 |
| boundary 但仅靠 fixture | `["A","B"]`（如默认是这） | `["B"]` | 节省 A 轨开销 |
| visual 但是 hover/active 态 | `["B","C"]` | `["B"]` | C 轨无交互能力 |
| error_path 是 401（需真 JWT 过期） | `["B"]` | `["A","B"]` | B 轨可 mock 401，A 轨可验真实降级链路 |

## 反模式

- ❌ **A/B/C 全勾** — 浪费成本，且 C 轨基本只能验 visual 类
- ❌ **observable + B 轨** — observable 的定义就是"必须真后端"，加 B 轨 = 该 TC 在 B 轨永远失败
- ❌ **happy_path 只勾 C** — happy_path 通常需要数据驱动渲染，C 轨没数据
- ❌ **error_path 加 C 轨** — 错误态截图意义不大，且 C 无 mock 能力

## 与 priority 的交叉规则

| priority | 必须含的轨道 | 说明 |
|---|---|---|
| P0 happy_path | 至少含 A 或 B | P0 = 必须能机器验，pixel-only 不算 |
| P0 error_path | 必须含 B | error 是 MSW 的强项 |
| P0 boundary | 必须含 B | boundary 通常 fixture 触发 |
| P1 visual | C 轨可单独承担 | 视觉次要项可仅 C 轨 |

## 速查表

| 默认分配 | 适用 | 不适用 |
|---|---|---|
| `["A", "B"]` | happy_path 主流程 | OCR/SSE 类 |
| `["A"]` | OCR / SSE / 数据持久化 / observable | 纯 UI |
| `["B"]` | error_paths / boundary fixture | 真后端行为验证 |
| `["B", "C"]` | visual diff | 交互/状态类 |
| `["C"]` | 极少（仅静态结构 baseline） | 几乎所有动态内容 |
