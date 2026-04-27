# Sd.5 Handoff · Fonts

> 主文档 §16.2 Sd.5 · 字体资产

## 字体策略：System Stack（不打包外部字体）

S7 H5 + miniapp + Sd.3 mockup 全部用 system font · **无需 ship 字体文件**。

```css
font-family:
  -apple-system,
  BlinkMacSystemFont,
  "SF Pro Text",
  "SF Pro Display",
  "PingFang SC",
  "Helvetica Neue",
  Helvetica,
  Arial,
  sans-serif;
```

## 为什么不打包

| 选项 | 利 | 弊 |
|---|---|---|
| 打包 SF Pro / Inter | 视觉统一跨平台 | +200KB · iOS 已有不必再下 |
| **System Stack** | 0 字节 · iOS HIG 原生 · Apple 推荐 | Android / 桌面 fallback 略不同（可接受）|

S7 选 System Stack（与 Sd.3 mockup 一致 · 与 ui-kit `--tkn-font-display` / `--tkn-font-text` token 一致）。

## tokens 字体引用

详见 `frontend/packages/ui-kit/src/tokens.css`：
- `--tkn-font-display`: SF Pro Display fallback stack（≥ 20px）
- `--tkn-font-text`: SF Pro Text fallback stack（< 20px）

## 微调

- 中文字符落到 PingFang SC · 无需额外 webfont
- 英文落到 SF Pro · iOS 原生
- Android 落到 Roboto / Helvetica fallback
- 桌面 Chrome / Edge 落到 BlinkMacSystemFont 或系统等价

## 待 User 决策

如需引入品牌字（如错题本专属手写体 · 类似 Caveat for "学生批改笔迹"）：
- 字体文件放本目录 woff2 格式
- 在 tokens.css 加 @font-face
- 在 ui-kit 注入

当前 Sd.3 mockup 仅在 paper 缩略图用 Georgia serif（system 自带）· 不需额外字体。
