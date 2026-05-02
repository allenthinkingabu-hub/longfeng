---
page_id: P-XX
name: 页面中文名
name_en: Page English Name
route_h5: /xxx
route_miniprogram: pages/xxx/yyy
deeplink: wb://xxx
auth_state: authenticated | anonymous | observer
persona:
  - P1-K12
scenarios:
  - SC-XX
mockup_canonical: design/mockups/wrongbook/NN_xxx.html
mockup_version: v1
last_reviewed: YYYY-MM-DD
status: spec-draft       # spec-draft | spec-locked | implemented | shipped
sprint: S1               # S1 | S2 | S3 | S4 | S5
---

# P-XX · 页面中文名

> **使用说明**：本模板对应 DESIGN.md §4.2 的 14 段标准结构。每段都必填，找不到内容时标注 "[N/A · 理由]" 或 "[AI 推测 · 业务方 review]"。
> **填写顺序建议**：先填 frontmatter + §1 + §2 + §3，再依次填 §4–§14。

---

## §1 页面目的（why · 1 句话）

[一句话说清"学生打开这页是为了什么"。AI 实现遇到取舍时回到这句话拍板。]

---

## §2 ASCII 布局图

```
┌─────────────────────────────────────┐
│  [block-id-1 · mood=cool|warm|celebrate]
│  ...
├─────────────────────────────────────┤   ← mood 切换（如有）
│  [block-id-2 · mood=...]
│  ...
└─────────────────────────────────────┘
```

[ASCII 是给人类秒懂结构用的。AI 实现时配合 §3 块清单。]

---

## §3 Block 清单（每块独立可实现单元）

| Block ID | 名称 | Section | Mood | Component | testid root | Tokens 用到 |
|---|---|---|---|---|---|---|
| `B1` | xxx | hero / info | cool / warm / celebrate | M.GreetingHero（or L0 primitive） | `xxx-yyy` | `--tkn-...` |

> **fe-preflight 用法**：把 mockup HTML 切成这些块；fe-builder 按块逐个实现并跑 lint。

---

## §4 数据契约（page-level interface）

```typescript
interface XxxResp {
  // [page-level API 响应结构]
}
```

> 类型不一致直接 fail。

---

## §5 API 触点

| Method | Path | 用途 | P95 预算 | 失败降级 |
|---|---|---|---|---|
| GET | `/api/xxx` | 主聚合接口 | XXXms | [降级策略] |

---

## §6 状态机

| State | 触发 | UI 表现 |
|---|---|---|
| `LOADING` | 进入页面 | [全页骨架屏] |
| `READY` | API 200 + 数据 | [完整渲染] |
| `EMPTY` | API 200 + 数据为空 | [空态设计] |
| `ERROR` | API non-2xx OR 网络失败 | [错误展示] |

---

## §7 跳转图

```
[入口]
  来源 1 ─┐
  来源 2 ─┤──→ P-XX
  来源 N ─┘
        │
        ├──[block-1 操作]──→ 目标页 1
        ├──[block-2 操作]──→ 目标页 2
        └──[block-3 操作]──→ 目标页 3
```

---

## §8 AC 覆盖表（与 business-analysis.yml 双向锚定）

| AC ID | 验收点 | 涉及 Block | testid 验证点 |
|---|---|---|---|
| `AC-XXX-001` | [描述] | B1 | `xxx-yyy` 文本 = ... |

> **fe-accept-mock 用法**：用这张表逐条断言；缺 testid = 验收 fail。
> **AC 缺失处理**：从业务文档 §2A US-XX 提取；业务文档没有的标 `[AI 推测]` 前缀，业务方 review。

---

## §9 异常路径

| 触发 | UI | 系统行为 |
|---|---|---|
| [异常场景] | [UI 展示] | [系统响应] |

---

## §10 埋点事件

| 事件名 | 触发 Block | 必带属性 |
|---|---|---|
| `xxx_yyy` | B1 | `prop1`, `prop2` |

> 所有事件经 `packages/analytics` 包；`anon_*` / `obs_*` 事件必须携带 `device_fp` 或 `student_id_hash`，禁带原始 PII。

---

## §11 性能预算

- TTI ≤ XXXms
- LCP ≤ XXXms
- CLS < 0.05
- API P95 ≤ XXXms
- 骨架屏与数据切换 < 100ms 渐入

---

## §12 A11y

- Landmarks: [`<header role="banner">`, `<main role="main">`, `<nav role="navigation">`...]
- 焦点顺序: [block-1-cta → block-2-first-item → ...]
- 屏幕阅读器朗读优先级: [哪个 block 优先 / 哪些用 aria-live]
- `prefers-reduced-motion` 兜底: [关闭哪些动画]

---

## §13 Mockup 锚定

- **权威**: `design/mockups/wrongbook/NN_xxx.html` (vN)
- **历史变体**: [如有，列出]
- **截图**: `design/system/screenshots/P-XX-vN-light.png`（fe-preflight 自动生成）
- **反向锚定**: mockup HTML `<head>` 中含 `<meta name="design-spec" content="P-XX.spec.md@vN">`

---

## §14 Tokens 清单（grep 校验用）

> mockup HTML 的 CSS 中**每一处** hex / rgb / px 数值必须能映射回这张清单中的 token。grep 不命中 = lint fail。

```
L1 (Apple base):
  --tkn-color-primary-DEFAULT
  --tkn-type-XXX
  --tkn-spacing-XXX
  --tkn-radius-XXX
  --tkn-shadow-XXX
  --tkn-motion-XXX

L2 (warmth · 仅 mood=warm/celebrate 区段使用):
  --tkn-color-text-primary  (例 · 详见 STYLE-TRUTH §2.1)
  --tkn-color-encouragement-XXX
  --tkn-gradient-XXX
  --tkn-shadow-card  (例 · 详见 STYLE-TRUTH §2.5)

L3 (celebration · 仅庆祝白名单时刻):
  --tkn-color-mastery-XXX
  --tkn-color-celebrate-confetti-XXX
  --tkn-color-streak-fire
  --tkn-motion-celebrate-XXX

EXCEPTION (subject):
  --tkn-subject-{math|physics|chemistry|english}
```

---

## 模板使用清单（写新 spec.md 时勾选）

- [ ] frontmatter 全部字段填写（特别是 `sprint` / `mockup_canonical` / `last_reviewed`）
- [ ] §1 一句话目的写完
- [ ] §2 ASCII 布局图绘制
- [ ] §3 Block 清单 testid 与 §8 AC 表交叉对账（每条 AC 至少绑定 1 个 testid）
- [ ] §4 TS interface 与业务文档 §10 API 契约一致
- [ ] §5 API 路径与业务文档 §10 一致
- [ ] §6 状态机覆盖 LOADING/READY/EMPTY/ERROR 四态最小集
- [ ] §7 跳转图入口出口都列全
- [ ] §8 AC 表至少 5 条；缺失的 AC 标 `[AI 推测]`
- [ ] §10 埋点事件名与业务文档 §2A.8 字典一致
- [ ] §14 Tokens 清单与本页用到的 token 完全一致（多一个少一个 = lint fail）
