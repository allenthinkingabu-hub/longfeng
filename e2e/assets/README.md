# e2e/assets · QA fixture 资产

本目录放 Playwright spec 用的样本图片、JSON、SVG 等。

## 必备文件（caveat：当前为占位 · DevOps 在 §S2/S5 准备真实样本）

- `sample-question.jpg` · 1 张样本错题照片（A4 纸 · math 题干清晰可识别）· 给 SC-01/SC-12 用
- `sample-question-blur.jpg` · 模糊版（用于 SC-07 低置信度）
- `sample-multi-question.jpg` · 多题（SC-01 multi mode 路径）

## 生成方式（占位）

A 轨 staging 上：从 staging seed 数据集复制 1 张已知 qid 的图（DevOps 维护 · 在 `infra/seed/` 下）。
B 轨 mock：MSW 拦截 file presign · 不真上传 · 任意 1KB JPG 占位即可。

> 当前 commit 不包含真图。CI 跑前需 DevOps 通过 LFS 或 S3 注入。
