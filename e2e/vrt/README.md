# C 轨 · 像素回归（VRT · plan §3.2）

本目录存放 Playwright `toHaveScreenshot()` 自动管理的 baseline 快照。

## 配置

`playwright.config.ts` 中：
```ts
expect: { toHaveScreenshot: { maxDiffPixelRatio: 0.01, threshold: 0.2 } }
```

## 跑法

```bash
# 首次生成 baseline
pnpm e2e:vrt          # = playwright test --grep @vrt --update-snapshots=missing

# 严格 diff（baseline 已存在 · CI 用）
pnpm e2e:vrt-check    # = playwright test --grep @vrt
```

## 阈值

- happy path 页（19 archive 页）· **≤ 1%** diff
- skeleton 页（P-OBSERVER / P-WELCOMEBACK）· ≤ 3% diff
- 浏览器：仅 chromium 393×852 · scale=3

## Baseline 存储

按 spec 文件名分目录：`vrt/{sc-XX}/{platform}-{viewport}-{name}.png`
