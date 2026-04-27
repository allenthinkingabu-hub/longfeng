# Sd.5 Handoff · OG Image

> 主文档 §16.2 Sd.5 · 社交分享图

## 状态：占位 · 待 User 设计

OG image 用于：
- 微信 / Twitter / Facebook 分享卡（Open Graph protocol）
- 标准尺寸 1200×630 · 比例 1.91:1
- 内容建议：app logo + tagline「AI 错题本 · 让每道错题都被记住」

## 接入

H5 在 `frontend/apps/h5/index.html` 加 meta：

```html
<meta property="og:title" content="龙凤错题本" />
<meta property="og:description" content="AI 帮你记住每道错题" />
<meta property="og:image" content="https://wrongbook.longfeng.com/og.png" />
<meta property="og:type" content="website" />
```

## 不阻塞 S7

S7 H5 上线前可用 placeholder 文字 og（CSS 渲染降级）· OG image 上线 ≤ S10 可观测性 Phase 前提供即可。

## 待 User 提供

- `og-default.png` 1200×630 · 主品牌色 · logo 居左 · slogan 居右
- `og-share-wrongitem.png` 1200×630 · 错题分享专用模板（含问题缩略 + 学科徽标）

设计师交付后归本目录 · sd-done 二期合并。
