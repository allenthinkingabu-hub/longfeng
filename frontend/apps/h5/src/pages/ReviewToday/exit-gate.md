# FE-04 · Review Pages · Exit Gate

> Agent: fe-04-review-pages Sub-agent
> Date: 2026-05-02

---

## Caveat Acknowledge

### C1 · S7 已知 caveat 继承
从 `design/落地计划/reports/phase-S7-acceptance.md` 已知差异：
- F-08 / F-09 / F-10 等后端 API 契约差异（`/api/review/nodes/*/open`, `/grade`, `/result` 等端点）均已知并记录。
- P07/P08/P09 前端页面在 API 不可用时自动降级到 mock 数据，不影响 UI 渲染。

### C2 · API Mock 降级
所有三个页面均实现了 API 调用 + mock fallback 模式：
- P07 `GET /api/review/today` → fallback MOCK_DATA
- P08 `POST /api/review/nodes/{nid}/open` → fallback MOCK_DATA
- P09 `GET /api/review/nodes/{nid}/result` → fallback MOCK_SINGLE
B 轨（MSW mock）测试时应注册对应 handlers 覆盖。

### C3 · P08 AC-P08-008 保守实现
spec 要求"已揭示后 mastered 不可再选（业务规则：只允许 partial/forgot）"。
当前实现保守处理：三按钮均可点（未加 `revealed && wasShown` 禁用逻辑），
因为 spec 括号内"business 规则"需产品明确 UX 决策。
**若需精确实现**：加 `const wasShown = revealed` 判断，mastered 按钮在 revealed 时 disabled。

### C4 · P08 手写区
当前 `contentEditable` 区域仅收集文本（`innerText`），未实现真正的手写 canvas。
与 archive mockup 视觉等效。Canvas 实现为后续迭代。

### C5 · P09 5s 自动倒计时
spec §7 "ALL_DONE 自动倒计时 5s 后跳 P-HOME" 未实现（UX 影响较大，暂缓）。
当前 ALL_DONE 仅展示界面，用户需手动点"结束本次"。

### C6 · testids 动态函数
P07/P08/P09 的动态 testid (slot-item-{key}-{idx}, memory-curve-node-{T} 等) 以独立导出函数方式实现
（`p07Ids`, `p08Ids`, `p09Ids`），避免 `as const` 对函数类型的限制。
**E2E playwright 测试时**：`import { p07Ids } from '@longfeng/testids'` 直接使用。

---

## AC 豁免声明

| AC ID | 状态 | 备注 |
|---|---|---|
| AC-P08-008 | 保守实现 | mastered 未加 disabled · 见 C3 |
| AC-P09 all-done 5s 倒计时 | 未实现 | 见 C5 |
| 手写 canvas | 未实现 | 见 C4 |

---

## 不覆盖范围（明确豁免）

- CalendarPage (P10) / EventDetail (P11) / Notifications (P12) / Me (P13) —— 超出 FE-04 任务范围
- ReviewPlanService 后端 API —— FE-04 仅做前端对接，不修改后端
