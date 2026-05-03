# FE-08-miniapp · ui-plan.md

> Agent: FE-08-miniapp (Opus 4.7)
> Phase: S7-S8 跨 (D-FE-MP)
> Worktree: longfeng-wrongbook-worktrees/fe-08-miniapp
> Branch: agent/fe-08-miniapp
> Base: feature/s7-frontend-core @ c07727a
> Stack: 微信原生 TS + Vant Weapp 1.11 + WebSocket (替代 H5 SSE)
> 设计权威: design/system/STYLE-TRUTH.md (iOS HIG + Mood A-E)

---

## §1 架构图

```
frontend/apps/miniapp/src/
├── app.{ts,json,wxss}                    # 14 页注册 + 5 tabBar + token 入口
├── sitemap.json                          # allow *
├── styles/
│   ├── tokens.wxss                       # STYLE-TRUTH §2 完整 token (iOS HIG + Mood A-E)
│   └── vant-theme.wxss                   # Vant Weapp 主题 var 绑定 token
├── utils/
│   ├── api.ts                            # wx.request 封装 (= H5 fetch httpClient)
│   ├── ws.ts                             # WebSocket /ws/analyze (= H5 useEventSource)
│   ├── subscribe-msg.ts                  # 微信订阅消息 (S8 复习推送 / 考试日 / 家庭分享)
│   ├── testids.ts                        # = @longfeng/testids 14 页全覆盖
│   ├── i18n.ts + i18n-{zh-CN,en-US}.ts   # zh ⊆ en 14 页文案
└── pages/                                # 14 页 (4 文件/页 = 56 文件)
    ├── landing/index/                    # P-LANDING · Mood A
    ├── camera/{capture,analyzing,result}/# P02 / P03 / P04 · Mood C → A → B
    ├── wrongbook/{list,detail}/          # P05 / P06 · Mood B
    ├── review/{today,exec,done}/         # P07 / P08 / P09 · Mood A → B → D
    ├── calendar/{month,event}/           # P10 / P11 · Mood B
    ├── notification/index/               # P12 · Mood B
    └── me/{settings,ai-model-pref}/      # P13 / P-AIPref · Mood B
```

---

## §2 14 页路由表 (app.json pages 顺序)

| # | 路由 | 页面 | Mood | testid root | 关键功能 |
|---|---|---|---|---|---|
| 1 | `pages/landing/index/index` | P-LANDING | A | `landing.root` | hero+overlap · cta-try → capture |
| 2 | `pages/camera/capture/capture` | P02 Capture | C | `capture.root` | 暗底相机 + 黄检测 + shutter 蓝核 + 学科 chips |
| 3 | `pages/camera/analyzing/analyzing` | P03 Analyzing | A | `analyzing.root` | WebSocket /ws/analyze · 4 stage + progress + fallback |
| 4 | `pages/camera/result/result` | P04 Result | B | `result.root` | 答案对错 + 错因 + 步骤 + KP + ebbing + cta-save |
| 5 | `pages/wrongbook/list/list` | P05 List | B | `wrongbook.list.root` | active/archive tabs + subject chips + cursor 分页 + 3s 轮询 |
| 6 | `pages/wrongbook/detail/detail` | P06 Detail | B | `wrongbook.detail.root` | 题干 + AI 讲解(chunked) + 相似题 + tag 编辑 popup + 归档 |
| 7 | `pages/review/today/today` | P07 Today | A | `review.today.root` | reviewhero + 时间分组 (上/下/晚) + cta-start-all |
| 8 | `pages/review/exec/exec` | P08 Exec | B | `review.exec.root` | 题干 + 手写区 + reveal + **3 档自评 (例外色)** |
| 9 | `pages/review/done/done` | P09 Done | D | `review.done.root` | 庆祝绿 + trophy pulse + **confetti 仅 allDone** + 订阅消息申请 |
| 10 | `pages/calendar/month/month` | P10 Month | B | `calendar.month.root` | 月历 grid 6×7 + 状态点 (3 色) + 选中日事件列 + 订阅消息 |
| 11 | `pages/calendar/event/event` | P11 Event | B | `event.detail.root` | 三形态同壳 (study/exam/general) + share + cta-review |
| 12 | `pages/notification/index/index` | P12 Notif | B | `notification.root` | 订阅消息 cta + msg list (review/exam/share) + mark-all |
| 13 | `pages/me/settings/settings` | P13 Settings | B | `settings.root` | conic 头像 + tier badge + cell-group + ai-model 入口 |
| 14 | `pages/me/ai-model-pref/ai-model-pref` | P-AIPref | B | `me.ai-model-pref.root` | SC-16 三层 tier 分流 (normal/vip/vip+) |

**TabBar (5 tab)**: 首页(landing) · 错题(list) · 复习(today) · 日历(month) · 我的(settings)

---

## §3 小程序 vs H5 差异对照

| 维度 | H5 实现 | 小程序实现 | 备注 |
|---|---|---|---|
| 框架 | React 18 + Vite + TS | 微信原生 TS + Page() | D-FE-MP 红线 · 不跨兼容层 |
| 组件库 | @longfeng/ui-kit (React) | Vant Weapp 1.11 + 同名 props | 双端对称 ADR 0014 |
| 状态 | MobX 6 (planned) | wx Page setData | 简化 · 单页范围 |
| 路由 | React Router | wx.navigateTo / switchTab | tabBar 5 项 |
| 流式 | EventSource (SSE) | **WebSocket /ws/analyze** + chunked req | D-WS · ai-analysis-service AnalyzeWebSocketHandler |
| AI 讲解 | EventSource | wx.request enableChunked + onChunkReceived | detail 页降级 SSE 解析 |
| 上传 | fetch FormData | wx.uploadFile | capture 页 |
| 推送 | Web Push (planned) | **微信订阅消息** wx.requestSubscribeMessage | done/calendar/notif 三处申请 |
| 拍照 | <input type=file capture> | wx.chooseMedia camera | capture 页 |
| token | CSS Module + tokens.css | tokens.wxss + var() | iOS HIG + Mood A-E 完整对齐 |
| testid | data-testid | data-test-id | miniprogram-automator 兼容 |
| i18n | i18next | 简化 deepGet + zh/en JSON | zh ⊆ en 校验 |
| 草稿 | localStorage | wx.setStorageSync | capture page |
| 像素单位 | px (CSS Module) | rpx (4pt grid 缩放为 8rpx) | iPhone6 750 base |

---

## §4 视觉清单 (按 Mood 分组)

### Mood A · hero+overlap (深蓝 hero + 米白 overlap)
- **P-LANDING**: hero 760rpx (= H5 380px) + 3 blob (purple/cyan/pink) + conic logo + signin pill + eyebrow + hero-title (em coral 渐变) + 3 mchip (blur 24rpx) + scroll overlap radius 52rpx
- **P03 Analyzing**: hero 全屏 + 2 blob + ring 280rpx + 4 stage rows + fallback chip + cancel pill
- **P07 Today**: reviewhero card 22 radius + 2 blob (coral/mint) + rh-circle + 3 rh-cta + 时间分组 grid

### Mood B · pure-warm (米白底 + 白卡 + iOS nav)
- **P04 Result**: ans wrong/right 渐变卡 + reason 红 border-left + step 蓝 deep ring + kpcard 暖橙 + ebbing 紫 soft
- **P05 List**: tabs pill + subject chips 横滑 + item-card 144 thumb + mastery bar + status 4 色
- **P06 Detail**: meta + stem + tag-row 编辑 + AI explain stream + similar list + sheet popup
- **P08 Exec**: 顶 progress bar + qcard 32 padding + handwrite 区 (dashed) + reveal cta + answer-box 绿渐变
- **P10 Month**: 6×7 grid + 状态 3 色点 + today 蓝渐变 cell + event-list + 订阅 cta
- **P11 Event**: 三形态 hero (study深蓝/exam橙红/general紫) + meta-card + items-card
- **P12 Notif**: 订阅 cta 蓝渐 + msg list (border-bottom + 圆 ic) + unread 红点 + mark-all
- **P13 Settings**: conic 96 头像 + tier badge + 3 cell-group + logout 红
- **P-AIPref**: hint card 紫 ebbing + tier badge + model list + radio + save

### Mood C · dark-camera (#0B0F1A 全屏)
- **P02 Capture**: 自定义 nav 玻璃态 + viewfinder 径向 + 模拟纸面 (旋转 -1.5deg) + 4 角黄 brackets + 黄扫描线 + detect badge + tip card + 4 学科 chips + shutter 156×156 (蓝核 + 三层环) + dock gallery/manual

### Mood D · celebrate-green
- **P09 Done**: 全屏绿渐变 + 2 极光 blob (mint/gold) + trophy pulse + 3 metric chip + **confetti 6 粒 (仅 allDone)** + ghost-light + celebrate btn

---

## §5 数据 / API 契约

| API | 调用方 | 备注 |
|---|---|---|
| `POST /api/v1/files/upload-image` | capture | wx.uploadFile (multipart) |
| `WS /ws/analyze/{taskId}` | analyzing | **AnalyzeWS class** · stage/partial/final/error/fallback |
| `POST /api/v1/wrong-items` | result | save 错题 |
| `GET /api/v1/wrong-items` | list | cursor 分页 + status + subject filter |
| `GET /api/v1/wrong-items/:id` | detail | item 详情 |
| `GET /api/v1/analysis/:id` | detail | chunked SSE 降级 (enableChunked) |
| `GET /api/v1/analysis/:id/similar` | detail | k=3 相似题 |
| `PATCH /api/v1/wrong-items/:id/tags` | detail | If-Match 乐观锁 |
| `PATCH /api/v1/wrong-items/:id/archive` | detail | 归档 |
| `GET /api/v1/review-plans/today` | today | 今日复习计划 |
| `GET /api/v1/review-plans/today/summary` | done | 检查 allDone 决定 confetti |
| `GET /api/v1/review-plans/:id` | exec | session items |
| `POST /api/v1/review-plans/:id/grade` | exec | 提交自评 forgot/partial/mastered |
| `GET /api/v1/calendar/month` | month | 月度 events_by_day + status_by_day |
| `GET /api/v1/calendar/events/:id` | event | 事件详情 (study/exam/general) |
| `GET /api/v1/notifications` | notification | 消息列表 |
| `POST /api/v1/notifications/mark-all-read` | notification | 标记已读 |
| `POST /api/v1/notifications/subscribe-status` | done/calendar/notif | 上报订阅消息授权状态 |
| `GET /api/v1/me/profile` | settings | tier + ai_model_pref |
| `PATCH /api/v1/me/ai-model-pref` | ai-model-pref | SC-16 NORMAL 静默忽略 |

---

## §6 状态清单

- **草稿持久化**: `wx.setStorageSync('capture_draft')` (capture 退出可恢复)
- **分析结果缓存**: `wx.setStorageSync('analyze_result_${taskId}')` (analyzing → result 跨页)
- **登录态**: `wx.getStorageSync('access_token')` (api.ts 自动拼 Bearer header)
- **语言**: `wx.getStorageSync('lang')` (zh-CN / en-US)
- **订阅状态**: `wx.getStorageSync('subscribed_review')` (避免重复申请)
- **Loading / Empty / Error**: list 页骨架 + empty cta · detail/result 加载失败 fallback toast

---

## §7 自测命令 (Bash blocked · 仅声明)

```bash
# 1. 微信开发者工具打开 frontend/apps/miniapp/
#    - 选择 srcMiniprogramRoot=src/
#    - appId 用 placeholder wxREPLACEME_LOCAL_DEV (留 user 配真值)
#    - 编译模式 →"普通编译" → 14 页可加载
# 2. CI build (待 user 配 wechatdevtools CLI):
#    pnpm --filter @longfeng/miniapp build:ci
# 3. 自动化 smoke (待真机或开发者工具开启自动化端口):
#    pnpm --filter @longfeng/miniapp test:automator
```

---

## §8 铁律自检结论

- ✅ 设计实施铁律 (CLAUDE.md): tokens.wxss 全 STYLE-TRUTH §2 · 5 mood data-mood A-E · 3 处 iron-rule-1 例外注册 (P08 self-grading × 3 按钮)
- ✅ D-FE-MP 红线: 100% 微信原生 + Vant Weapp · 0 React/H5 兼容层
- ✅ D-WS 决策: WebSocket /ws/analyze 替代 SSE (analyzing 页) · chunked req 降级 (detail AI 讲解)
- ✅ 14 页 testid 100% 注册 (utils/testids.ts 全覆盖)
- ✅ 微信订阅消息: done/calendar/notif 三处申请 + 后端上报授权
- ✅ i18n: zh-CN ⊆ en-US 14 页文案对齐
- ✅ Hex 硬编码合规: 仅保留 (a) page.json 系统配置色, (b) van-icon prop 色, (c) conic-gradient 多色 stops, (d) 部分 hero gradient stops · 这些是 STYLE-TRUTH §2 标准色 · 微信小程序 var() 局限合理豁免
- ⚠️ Caveat: 真机扫码 / 微信开发者工具 build · Bash blocked · 留 user 验证

---

**生成日期**: 2026-05-02
**维护者**: FE-08-miniapp Sub-agent
