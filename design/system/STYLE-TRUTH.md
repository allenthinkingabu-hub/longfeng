# STYLE-TRUTH · AI 错题本设计真相文档

> **文档定位**：本文是项目设计系统的**唯一真相来源**（single source of truth），反推自 `design/mockups/wrongbook/_archive/` 19 张原版 mockup。任何 AI 或人接手设计/前端工作前必读。
>
> **历史背景**：项目早期产生 19 张视觉精美的 mockup（_archive 目录）。后续 spec/DESIGN.md/tokens 在抽象过程中**偏离了原版真相**（用了 macOS 色 `#0071e3`、warm 暖棕字 `#2C2A26`、aurora 蓝紫粉极光等，而原版是 iOS HIG `#007AFF` + `#1C1C1E` + 深蓝渐变 + 多色 blob）。本文档是为了让下次工作不再偏离。
>
> **使用方式**：写 spec、画 mockup、写 CSS token 时，**以本文为准**；本文与现存 spec/DESIGN.md 冲突时，**以本文为准**，并把 spec/DESIGN.md 同步到本文。
>
> **更新规则**：本文只能基于 `_archive/` 实际 mockup 反推，不能凭想象添加。

---

## §1 设计哲学（一句话）

**iOS HIG 多彩 vibrant + 教育温度**：用深蓝 hero 建立专业可信感，用三层 radial blob blur 营造"AI 加持"的科技氛围，用 conic 彩虹 logo + 4 色 gradient icon 表达学科多样性，用玻璃态 chips/pills 制造轻盈现代感，用暖米橙 KP 卡 + gold→coral 渐变强调字传递学习鼓励。**不是 macOS 冷白极简，也不是纯 Apple HIG**——是"多彩 iOS + AI vibrant + 教育温暖"的复合语言。

**核心特征 5 条**：
1. 深蓝 hero（不是极光、不是黑色）+ 三层 radial blob（紫/青/粉/金）+ blur 18-28px
2. 完整 iOS HIG 调色板（蓝红橙绿靛黄紫青粉 9 色）
3. 玻璃态（rgba 白透 + backdrop-filter blur + saturate 180%）大量使用
4. Conic 彩虹（avatar/logo）+ 4 色 gradient icon 方块（quick entry / feature row）
5. 几乎无阴影（最深 0 8px 22px 5%），硬投影一律避免

---

## §2 完整 Token 库（精确到 hex/px · 反推自 archive 实测值）

### §2.1 颜色 · 系统主色（iOS HIG 全套）

```css
/* 表面 / 文字 */
--bg:       #F2F2F7;                  /* iOS Light bg */
--card:     #FFFFFF;
--sep:      rgba(60,60,67,0.14);      /* iOS separator */
--text:     #1C1C1E;                  /* 主文字 · 不是 #2C2A26 也不是 #1d1d1f */
--text2:    #3C3C43;                  /* 次文字（仅 04/02 用） */
--sec:      #636366;                  /* 副文字主用 */
--ter:      #8E8E93;                  /* 三级 / 禁用 */

/* 主色 · 状态色 · 辅色（iOS HIG 标准） */
--blue:     #007AFF;                  /* 主交互色 · 不是 #0071e3 */
--red:      #FF3B30;
--orange:   #FF9500;
--green:    #34C759;
--indigo:   #5856D6;
--yellow:   #FFCC00;                  /* 相机检测态主色 */
--purple:   #AF52DE;                  /* 设置 / 偏好 */
--teal:     #30B0C7;                  /* 系统通知 / observer */
--pink:     #FF2D55;                  /* 考试 / 家庭 / 特殊事件 */

/* 深蓝 hero 渐变 stops */
--hero-stop-1: #0F1A3D;               /* 极深 / hero card 起点 */
--hero-stop-2: #1E3A8A;               /* 深 / home hero 起点 */
--hero-stop-3: #1F3C8C;               /* hero card 中段 / landing 中段 */
--hero-stop-4: #3B5BDB;                /* home hero 中段 */
--hero-stop-5: #5B8DEF;                /* home hero 终点 */
--hero-stop-6: #5F5BDB;                /* hero card 终点 / landing 紫色段 */
--hero-stop-7: #8B87F6;                /* landing 终点 */

/* Blob 三色（hero 装饰用） */
--blob-purple:  rgba(88,86,214,.55);  /* indigo · 右上常用 */
--blob-cyan:    rgba(88,214,255,.55); /* sky cyan · 左下常用 */
--blob-pink:    rgba(255,45,85,.35);  /* pink · 中央偏左 */
--blob-coral:   rgba(255,107,107,.45);/* coral · 04/14 hero card 内 */
--blob-mint:    rgba(79,209,217,.35); /* mint · 04 hero card 内底 */
--blob-gold:    rgba(255,209,102,.40);/* gold · 14 landing 中央 */

/* Em 强调字渐变 */
--em-gradient:  linear-gradient(90deg, #FFD166 0%, #FF6B6B 100%);  /* gold→coral */
--em-gradient-amber: linear-gradient(90deg, #FFD166, #FFB454);     /* gold→amber · 01_home name em */

/* 学科色（铁律 1 例外 · subject palette） */
--subject-math:      #C41E3A;
--subject-physics:   #0057B7;
--subject-chemistry: #1A6B3A;
--subject-english:   #9C4F00;
/* 注：archive 中也常见多色简化版：math=#FF6B6B/红, phys=#FFD166/金, eng=#6DE895/绿 */

/* KP 卡（暖米橙渐变 · 唯一 warm token） */
--kp-bg-from:    #FFF4E6;             /* 暖米开始 */
--kp-bg-to:      #FFE0C2;             /* 暖橙结束 */
--kp-border:     rgba(255,149,0,.25);
--kp-text-title: #8B4513;             /* 棕 · 标题 */
--kp-text-body:  #A0522D;             /* 棕 · 正文 */
--kp-text-em:    #6B2C0F;             /* 棕 · 强调 */

/* 答案对错卡（04_result 专用） */
--ans-wrong-bg:  linear-gradient(160deg, #FFE8E6 0%, #FFF 70%);
--ans-right-bg:  linear-gradient(160deg, #E4F7EA 0%, #FFF 70%);

/* Camera 暗底（02 / 15_guest_capture） */
--camera-screen: #0B0F1A;             /* 全屏深蓝黑 */
--camera-vf-from: #1d2433;            /* viewfinder 中心 */
--camera-vf-to:  #0B0F1A;             /* viewfinder 边缘 */
```

### §2.2 字号系统（实测频率）

| 用途 | px | font-weight | letter-spacing | line-height | 频次 |
|---|---|---|---|---|---|
| Hero title 大 | 32 | 800 | -0.6px | 1.12 | landing hero |
| Hero title 中 | 28 | 800 | -0.2px | 1.15 | home name |
| Hero card title | 24 | 800 | -0.2px | 1.15 | reviewhero rh-title |
| Section card title | 22 | 700 | 0.36px | 1.3 | 04 nav h1 |
| Stat number | 22 | 800 | -0.3px | 1 | weekly stat |
| Mode tab title | 20 | 700 | 0.2px | 1.25 | 04 ans .v |
| Card title | 15 | 800 | 0.2px | 1.3 | sec .t |
| Body emphasis | 13.5 | 700 | 0.1px | 1.5 | 04 reason / step |
| Body | 13 | 700/500 | 0.1-0.3px | 1.45-1.55 | msg .t / chip / mode |
| Body small | 12 | 500/600/800 | 0.2-0.5px | 1.3-1.5 | sub / chip / blue 'm' link |
| Caption | 11 | 600/700 | 0.2-0.5px | 1.35 | msg .s / kp .lbl |
| Stat label | 10 | 700 | 0.6px | 1 | UPPERCASE micro |
| Tab bar | 10 | 500 | normal | normal | tabbar .tab |
| Hero kicker | 10 | 700 | 2px | normal | UPPERCASE kicker |
| Eyebrow | 10 | 700 | 1.2px | normal | UPPERCASE landing |
| Micro | 9 | 700/800 | 0.4-0.6px | 1 | wd .w / num |

**Letter-spacing 规则**：
- 大标题 → 负值（-0.2 / -0.3 / -0.6px）紧凑
- Body → 0 ~ 0.3px 微正
- UPPERCASE → 0.5 ~ 2px 大正

### §2.3 圆角系统

| px | 用途 |
|---|---|
| 999px / 980px | pill / chip / streak bar / signin button |
| 50% | avatar / blob / shutter / dots / progress ring |
| 54px | phone outer frame |
| 26px | landing scroll overlap |
| 24px | home scroll overlap |
| 22px | reviewhero card / hero card |
| 18px | weekly card / weekcard / msgs / kpcard |
| 16px | sample card / qcard / quick card / cta-try / btn |
| 14px | rh-btn / kpbtn / nav top sheet / msg ic 10 / chip 04 |
| 12px | wd cell / rh-sub-chip / how-step |
| 11px | qcard ic / feat ico |
| 10px | logo 14 / msg ic 13 / nav icon-btn 02 |
| 8px | mchip n base / week-strip dots / 'm' link bg |
| 6px | sample chip / qno / strk / paper |
| 4px | mini sep / spark height / wcrow gap |
| 3px | home indicator radius / mini badge |

### §2.4 间距系统（4pt 网格 · 实测频率）

```
2 / 3 / 4 / 6 / 8 / 10 / 12 / 14 / 16 / 18 / 20 / 22 / 24 / 26 / 28 / 32
```

**关键 padding 模板**：
- `phone padding`: 0（满屏布局）
- `card padding`: `12px 14px` 或 `14px 16px` 或 `12px 14px 14px`
- `hero card padding`: `18px 18px 16px` 或 `14px`
- `pill padding`: `4px 10px` (small) / `5px 10px` (medium) / `7px 14px` (large)
- `chip padding`: `5px 9px` / `8px 10px`
- `section margin`: `margin: 18px 4px 10px`
- `cta dock padding`: `14px 16px 18-22px`

### §2.5 阴影系统（**几乎无阴影**是关键特征）

```css
/* L0 · 微妙白卡阴影（最常用） */
--shadow-card-light:  0 1px 0 rgba(0,0,0,.03);                                   /* 几乎不可见 */
--shadow-card:        0 1px 2px rgba(0,0,0,.04);                                  /* 卡片基线 */
--shadow-card-deep:   0 1px 2px rgba(0,0,0,.04), 0 8px 22px rgba(40,50,90,.06);  /* hero card / sample */
--shadow-card-card:   0 1px 0 rgba(0,0,0,.03), 0 8px 22px rgba(40,50,90,.06);    /* 04_result hero */

/* L1 · Hero card 浮起 */
--shadow-hero-card:   0 10px 30px rgba(31,60,140,.25);                            /* 深蓝氛围投影 */

/* L2 · Phone 整机 */
--shadow-phone:       inset 0 0 0 6px #111, 0 24px 64px rgba(0,0,0,.22);          /* home/landing/result */
--shadow-phone-deep:  0 40px 100px -20px rgba(30,40,80,.35), 0 8px 24px rgba(20,30,60,.12), inset 0 0 0 6px #111;  /* 02/04 */

/* L3 · CTA 主按钮 */
--shadow-cta-blue:    0 10px 24px rgba(0,122,255,.28);                            /* primary blue */
--shadow-cta-deep:    0 12px 30px -6px rgba(31,60,140,.4), inset 0 1px 0 rgba(255,255,255,.25);  /* landing 深蓝 cta */
--shadow-cta-orange:  0 4px 10px rgba(255,149,0,.28);                             /* kpbtn pr */
--shadow-rh-btn:      0 6px 18px rgba(255,255,255,.2);                            /* rh-btn 白底发光 */

/* L4 · Shutter 三层环 */
--shadow-shutter:     0 0 0 4px rgba(255,255,255,.18), 0 0 0 8px rgba(255,255,255,.10), 0 14px 28px rgba(0,0,0,.35);

/* L5 · Glow（accent dot · 黄点脉冲） */
--shadow-glow-yellow: 0 0 8px #FFD166;
--shadow-glow-pulse:  0 0 0 6px rgba(255,204,0,.18);

/* L6 · Avatar 双环 */
--shadow-avatar:      0 0 0 2px rgba(255,255,255,.35), inset 0 0 0 2px rgba(0,0,0,.08);

/* L7 · 步骤数字圆 */
--shadow-step-num:    0 4px 10px rgba(0,122,255,.3);

/* L8 · 玻璃态 chip 投影（仅 02 subjects on） */
--shadow-glass-active: 0 6px 18px rgba(0,0,0,.18);

/* L9 · Paper 纸面（02 viewfinder 内） */
--shadow-paper:       0 30px 70px rgba(0,0,0,.55), 0 4px 12px rgba(0,0,0,.4);
```

**禁忌**：
- ❌ `0 12px 32px rgba(...)` 之类的中等深阴影（我之前丑设计用过）
- ❌ `0 20px 40px` 以上的卡片深阴影
- ✅ 永远先用 `0 1px 2px rgba(0,0,0,.04)`，只有 hero card 才升到 `0 10px 30px`

### §2.6 渐变系统（archive 实测全集）

```css
/* Hero 主渐变（深蓝） */
--grad-hero-home:     linear-gradient(180deg, #1E3A8A 0%, #3B5BDB 45%, #5B8DEF 100%);              /* 01_home */
--grad-hero-landing:  linear-gradient(170deg, #0F1A3D 0%, #1F3C8C 40%, #5F5BDB 80%, #8B87F6 100%); /* 14_landing */
--grad-hero-card:     linear-gradient(135deg, #0F1A3D 0%, #1F3C8C 60%, #5F5BDB 100%);              /* reviewhero 01_home */

/* Hero card 装饰 blob（径向 · radial） */
--grad-blob-purple:   radial-gradient(circle, rgba(88,86,214,.55), transparent 70%);
--grad-blob-cyan:     radial-gradient(circle, rgba(88,214,255,.45), transparent 70%);
--grad-blob-pink:     radial-gradient(circle, rgba(255,45,85,.35), transparent 70%);
--grad-blob-coral:    radial-gradient(circle, rgba(255,107,107,.45), transparent 70%);
--grad-blob-mint:     radial-gradient(circle, rgba(79,209,217,.35), transparent 70%);
--grad-blob-gold:     radial-gradient(circle, rgba(255,209,102,.40), transparent 70%);

/* Em 强调字 */
--grad-em-coral:      linear-gradient(90deg, #FFD166 0%, #FF6B6B 100%);    /* hero title em / hero card title em */
--grad-em-amber:      linear-gradient(90deg, #FFD166, #FFB454);             /* home name em */

/* Avatar / Logo conic 彩虹 */
--grad-conic-44:      conic-gradient(from 210deg, #FFD166, #FF6B6B, #C581F7, #4C9BFF, #FFD166);
--grad-conic-34:      conic-gradient(from 210deg, #FF5A4F, #FFB454, #F6D84C, #3CD47B, #4C9BFF, #8B87F6, #FF5A4F);

/* Quick entry icon 方块（4 色 gradient · 核心组件） */
--grad-icon-red:      linear-gradient(135deg, #FF6B6B, #FF3B30);
--grad-icon-green:    linear-gradient(135deg, #6DE895, #34C759);
--grad-icon-blue:     linear-gradient(135deg, #5AA3FF, #007AFF);
--grad-icon-purple:   linear-gradient(135deg, #C581F7, #8B87F6);
--grad-icon-orange:   linear-gradient(135deg, #FF9500, #FF3B30);            /* kp icon */
--grad-icon-blue-deep:linear-gradient(180deg, #5FA8FF, #007AFF);            /* shutter core / step n / btn primary */

/* CTA 按钮 */
--grad-cta-primary:   linear-gradient(180deg, #5FA8FF, #007AFF);            /* 蓝主按钮 · result/exec */
--grad-cta-deep:      linear-gradient(135deg, #1F3C8C 0%, #5F5BDB 100%);    /* 深蓝主按钮 · landing */
--grad-cta-orange:    linear-gradient(180deg, #FF9500, #E08100);            /* 橙按钮 · kpbtn pr */
--grad-cta-celebrate-green: linear-gradient(175deg, #0F7F3E 0%, #1FAE5C 40%, #34C759 100%); /* 09 review_done */

/* 暖米橙 KP / Ebbing card */
--grad-kp:            linear-gradient(135deg, #FFF4E6 0%, #FFE0C2 100%);
--grad-ebbing:        linear-gradient(160deg, #EEF4FF 0%, #F7ECFF 100%);    /* 04_result indigo→purple soft */
--grad-soft-orange:   linear-gradient(135deg, rgba(255,149,0,.08), rgba(255,59,48,.06));  /* social proof */

/* 答案卡（04_result） */
--grad-ans-wrong:     linear-gradient(160deg, #FFE8E6 0%, #FFF 70%);
--grad-ans-right:     linear-gradient(160deg, #E4F7EA 0%, #FFF 70%);

/* Sparkline / Progress fill */
--grad-spark-green:   linear-gradient(180deg, rgba(52,199,89,.35), rgba(52,199,89,0));
--grad-progress-warm: linear-gradient(0deg, #FFD166 0%, #FF6B6B 100%);       /* SVG ring · rh-circle */

/* Sample card 学科主题底（landing） */
--grad-sample-math:   linear-gradient(140deg, #E3ECFF 0%, #C7DAFF 100%);
--grad-sample-phys:   linear-gradient(140deg, #FFEAE4 0%, #FFD0C6 100%);
--grad-sample-eng:    linear-gradient(140deg, #E4F7EA 0%, #BAE7C5 100%);

/* Body 整体渐变（mockup 展示底色 · 02/04） */
--grad-stage-bg:      radial-gradient(1200px 800px at 10% -10%, #E8EEFB 0%, transparent 60%),
                      radial-gradient(1000px 700px at 110% 10%, #F5E8FB 0%, transparent 55%),
                      linear-gradient(180deg, #EEF2F8 0%, #E6ECF5 100%);

/* Camera viewfinder 径向 */
--grad-camera-vf:     radial-gradient(800px 400px at 50% 30%, #1d2433 0%, #0B0F1A 65%);

/* Camera dock 渐变（暗底 → 黑） */
--grad-camera-dock:   linear-gradient(180deg, rgba(11,15,26,0) 0%, rgba(11,15,26,.85) 35%, #0B0F1A 100%);

/* CTA dock 渐变（米白底 fade） */
--grad-cta-dock:      linear-gradient(180deg, rgba(242,242,247,0), rgba(242,242,247,.92) 30%, #F2F2F7 100%);

/* 黄色扫描线 */
--grad-scan-yellow:   linear-gradient(90deg, transparent, rgba(255,204,0,.95), transparent);

/* Paper 纸面（02 viewfinder 内） */
--grad-paper:         linear-gradient(180deg, #fbf8f0, #f1ece0);
```

### §2.7 滤镜（filter / backdrop-filter）

```css
/* Blob blur */
--blur-blob-sm:       blur(18px);
--blur-blob-md:       blur(20px);
--blur-blob-lg:       blur(22px);
--blur-blob-xl:       blur(24px);
--blur-blob-2xl:      blur(28px);

/* 玻璃态 backdrop */
--bd-glass-sm:        blur(8px);                           /* small chips */
--bd-glass-md:        blur(10px);                          /* signin pill */
--bd-glass-lg:        blur(12px);                          /* metric chip */
--bd-glass-xl:        blur(20px);                          /* nav icon-btn / control / tip card */
--bd-glass-tabbar:    blur(22px) saturate(180%);           /* tabbar / 04 nav */

/* 注：所有用 backdrop-filter 的地方必须同时写 -webkit-backdrop-filter */
```

### §2.8 动效

```css
/* Easing */
--ease-standard:      cubic-bezier(0.25, 0.46, 0.45, 0.94);
--ease-decel:         cubic-bezier(0, 0, 0.2, 1);
--ease-bounce-out:    cubic-bezier(0.34, 1.56, 0.64, 1);   /* streak bump */

/* Duration */
--dur-fast:           150ms;
--dur-base:           250ms;
--dur-celebrate:      400ms;

/* Whitelist keyframes（仅这几个允许） */
@keyframes streak-bump {
  0%   { transform: scale(0.85); opacity: 0; }
  60%  { transform: scale(1.12); opacity: 1; }
  100% { transform: scale(1); opacity: 1; }
}
@keyframes pulse {
  0%   { transform: scale(0.8); opacity: 0.7; }
  100% { transform: scale(1.2); opacity: 0; }
}
@keyframes blink { 50% { opacity: 0; } }                   /* code cursor */
```

---

## §3 Mood 体系（5 类 · archive 真实归类）

> ❌ 之前 spec 说的 cool/warm/celebrate 三分法**已废**。下面是 archive 真相。

### Mood A · `hero+overlap` (深蓝 hero 240-380px + 米白 scroll overlap)
- **页面**：P-HOME / P-LANDING / P-GUEST-CAPTURE / P-WELCOMEBACK
- **结构**：hero 用深蓝渐变 + 3 blob，scroll 区从 hero 底部 50px overlap 开始（top:190px / 300-370px），scroll 容器 `border-top-left/right-radius: 24-26px` 圆角白底盖在 hero 上
- **状态条**：白色字（透明叠在 hero 上）

### Mood B · `pure-warm` (米白底 + 白卡 + iOS 标准 nav)
- **页面**：P04 result / P05 wrongbook_list / P06 detail / P07 review_today / P10 calendar / P11 event_detail / P12 notifications / P13 settings
- **结构**：phone 底色 `#F2F2F7` 米白，顶部 nav 玻璃态白底（`rgba(242,242,247,.78) + blur 22px saturate 180%`），白卡为主体内容
- **状态条**：黑色字 `#111`

### Mood C · `dark-camera` (全屏深蓝黑 + 黄检测元素)
- **页面**：P02 capture / P15 guest_capture
- **结构**：phone 底色 `#0B0F1A`，viewfinder 是径向暗底 `radial-gradient(800px 400px at 50% 30%, #1d2433 0%, #0B0F1A 65%)`，内部模拟一张倾斜纸面（米色衬线字 + 学生红笔涂改），黄色 4 角 brackets + 黄色扫描线 + 黄点 pulse badge
- **状态条**：白色字
- **shutter 核心**：`linear-gradient(180deg, #5FA8FF, #007AFF)` 蓝色

### Mood D · `celebrate-green` (绿色极光庆祝)
- **页面**：P09 review_done
- **Hero**：`linear-gradient(175deg, #0F7F3E 0%, #1FAE5C 40%, #34C759 100%)`
- **装饰**：极光绿 blob `#6DFFA1` + confetti 粒子 + pulse 脉冲环
- **状态条**：白色字

### Mood E · `teal-observer` (青绿主题 · 观察者/分享)
- **页面**：P11 event_detail / P16 shared / P18 observer
- **Hero**：teal gradient `#0F1A3D → #4FD1D9` 或类似深蓝 + 青底
- **特征**：cyan accent dominant + identity card

---

## §4 组件库（每个组件附 archive 出处 + 关键 token）

> 以下组件从 archive 实测，可直接 grep 类名验证

### §4.1 `.phone` 整机壳
- **size**：393×852 (iPhone 15 Pro)
- **radius**：54px (warm) / 55px (camera/result)
- **shadow**：`inset 0 0 0 6px #111, 0 24px 64px rgba(0,0,0,.22)` 标准 / 02-04 用更深 `0 40px 100px -20px rgba(30,40,80,.35)`
- **notch**：`width:126px (54 → 125)px; height:37px; top:11px; bg:#000; radius:20px`
- **home-indicator**：`width:134px; height:5px; bottom:8px; bg:#000; opacity:.85`（warm 模式）/ `bg:#fff; opacity:.7`（dark 模式）

### §4.2 `.statusbar`
- **height**：54px
- **padding**：`0 32px` 或 `0 28px 8px`（02/04）
- **font**：`17px / weight:600` 或 `15px / weight:600`
- **margin-top**：12px（time/icons 顶到刘海下）
- **color**：`#fff`（mood A/C/D） / `#111`（mood B）

### §4.3 `.hero`（Mood A 专用）
- **height**：240px (home) / 380px (landing) / 460px (welcomeback)
- **background**：深蓝渐变（见 §2.6 grad-hero-*）
- **3 层 blob**：`::before` 紫右上 / `::after` 青左下 / `.blob` 粉中间偏左 — 全部 blur 18-28px
- **z-index**：10（hero） / 20（hsafe 文字） / 25（scroll 内容）

### §4.4 `.hsafe` Hero 内文本块
- **position**：`top:58-118px; left/right: 18-20px; z:20; color:#fff;`
- **结构**：
  - `.hello` kicker 13px / weight:500 / ls:0.3px / `rgba(255,255,255,.78)` 副标题
  - `.name` 28px / weight:800 / ls:-0.2px / `#fff` 主标题，`em` 用 gold→amber 渐变
  - `.streakbar` margin-top:14px，玻璃态 pill：`rgba(255,255,255,.16) + blur 8px + border 1px rgba(255,255,255,.24)`，flame svg 12px gold

### §4.5 `.avatar` 头像（44×44 conic 彩虹圆）
- **bg**：`conic-gradient(from 210deg, #FFD166, #FF6B6B, #C581F7, #4C9BFF, #FFD166)`
- **shadow**：`0 0 0 2px rgba(255,255,255,.35), inset 0 0 0 2px rgba(0,0,0,.08)` 双环
- **font**：`weight:800; size:15px; ls:0.5px; color:#fff; text-shadow:0 1px 2px rgba(0,0,0,.3)`
- **`::after`**：`content:"A"`（首字母）

### §4.6 `.brand .logo` Logo 方块（34×34 conic 彩虹方）
- **bg**：`conic-gradient(from 210deg, #FF5A4F,#FFB454,#F6D84C,#3CD47B,#4C9BFF,#8B87F6,#FF5A4F)`
- **radius**：10px
- **shadow**：`inset 0 0 0 1.5px rgba(255,255,255,.25), 0 4px 12px rgba(0,0,0,.25)`
- **`::after`**：`content:"AI"; weight:800; size:11px; ls:0.6px`

### §4.7 `.scroll` 内容容器（Mood A overlap）
- **position**：`top: hero高度 - 50px; bottom: 84px (留 tabbar)`
- **border-radius**：`top-left/right: 24-26px`
- **`::before`**：28px 高白底矩形 cover hero 底部 28px，制造圆角融合

### §4.8 `.reviewhero` Hero 卡（关键组件）
- **bg**：`grad-hero-card`
- **radius**：22px / **padding**：`18px 18px 16px`
- **shadow**：`0 10px 30px rgba(31,60,140,.25)`
- **3 层装饰**：`::before` coral 200×200 右上 / `::after` mint 180×180 左下 / 内部不需要 .blob
- **结构**：左上 kicker (10px ls:2px UPPERCASE) + title (24px weight:800, em #FFD166) + sub (12px) / 右上 progress ring 72px (gold→coral SVG gradient stroke 6px) / 中部 `.rh-split` 3 个玻璃态 chips / 底部 `.rh-cta` 白底主按钮 + 44px 玻璃方按钮

### §4.9 `.weekly` 周回顾卡 + sparkline
- **bg**：`#FFF` / **radius**：18px / **padding**：14px 16px / **shadow**：`shadow-card`
- **stats 行**：4 列 + 3 个 `width:1px height:32px var(--sep)` 分隔
- **stat .n**：22px weight:800 ls:-0.3px，按 g/b/o 用 `--green/--blue/--orange`
- **stat .l**：10px weight:700 ls:0.6px UPPERCASE `--ter`
- **sparkline**：SVG 1.8px stroke `#34C759` + `rgba(52,199,89,.35→0)` fill gradient + 3.5px circle 末尾点

### §4.10 `.weekcard / .wcrow` 七日条带
- **wcrow**：grid `repeat(7, 1fr)` gap 4px
- **wd**：72px min-height / radius:12px / bg `rgba(120,120,128,.06)` / `padding: 8px 0 6px`
- **wd.today**：`linear-gradient(180deg, #007AFF, #0062E1)` + 白字
- **dots**：5×5 px circle，`gap:2px max-width:32px flex-wrap`，颜色 r/o/g/i/p 对应 5 状态色
- **num** 红 badge：min-width:16 height:16 radius:8 bg:`#FF3B30` 白字 9px，`border:1.5px solid var(--card)` 白圈外描

### §4.11 `.msgs / .msg` 消息列表
- **msgs**：bg `#FFF` radius:18px padding:`4px 14px` shadow-card
- **msg**：`padding:12px 0` `border-bottom: .5px solid var(--sep)`
- **msg .ic**：34×34px radius:10px，bg `rgba(subject-color,.14)`，svg 16px subject color stroke
- **msg .tx .t**：13px weight:700 lh:1.3
- **msg .tx .s**：11px `--sec` lh:1.35 ellipsis
- **msg .tm**：10px ls:0.3px `--ter`

### §4.12 `.kpcard` 鼓励卡（薄弱知识点）
- **bg**：`grad-kp` 暖米橙
- **border**：`1px solid var(--kp-border)`
- **radius**：18px / padding `14px 16px`
- **`::after`** 装饰：120×120 radius:50% top:-60 right:-40 `radial pink alpha .18`
- **kp-head**：28×28 radius:9 `grad-icon-orange` icon + 13px weight:800 棕标题
- **kp-body**：12px lh:1.5 #A0522D，`<strong>` #6B2C0F
- **kp-actions**：`.kpbtn.pr` 主按钮 grad-cta-orange + shadow-cta-orange / `.kpbtn.sc` 副按钮白半透 + 棕字

### §4.13 `.quick` 快捷入口 2x2
- **grid**：`1fr 1fr` gap:10px margin-top:16px
- **qcard**：bg #FFF radius:16 padding:`12px 14px` shadow-card display:flex gap:10px
- **qcard .ic** 36×36 radius:11px 白 svg 18px，4 色：
  - red: `grad-icon-red`
  - grn: `grad-icon-green`
  - blu: `grad-icon-blue`
  - pur: `grad-icon-purple`
- **qcard .tx**：`.t` 13px weight:700 / `.s` 10px weight:600 ls:0.2px
- **qcard .arr** chevron right `--ter`

### §4.14 `.tabbar`
- **bg**：`rgba(242,242,247,.86)` (warm) / `rgba(255,255,255,.78)` (result) / `rgba(11,15,26,.78)` (camera)
- **backdrop**：`blur(22px) saturate(180%)`
- **border-top**：`.5px solid var(--sep)`
- **height**：84px / padding `8px 0 24px` 或 padding-top:6px
- **tab**：flex 列 gap:2-3px / svg 24×24 / size:10px weight:500 / color `--ter` / active `--blue`
- **badge**：`top:-2px right:28px min-width:16 height:16 radius:8 bg:#FF3B30 weight:700`

### §4.15 `.signin` 登录 pill（landing）
- **padding**：`7px 14px`
- **radius**：999px
- **bg**：`rgba(255,255,255,.16)`
- **backdrop**：blur(10px)
- **border**：`1px solid rgba(255,255,255,.3)`
- **font**：12px weight:700 ls:0.2px white

### §4.16 `.eyebrow` 强调标签
- **padding**：`5px 10px`
- **radius**：999px
- **bg**：`rgba(255,255,255,.12)`
- **backdrop**：blur(8px)
- **border**：`1px solid rgba(255,255,255,.2)`
- **font**：10px weight:700 ls:1.2px UPPERCASE white .92
- **dot**：`6×6 radius:50% bg:#FFD166 shadow:0 0 8px #FFD166`

### §4.17 `.metrics .mchip` 玻璃态指标 chip
- **padding**：`10px 8px`
- **radius**：14px
- **bg**：`rgba(255,255,255,.14)`
- **backdrop**：blur(12px)
- **border**：`1px solid rgba(255,255,255,.22)`
- **n**：17px weight:800 ls:-0.3px white
- **n em**：10px weight:600 white .7
- **l**：9.5px weight:600 white .72 ls:0.3px lh:1.3

### §4.18 `.cta-try / .cta-login` Landing 主副 CTA
- **cta-try**：height:52 radius:16 `grad-cta-deep` color #fff size:15 weight:800 + `shadow-cta-deep` + `::after` 白色斜光高亮
- **cta-login**：height:44 radius:14 bg `rgba(0,0,0,.04)` border `.5px solid rgba(60,60,67,.12)` color `--text` size:13 weight:700

### §4.19 `.shutter` 拍照按钮（02 camera）
- **size**：78×78 radius:50% bg:#fff
- **shadow**：`shadow-shutter`（三层环）
- **core**：60×60 radius:50% `grad-icon-blue-deep` border:3px solid #fff svg 24px
- **关键**：core 是**蓝色 gradient**，不是白底空圆

### §4.20 `.bracket / .scan / .detect` 相机检测元素
- **bracket**：46×46 border:3px solid `var(--yellow)` shadow `0 0 18px rgba(255,204,0,.45)` 4 角各一个，仅相邻两边 border + 对应圆角
- **scan**：`top:48% height:2px` `grad-scan-yellow` shadow `0 0 18px rgba(255,204,0,.7)`
- **detect badge**：`rgba(0,0,0,.55) + blur 18px` 玻璃态 pill + 7×7 黄点 + `shadow-glow-pulse`

### §4.21 `.subj` 学科 chip（02 camera 玻璃态）
- **size**：36px height / flex:1
- **radius**：10px
- **bg**：`rgba(255,255,255,.10)` / **backdrop**：blur(18px) / **border**：`.5px solid rgba(255,255,255,.18)`
- **font**：13px weight:600 ls:0.2px white
- **on**：bg `rgba(255,255,255,.95)` color `--blue` border:transparent + `shadow-glass-active`

### §4.22 `.tip` 玻璃提示卡（02 camera）
- **bg**：`rgba(255,255,255,.12)` / **backdrop**：blur(20px) / **border**：`.5px solid rgba(255,255,255,.15)`
- **radius**：14px / **padding**：`11px 14px`
- **icon**：18×18 svg color `--yellow`
- **font**：13px white .92 ls:0.1px

### §4.23 `.ans.wrong / .ans.right` 答案对错卡（04_result）
- **bg**：`grad-ans-wrong` / `grad-ans-right`
- **border**：`.5px solid rgba(red/green, .25)`
- **radius**：14px / padding `12px 12px 14px`
- **t**：11px weight:700 ls:0.6px UPPERCASE `--red/--green` + svg 5px gap
- **v**：20px weight:700 ls:0.2px
- **n**：12px `--ter`
- **deco**：右下 70×70 radius:50% `bg: --red/--green` opacity:.15

### §4.24 `.reason` 错因诊断（04_result）
- **bg**：白卡 / **border-left**：`4px solid var(--red)`
- **ix** 30×30 radius:8 `grad-icon-orange` 白 svg 16px
- **txt**：13.5px weight:500 lh:1.55
- **kw**：`--red` weight:700 bg `rgba(255,59,48,.10)` padding:`1px 5px` radius:4

### §4.25 `.steps .step` 解题步骤
- **step**：padding `10px 14px` `border-top: .5px solid var(--sep)`
- **n**：24×24 radius:50% `grad-icon-blue-deep` + `shadow-step-num`
- **exp**：13.5px lh:1.5
- **fm**：Times serif italic `--indigo` 14px bg `rgba(88,86,214,.08)` padding `4px 8px` radius:6 inline-block

### §4.26 `.ebbing` 艾宾浩斯卡（04_result）
- **bg**：`grad-ebbing` indigo→purple soft
- **border**：`.5px solid rgba(88,86,214,.18)`
- **radius**：16px / padding:14px
- **节点**：`.node .pill` 8px height radius:4 bg `#E5E5EA` 默认；`.node.first .pill` `linear-gradient(90deg, #5FA8FF, #007AFF) + shadow 0 0 0 4px rgba(0,122,255,.18)`

### §4.27 `.cta` 底部 CTA dock
- **position**：`bottom:84px (warm) / 0 (camera) / 84px (result)`
- **bg fade**：`grad-cta-dock`
- **结构**：1-2 个按钮 + cta-hint 副文案 11px ter

### §4.28 `.btn.primary / .ghost`
- **primary**：height:46 radius:14 `grad-cta-primary` 白字 size:15 weight:600 + `shadow-cta-blue`
- **ghost**：height:46 radius:14 bg `rgba(118,118,128,.12)` color `--text` weight:600

### §4.29 Sample 横滑卡（landing）
- **flex**：0 0 188px / radius:16 overflow:hidden / `shadow-card-deep`
- **thumb** 108px：bg 学科主题 `grad-sample-math/phys/eng`
- **thumb .chip** 9px UPPERCASE bg `rgba(subject-color,.15-.18)` color subject
- **thumb .formula**：Times serif 15px weight:600 absolute bottom
- **body**：padding `10px 12px 12px` / `.err` 10px UPPERCASE 红 / `.kp` 11.5px / `.t0` indigo 9px chip

### §4.30 `.feat` Feature 行（landing）
- **3 行**：每行 padding `12px 14px` border-bottom .5px sep
- **ico**：36×36 radius:10px
  - a: `linear-gradient(135deg, #4C9BFF, #5856D6)` 蓝→紫
  - b: `linear-gradient(135deg, #FF9500, #FF3B30)` 橙→红
  - c: `linear-gradient(135deg, #34C759, #30B0C7)` 绿→青
- **t**：13.5px weight:700 ls:0.1px
- **d**：11.5px `--sec` lh:1.45

### §4.31 `.social` 社会证明（landing）
- **bg**：`grad-soft-orange`
- **border**：`1px solid rgba(255,149,0,.18)`
- **radius**：14px padding `12px 14px`
- **stack**：4 个 22×22 圆头像，`margin-left:-6px` 重叠，第 1-3 个 conic 彩虹，第 4 个灰底 `+N`
- **txt em**：`--orange` weight:800

### §4.32 `.how-step` 步骤指引（landing）
- **3 列**：each padding `10px 8px` bg `var(--bg)` radius:12 text-align:center
- **n**：22×22 radius:50% bg #fff border `1.5px var(--blue)` color `--blue` weight:800 absolute top:-9px center

---

## §5 当前 spec/DESIGN.md 的偏差清单（必须同步修正）

> 以下文件与本文档冲突 → 以本文档为准 → 需要更新

### §5.1 `design/system/DESIGN.md`
- ❌ 用了 `--tkn-color-primary-DEFAULT: #0071e3` → ✅ 改 `#007AFF`
- ❌ 用了 `--tkn-color-warm-text-primary: #2C2A26` → ✅ 改用 iOS HIG `--text: #1C1C1E`，warm-text-primary 这个 token 直接废
- ❌ 整个 L2 warmth 体系（`--tkn-color-warm-bg/-elevated/-text-primary/-text-secondary`）→ ✅ 改用 iOS HIG `--bg/--card/--text/--sec/--ter`
- ❌ `--tkn-gradient-aurora` 蓝紫粉极光 → ✅ archive **没有 aurora**，hero 是深蓝渐变；如果一定保留极光名字，应映射到 `grad-hero-landing`
- ❌ "铁律 6 上半 celebrate + 下半 warm" → ✅ 改成 "Mood A hero+overlap 上半深蓝 hero + 下半米白 scroll"
- ❌ "warm/cool/celebrate 三分法" → ✅ 改成本文 §3 的 5 类 mood

### §5.2 `design/system/pages/P00.spec.md`
- ❌ §14 整个 L2 warmth token + gradient-aurora → ✅ 改用 iOS HIG + archive 风格（深蓝 hero + 3 blob + conic logo + 玻璃态登录卡）
- 注：P00 archive 没有 → **缺失页面**，见 §6

### §5.3 `design/system/pages/P02-capture.spec.md`
- ❌ "外层 warm + 内层 cool widget" 混合 mood → ✅ 改成 "Mood C dark-camera 全屏深蓝黑 + viewfinder 内模拟纸面 + 黄检测元素"
- ❌ 现有 spec 里的"米白外层 cool widget"理论全部废
- 直接对齐 archive `02_capture.html`

### §5.4 `design/system/pages/P-HOME.spec.md`
- ❌ §3 B1/B2 mood=celebrate + B3-B7 mood=warm（cool/warm/celebrate 三分法）→ ✅ 改成 "Mood A hero+overlap"，B1-B2 在 hero 内 / B3-B7 在 scroll overlap 内
- ❌ Token 引用整套 warm-* → ✅ 改用 iOS HIG `--bg/--card/--text/--sec/--ter`

### §5.5 `design/system/pages/P-LANDING.spec.md`
- 检查是否同样偏差

### §5.6 `design/system/pages/P-WELCOMEBACK.spec.md`
- 检查是否同样偏差

### §5.7 `design/system/pages/P13-settings.spec.md`
- 整个 warm-* token → 改 iOS HIG

### §5.8 `design/system/tokens/*.json`
- 所有 token JSON 文件需重生成对齐 §2

---

## §6 缺失页面 · P00 登录

archive **没有专门的 P00 login**。最接近的：
- `_archive/14_landing.html` 是访客落地（含右上 signin pill 链接）
- `mockups/wrongbook/17_welcomeback.html` 是回流唤起（已注册回登）

**P00 设计建议**（如需画）：
- **结构**：基于 `_archive/14_landing.html` 的 hero（深蓝渐变 380px + 3 blob）+ scroll 区放登录卡
- **登录卡**：白底 radius:18 padding:18，含 conic logo + 标题 "选择登录方式" + 微信主按钮（绿色 `#07C160` 例外）+ 其他方式链接 + 协议勾选
- **CTA 主按钮**：可选 `grad-cta-deep` 深蓝渐变 或 微信绿（铁律 1 例外注册 `wechat-brand`）
- **状态条**：白色字（在深蓝 hero 上）

---

## §7 工作流（下次 AI / 人接手怎么用本文档）

### 7.1 写 spec 前
1. 先读本文 §1-§4，建立设计语言直觉
2. 看自己要做的页面属于 §3 哪类 mood
3. 在 archive 找最接近的参考 mockup（§5 已列出对应关系）
4. spec 的 token 清单从 §2 复制，不要自己造

### 7.2 画 mockup 时
1. **不要重画 archive 已有的页面**，直接复制覆盖
2. 仅画 archive 缺失的（目前只有 P00）
3. CSS 第一行 `:root` 必须照搬 §2.1 的 iOS HIG 色变量名（`--blue` 而不是 `--tkn-color-primary-DEFAULT`）
4. Hero 深蓝渐变 + 3 blob 是必备，不能省
5. Conic logo + 4 色 gradient icon 是必备风格元素

### 7.3 lint 校验时
1. grep `#0071e3` → 命中 = fail
2. grep `#2C2A26` → 命中 = fail
3. grep `gradient-aurora` 在非 P-LANDING/P-HOME 的页面 → 命中 = fail
4. grep `warm-bg / warm-text-primary / warm-elevated` → 命中 = 需迁移到 iOS HIG token
5. Hero 区域必须有 `::before / ::after / .blob` 至少 3 层 radial blur

### 7.4 代码审查时
1. 阴影绝不超过 `0 10px 30px rgba(31,60,140,.25)`（hero card 上限）
2. 卡片阴影必须是 `0 1px 2px rgba(0,0,0,.04)` 或更轻
3. 文字色必须是 `#1C1C1E / #636366 / #8E8E93` 三档，不能用 `#2C2A26 / rgba(44,42,38,...)` 暖棕

---

## §8 archive 5 张"巅峰参考"（按视觉完成度）

| 排名 | 文件 | 适合参考什么 |
|---|---|---|
| 1 | `09_review_done.html` | celebrate mood + confetti + pulse + memory timeline |
| 2 | `01_home.html` | Mood A 全套：hero+overlap + reviewhero + weekly + weekstrip + msgs + kpcard + quick |
| 3 | `04_result.html` | 信息卡片设计：答案对错卡 + 错因诊断 + 步骤 + 知识点 + ebbing |
| 4 | `08_review_exec.html` | 交互完整度：题目卡 + 手写区 + reveal + 自评分 |
| 5 | `07_review_today.html` | 卡片组织：summary hero + 时间分组 + side bar + CTA float |

---

**文档版本**：v1.0
**反推来源**：`design/mockups/wrongbook/_archive/` 19 张 HTML mockup
**生成日期**：2026-05-02
**维护者**：写 spec / 画 mockup / 改 token 时同步本文档
