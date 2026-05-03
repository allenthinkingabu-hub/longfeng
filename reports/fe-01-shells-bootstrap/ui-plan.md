# FE-01-shells-bootstrap · ui-plan.md

## 架构总览

```
App (main.tsx)
  └── bootstrap/resolve-entry.ts   ← 入口决策树（冷启动/深链/分享）
        ├── token 有效 → TabShell
        ├── shareToken / observerCode → AnonymousShell (P-SHARED / P-OBSERVER route)
        ├── 设备指纹命中 → AnonymousShell (P-WELCOMEBACK route, P1)
        └── 否则 → AnonymousShell (P-LANDING route)
```

## Shell 切换决策树（PRD §2A.3.1）

```
入口 (冷启动 / 深链 / 分享链)
  │
  ├─ 1. 持有合法 JWT（localStorage 或 Cookie）？
  │       ├─ YES → TabShell（直达 deeplink 目标页 or P-HOME）
  │       └─ NO ↓
  │
  ├─ 2. URL 含 /s/:shareToken 或 /observer/:code ？
  │       ├─ YES · shareToken → AnonymousShell (route: P-SHARED)
  │       ├─ YES · observerCode → AnonymousShell (route: P-OBSERVER)
  │       └─ NO ↓
  │
  ├─ 3. 设备指纹 device_fp 命中 account_device？（P1 占位）
  │       ├─ YES → AnonymousShell (route: P-WELCOMEBACK) [P1]
  │       └─ NO ↓
  │
  └─ → AnonymousShell (route: P-LANDING, default)
```

---

## AnonymousShell · 匿名 Shell

**mockup 参考**：`design/mockups/wrongbook/_archive/14_landing.html` (Mood A) + `18_observer.html` (Mood E)

**Mood**：A (P-LANDING / P-GUEST-CAPTURE / P-WELCOMEBACK) / E (P-OBSERVER / P-SHARED)

### 视觉区块清单（按 DOM 顺序）
- B-01 深蓝/青 Hero 背景（Mood A/E 渐变 + 3 blob blur）
- B-02 匿名 Nav（Logo 左 · 登录 pill 右 · 无 Tab Bar）
- B-03 页面内容 outlet（P-LANDING / P-GUEST-CAPTURE / P-SHARED / P-OBSERVER 路由各自）
- B-04 无 Tab Bar（铁律：匿名 Shell 禁止 Tab Bar）

### 交互流程清单
- F-01 冷启动 → resolve-entry → 判断 JWT/token/fp → 落位此 Shell
- F-02 右上"登录" pill → P00
- F-03 深链 wb://s/:token → P-SHARED route
- F-04 深链 wb://observer/:code → P-OBSERVER route

### 状态切换清单
- S-01 LOADING（resolve-entry 异步判断中）
- S-02 READY（已决定路由）

---

## TabShell · 已登录主 Shell

**mockup 参考**：`design/mockups/wrongbook/_archive/01_home.html` (Mood A/B tabbar)

**Mood**：A (P-HOME hero) / B (其余页面)

### Tab Bar 5 项（archive 01_home.html 结构）

| Tab | Icon | 路由 | 页面 |
|---|---|---|---|
| 首页 | 房子 | `/` | P-HOME |
| 错题本 | 文件 | `/wrongbook` | P05 List |
| 拍题 | 相机 | `/capture` | P02 Capture |
| 复习 | 时钟 | `/review` | P07 ReviewToday |
| 我的 | 人像 | `/me` | P13 Settings |

### 二级页路由（11条）
- `/capture` → P02 Capture
- `/analyzing/:taskId` → P03 Analyzing
- `/question/:qid/result` → P04 Result
- `/wrongbook` → P05 List
- `/wrongbook/:qid` → P06 Detail
- `/review` → P07 ReviewToday
- `/review/exec/:nodeId` → P08 ReviewExec
- `/review/done` → P09 ReviewDone
- `/calendar/month` → P10 CalendarMonth
- `/event/:eventId` → P11 EventDetail
- `/notifications` → P12 Notifications

### 视觉区块清单
- B-01 Tab Bar（底部 84px · 玻璃态 · `rgba(242,242,247,.86)` + `blur(22px) saturate(180%)`）
- B-02 路由内容区（flex:1，bottom padding = 84px 避被 tabbar 遮）
- B-03 复习 Tab badge（红色 · 待复习数）

### 交互流程清单
- F-01 Tab 切换 → 路由跳转（保持 scroll 位置）
- F-02 深链 wb:// → deeplink-router 解析 → 对应二级页
- F-03 ObserverGuard 写操作拦截（Tab 3 拍题 / Tab 4 复习按钮）

### 状态切换清单
- S-01 正常已登录
- S-02 Token 过期 → 跳 P00（redirect 回原页）

---

## ObserverShell · 观察者 Shell

**mockup 参考**：`design/mockups/wrongbook/_archive/18_observer.html` (Mood E)

**Mood**：E `teal-observer`

### 视觉区块清单（按 archive 18_observer.html DOM 顺序）
- B-01 深蓝→青渐变 Header 260px (`linear-gradient(160deg,#0F1A3D 0%,#1F3C8C 55%,#30B0C7 100%)`)
- B-02 SCOPE=READ Watermark（斜 38deg · 半透明白字 · user-select:none）
- B-03 匿名 Nav（返回 · "观察者 Observer" brand · 退出按钮）
- B-04 Observer Identity Card（角色/会话信息 · ● READ badge）
- B-05 被观察学生 Summary Card
- B-06 只读内容 Outlet（useObserverGuard 守护写按钮）
- B-07 Tab Ghost dock（aria-disabled + 斑马纹遮罩 · C4 红线实现）

### 交互流程清单
- F-01 任何写动词（保存/提交/复习）→ useObserverGuard 拦截 → Toast "观察者不可操作"
- F-02 退出按钮 → 清除 observer session → 跳 P-LANDING
- F-03 写按钮 ARIA aria-disabled="true" · tabIndex=-1

### 状态切换清单
- S-01 OBSERVER JWT 有效
- S-02 OBSERVER JWT 过期 → 跳 P-LANDING
- S-03 写操作触发 → 显示只读 Toast

---

## bootstrap/resolve-entry.ts · 入口解析

**决策节点**：

| 步骤 | 判断 | 结果 |
|---|---|---|
| 1 | localStorage `lf:token` 非空且未过期 | → `{ shell: 'tab', deeplink }` |
| 2 | URL 含 `/s/:token` | → `{ shell: 'anon', route: 'shared', shareToken }` |
| 3 | URL 含 `/observer/:code` | → `{ shell: 'observer', code }` |
| 4 (P1) | 设备 fp 命中（跳过，P0 直接 guest） | → `{ shell: 'anon', route: 'welcomeback' }` |
| 5 | 默认 | → `{ shell: 'anon', route: 'landing' }` |

---

## bootstrap/deeplink-router.ts · 深链解析

**支持的 4 类 wb:// scheme**：

| scheme | 目标 | 说明 |
|---|---|---|
| `wb://capture` | `/capture` | 拍题（需 TabShell） |
| `wb://review/exec/:nodeId` | `/review/exec/:nodeId` | 复习执行 |
| `wb://s/:shareToken` | `/s/:shareToken` | 分享预览 |
| `wb://observer/:code` | `/observer/:code` | 观察者 |

---

## hooks/useObserverGuard.ts · 观察者防护

**功能**：
- 读 JWT scope 字段
- scope === 'OBSERVER' → 所有写动词请求直接 reject + toast
- 返回 `{ isObserver, guardWrite }` 供组件使用
- 为写按钮注入 `aria-disabled="true"` + `tabIndex={-1}` (C4 红线)

---

## 文件清单（完整）

```
frontend/apps/h5/src/
  shells/
    AnonymousShell.tsx          ← 匿名 Shell（P-LANDING/P-GUEST/P-SHARED/P-OBSERVER）
    AnonymousShell.module.css
    TabShell.tsx                ← 已登录主 Shell（5 Tab + 11 二级页）
    TabShell.module.css
    ObserverShell.tsx           ← 观察者 Shell（scope=READ watermark + 写拦截）
    ObserverShell.module.css
  bootstrap/
    resolve-entry.ts            ← 入口决策树（JWT/share/observer/guest）
    deeplink-router.ts          ← wb:// scheme 解析（4 类深链）
  hooks/
    useObserverGuard.ts         ← 观察者写动词拦截 + ARIA aria-disabled
  types/
    shell.ts                    ← ShellType / EntryResult / DeeplinkRoute 类型定义

reports/fe-01-shells-bootstrap/
  ui-plan.md                   ← 本文档（架构/路由/决策树）
  exit-gate.md                 ← 验收说明 + b 模式 caveat
```

---

## 元素清单（element-checklist · b 模式）

> b 模式（Bash blocked）：无法运行 playwright / pixel-diff / pnpm dev。
> 以下为代码层逐元素核对，待 Orchestrator 跑 C/B 轨验收。

### AnonymousShell
- [ ] `data-testid="anon-shell"` 根元素
- [ ] `data-testid="anon-shell-nav"` 顶部导航
- [ ] `data-testid="anon-shell-logo"` Logo
- [ ] `data-testid="anon-shell-login-btn"` 登录 pill
- [ ] `data-testid="anon-shell-outlet"` 内容区

### TabShell
- [ ] `data-testid="tab-shell"` 根元素
- [ ] `data-testid="tab-shell-tabbar"` 底部 Tab Bar
- [ ] `data-testid="tab-home"` 首页 Tab
- [ ] `data-testid="tab-wrongbook"` 错题本 Tab
- [ ] `data-testid="tab-capture"` 拍题 Tab
- [ ] `data-testid="tab-review"` 复习 Tab（含 badge）
- [ ] `data-testid="tab-me"` 我的 Tab
- [ ] `data-testid="tab-shell-outlet"` 内容区

### ObserverShell
- [ ] `data-testid="observer-shell"` 根元素
- [ ] `data-testid="observer-watermark"` SCOPE=READ 水印
- [ ] `data-testid="observer-shell-nav"` 顶部导航
- [ ] `data-testid="observer-identity-card"` 身份卡片
- [ ] `data-testid="observer-scope-badge"` ● READ badge
- [ ] `data-testid="observer-exit-btn"` 退出按钮
- [ ] `data-testid="observer-shell-outlet"` 内容区（只读）

### useObserverGuard
- [ ] scope=OBSERVER 时 `isObserver === true`
- [ ] `guardWrite` 调用返回 rejected Promise + toast
- [ ] 写按钮 `aria-disabled="true"` 已注入
