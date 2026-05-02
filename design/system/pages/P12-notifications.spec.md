---
page_id: P12
name: 通知中心
name_en: Notifications Inbox
route_h5: /notifications
route_miniprogram: pages/notification/list
deeplink: wb://notifications
auth_state: authenticated
persona:
  - P1-K12
scenarios:
  - SC-02
mockup_canonical: design/mockups/wrongbook/12_notifications.html
mockup_version: v2
last_reviewed: 2026-05-02
status: spec-draft
sprint: S4
---

# P12 · 通知中心

> **使用说明**：Sprint 4 辅助页之一。承载推送 inbox（SC-02）与 SC-09 家长分享降级（推送被拒时聚合点）。
> **核心约束**：DESIGN.md §1 铁律 6（页面节奏二分）—— mood=warm 信息流到底，无 hero，仅顶部大标题 + 时间分组列表。

---

## §1 页面目的（why · 1 句话）

学生在一处看到所有"被系统记得 / 被家人提醒"的事，并能 1 tap 跳到对应页面（复习节点 / 事件详情 / 设置变更）或一次性全部已读。

---

## §2 ASCII 布局图

```
┌─────────────────────────────────────┐
│  B1 顶部标题区                       │
│   "通知" (display-hero 56px)         │
│   "全部已读" 蓝色链接 (右对齐)       │
├─────────────────────────────────────┤
│  B2 TimelineGroup "今天"             │
│   ┌────────────────────────────┐    │
│   │ 🔵 [icon] 标题            ●│ │ ← 未读 dot
│   │           副标题  · 10 min │    │
│   └────────────────────────────┘    │
│   ┌────────────────────────────┐    │
│   │ 🔴 [icon] 妈妈分享了考试日 │    │
│   │           5 月 12 · 周一    │    │
│   └────────────────────────────┘    │
├─────────────────────────────────────┤
│  B3 TimelineGroup "昨天"             │
│   ...                                │
├─────────────────────────────────────┤
│  B4 TimelineGroup "本周"             │
│   ...                                │
├─────────────────────────────────────┤
│  B5 TimelineGroup "更早"             │
│   ...                                │
├─────────────────────────────────────┤
│  EMPTY 态：插画 + "暂无新消息"        │
└─────────────────────────────────────┘
[Tab Bar: 首页 错题本 拍题 复习 我的]
```

> 滑动归档手势：每条 NotificationCard 左滑 80px 露出灰色"归档"按钮（参考铁律 7）

---

## §3 Block 清单（每块独立可实现单元）

| Block ID | 名称 | Section | Mood | Component | testid root | Tokens 用到 |
|---|---|---|---|---|---|---|
| `B1` | HeaderTitle 顶部标题区 | info | warm | (custom block) | `p12-header` | `--tkn-color-bg-light`, `--tkn-color-text-primary`, `--tkn-color-primary-DEFAULT`, `--tkn-type-display-hero`, `--tkn-type-link` |
| `B2` | TimelineGroup 今天分组 | info | warm | (custom block · 含 NotificationCard[]) | `p12-group-today` | `--tkn-color-card`, `--tkn-color-text-primary`, `--tkn-color-text-secondary`, `--tkn-color-sep`, `--tkn-radius-lg`, `--tkn-shadow-card-deep`, `--tkn-type-caption-bold` |
| `B3` | TimelineGroup 昨天分组 | info | warm | (同 B2) | `p12-group-yesterday` | (同 B2) |
| `B4` | TimelineGroup 本周分组 | info | warm | (同 B2) | `p12-group-thisweek` | (同 B2) |
| `B5` | TimelineGroup 更早分组 | info | warm | (同 B2) | `p12-group-earlier` | (同 B2) |
| `B6` | EmptyState 空态 | info | warm | L0.Empty | `p12-empty-state` | `--tkn-color-text-secondary`, `--tkn-type-body`, `--tkn-radius-circle` |
| `B7` | NotificationCard 单条卡 (group 子项) | info | warm | (custom block · 多次实例化) | `p12-notif-card-{index}` | `--tkn-color-card`, `--tkn-color-primary-DEFAULT`, `--tkn-color-system-danger-DEFAULT`, `--tkn-color-system-info-DEFAULT`, `--tkn-color-text-primary`, `--tkn-color-text-secondary`, `--tkn-color-encouragement-DEFAULT`, `--tkn-radius-md`, `--tkn-radius-circle`, `--tkn-type-body-emphasis`, `--tkn-type-caption` |

> **fe-preflight 用法**：B2-B5 4 个分组共享同一个 NotificationCard 实例；测试时 fixture 需覆盖每组至少 1 条。

---

## §4 数据契约（page-level interface）

```typescript
type NotifKind = 'REVIEW' | 'EXAM' | 'SHARE' | 'SYSTEM';

interface NotificationsResp {
  groups: {
    today: NotificationItem[];
    yesterday: NotificationItem[];
    thisweek: NotificationItem[];
    earlier: NotificationItem[];
  };
  unreadTotal: number;
}

interface NotificationItem {
  id: string;
  kind: NotifKind;
  title: string;
  subtitle: string;
  occurredAt: string;        // ISO timestamp
  read: boolean;
  // 跳转锚点
  targetType: 'EVENT' | 'NODE' | 'SETTING' | 'STATIC';
  targetId?: string;         // eventId / nodeId / settingKey
  // 来源（SHARE 形态用）
  fromUser?: { name: string; role: 'PARENT' | 'TEACHER' };
}
```

> 类型不一致直接 fail。

---

## §5 API 触点

| Method | Path | 用途 | P95 预算 | 失败降级 |
|---|---|---|---|---|
| GET | `/api/notifications` | 主聚合接口 | 500ms | 缓存空时 ERROR + 重试 |
| POST | `/api/notifications/{id}/read` | 单条已读 | 200ms | toast "标记失败"，本地仍变灰 |
| POST | `/api/notifications/read-all` | "全部已读" | 300ms | 失败保留未读态 |
| POST | `/api/notifications/{id}/archive` | 滑动归档 | 200ms | 卡片回弹原位 |

---

## §6 状态机

| State | 触发 | UI 表现 |
|---|---|---|
| `LOADING` | 进入页面 | 4 组分组骨架（每组 2 个卡骨架） |
| `READY` | API 200 + 任意分组非空 | 完整渲染 + 顶部"全部已读"启用 |
| `EMPTY` | API 200 + 4 组全空 | B6 空态插画 + "暂无新消息"文字 |
| `ERROR` | API non-2xx | 错误占位 + 重试链接 |
| `READ_ALL_PENDING` | "全部已读" tap → POST 中 | 顶部链接变 spinner，未读 dot 立即消失（乐观更新） |
| `ARCHIVING` | 单条左滑 → 显示归档 + tap | 卡片淡出 200ms，列表自然收缩 |

---

## §7 跳转图

```
[入口]
  Tab "我的" 红点 ─┐
  推送横幅 tap     ─┤
  P-HOME 消息卡 ─┤── → P12
  深链 wb://notifications ─┘
                  │
        ├──[B1 全部已读]──→ 本页 READ_ALL_PENDING (不离开)
        ├──[B7 卡片 tap]──→ 按 targetType 路由：
        │     EVENT  → P11(事件详情)
        │     NODE   → P08(复习执行)
        │     SETTING → P13(设置 · 锚定 settingKey)
        │     STATIC → 当前页静态 toast / sheet
        └──[B7 左滑归档]──→ 本页内移除卡片
```

---

## §8 AC 覆盖表（与 business-analysis.yml 双向锚定）

| AC ID | 验收点 | 涉及 Block | testid 验证点 |
|---|---|---|---|
| `AC-P12-001` | 顶部标题"通知"使用 display-hero 字号 + "全部已读"链接右对齐 | B1 | `p12-header-title` 文本 = "通知"；`p12-header-mark-all-read` 可点击 |
| `AC-P12-002` | 4 个时间分组按非空才渲染 · 每组 group label = "今天/昨天/本周/更早" | B2-B5 | `p12-group-today/yesterday/thisweek/earlier` 出现条件 = group 非空 |
| `AC-P12-003` | 每条 NotificationCard 含 icon + 标题 + 副标题 + 时间 + 未读 dot | B7 | `p12-notif-card-{n}-icon`, `-title`, `-subtitle`, `-time`, `-unread-dot` 各 1 |
| `AC-P12-004` | 未读 dot 在 `read=true` 时不渲染 | B7 | `p12-notif-card-{n}-unread-dot` 当 `data-read="true"` 时不在 DOM |
| `AC-P12-005` | icon 颜色按 kind 切：REVIEW=蓝 / EXAM=红 / SHARE=橙 / SYSTEM=info 蓝 | B7 | `p12-notif-card-{n}-icon` 含 `data-kind=` + 背景色命中对应 token |
| `AC-P12-006` | tap 一条 REVIEW kind + targetType=NODE → 路由 `/review/exec/:nid` (`SC-02` 推送 inbox) | B7 | `p12-notif-card-{n}` click → 路由匹配 |
| `AC-P12-007` | tap "全部已读" → POST `/api/notifications/read-all` + 所有 unread-dot 立即消失 (乐观) | B1 | `p12-header-mark-all-read` click 后 `[data-read="false"]` 卡片消失 |
| `AC-P12-008` | EMPTY 态显示 B6 插画 + 文案"暂无新消息" | B6 | `p12-empty-state` 仅在所有组为空时可见 |
| `AC-P12-009` | [AI 推测] 滑动归档手势：左滑 80px 露出归档按钮，tap 后卡片淡出 200ms | B7 | `p12-notif-card-{n}-archive-btn` 在滑动后可见 |
| `AC-P12-010` | [AI 推测] SC-09 家长分享 EXAM 推送被拒时降级为 SHARE kind 单条置顶 | B2 | `p12-notif-card-1` 在 today 组首位 + `data-kind="SHARE"` |

> **fe-accept-mock 用法**：每条 AC 至少绑定 1 个 testid；缺一个变体即 fail。
> **AC 缺失处理**：业务文档 §2A.4 P12 仅给出"通知中心"概念，无明细行为；AC-P12-009 / -010 标 `[AI 推测]` 供业务方 review。

---

## §9 异常路径

| 触发 | UI | 系统行为 |
|---|---|---|
| API 网络失败 | 整页错误占位 + 重试链接 | 不消费埋点 |
| 单条 read 失败 | toast "操作失败"，dot 回退恢复 | 重试不阻塞其他卡 |
| 归档冲突（重复归档） | 卡片回弹原位 | 后端返回 409 不报错 |
| 推送权限被拒 | EMPTY 态 + "去设置开启推送 →" 链接到 P13 | SC-09 路径 |
| 通知数 > 200 条 | 仅渲染 today + yesterday + 最近 7 天本周 + earlier 折叠"加载更多" | 分页 cursor |

---

## §10 埋点事件

| 事件名 | 触发 Block | 必带属性 |
|---|---|---|
| `notif_view` | 进入 P12 | `unread_total, today_count` |
| `notif_card_tap` | B7 卡片 tap | `notif_id, kind, target_type, target_id` |
| `notif_mark_read_one` | 单条 read | `notif_id, kind` |
| `notif_mark_read_all` | B1 全部已读 | `total_count` |
| `notif_archive` | B7 滑动归档 | `notif_id, kind` |
| `notif_fallback_inapp` | SHARE kind 因推送降级落入 inbox | `from_role, exam_id` |
| `wb_push_click` | 来源是推送 inbox 跳 P08 | `taskId, channel=inbox` |

> 所有事件经 `packages/analytics` 包。

---

## §11 性能预算

- TTI ≤ 700ms
- LCP ≤ 600ms（首屏第一个 NotificationCard）
- CLS < 0.05
- API P95 ≤ 500ms
- read-all 乐观更新 < 50ms 视觉反馈
- 归档动画 200ms 内完成

---

## §12 A11y

- Landmarks: `<main role="main">` 包裹全部；B2-B5 用 `<section role="region" aria-label="今日通知">`；列表用 `<ul role="list">`
- 焦点顺序: 标题 → "全部已读" → 第一个分组 label → 第一条卡 → 第二条卡 → ... → 下一组 label
- 屏幕阅读器朗读优先级: 顶部"通知 · X 条未读" `aria-live="polite"`；新通知插入时朗读
- `prefers-reduced-motion`: 关闭归档淡出动画（瞬切移除 DOM）；关闭未读 dot 脉冲动画

---

## §13 Mockup 锚定

- **权威**: `design/mockups/wrongbook/12_notifications.html` (v2)
- **历史变体**: 早期 v1（无时间分组）已废弃
- **截图**: `design/system/screenshots/P12-v2-light.png`（fe-preflight 自动生成）
- **反向锚定**: mockup HTML `<head>` 含 `<meta name="design-spec" content="P12-notifications.spec.md@v2">`

---

## §14 Tokens 清单（grep 校验用）

> mockup HTML 的 CSS 中**每一处** hex / rgb / px 数值必须能映射回这张清单中的 token。grep 不命中 = lint fail。

```
L1 (Apple base):
  --tkn-color-primary-DEFAULT
  --tkn-color-system-danger-DEFAULT
  --tkn-color-system-info-DEFAULT
  --tkn-color-text-on-dark
  --tkn-font-display
  --tkn-font-text
  --tkn-type-display-hero
  --tkn-type-tile-heading
  --tkn-type-body
  --tkn-type-body-emphasis
  --tkn-type-caption
  --tkn-type-caption-bold
  --tkn-type-link
  --tkn-type-micro
  --tkn-spacing-2
  --tkn-spacing-xs
  --tkn-spacing-sm
  --tkn-spacing-12
  --tkn-spacing-md
  --tkn-spacing-lg
  --tkn-spacing-xl
  --tkn-radius-sm
  --tkn-radius-md
  --tkn-radius-lg
  --tkn-radius-pill
  --tkn-radius-circle
  --tkn-shadow-focus
  --tkn-motion-duration-fast
  --tkn-motion-duration-base
  --tkn-motion-ease-standard

L2 (warmth · 整页 mood=warm):
  --tkn-color-bg-light
  --tkn-color-card
  --tkn-color-text-primary
  --tkn-color-text-secondary
  --tkn-color-sep
  --tkn-color-encouragement-DEFAULT
  --tkn-color-encouragement-soft
  --tkn-shadow-card-deep

L3 (celebration):
  (本页不使用)

EXCEPTION (subject):
  (本页不使用 · 通知 icon 用系统轨色)
```
