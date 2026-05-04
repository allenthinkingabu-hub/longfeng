# 把 Design-Audit System 做成 GitHub Template Repo (#4)

> **目标**: 让别人在 GitHub 一键 `Use this template` 创新项目 · 自带 ABCDEFGH 8 机制全套 · 含 minimal demo H5 page 验证可跑通。

---

## 方案选择 · 2 种路径

### 路径 A · 最小 template (推荐 · 1-2h)

**用本 `tools/design-audit/` bundle + minimal scaffolding** · 不含真实业务代码 · 纯设计审计基建。

适合: 团队**已有项目** · 想加 design-audit 能力 (像 longfeng-wrongbook 这次的 port)。

### 路径 B · 完整 starter project (1-2d)

**完整 React + vite + playwright + 1 个 demo H5 page + 1 个 mockup + 1 个 spec** · 用户 fork 后 `pnpm install` 即可跑通验证。

适合: **新项目从零开始** · 直接 fork 当起点。

---

## 路径 A 实施步骤 (推荐先做)

### Step 1 · 创新 GitHub repo

```bash
# 在 GitHub.com 创建新 repo:
#   名称: design-audit-template
#   说明: 全自动 Design-Audit 多轮对抗系统 · 5 层 8 机制 · 解 "E2E PASS 但视觉对不齐"
#   Public · 勾选 "Template repository" (启用 fork as template)
#   不勾 README (本地准备 push)
```

### Step 2 · 本地初始化 + push

```bash
# 在你 dev machine
mkdir design-audit-template && cd design-audit-template
git init
git remote add origin git@github.com:<YOUR_ORG>/design-audit-template.git

# 拷贝 bundle 内容到根 (不带 tools/ 前缀 · 让 user fork 后直接 cp)
cp -r /Users/allenwang/build/longfeng-wrongbook/tools/design-audit/* .
mv methodology/FRAMEWORK.md ./FRAMEWORK.md  # 提到根方便阅读

# 加 GitHub template 必备文件
cat > .github/ISSUE_TEMPLATE/port-question.md <<'EOF'
---
name: Port question
about: 移植 design-audit 到自己项目时遇到问题
---

**目标项目 stack**: e.g. React+vite / Vue+vite / RN
**遇到的 step**: PORT-CHECKLIST 的 step X
**报错 / 卡点**: ...
EOF

# .gitignore (不带项目特定的)
cp /Users/allenwang/build/longfeng-wrongbook/.gitignore .gitignore

# 关键: README 改成 template-friendly
mv README.md PORT-FROM-LONGFENG-WRONGBOOK.md  # 原 README 变成 case study
cp _TEMPLATE-README.md README.md               # template-friendly readme (见下)

git add -A
git commit -m "feat: 全自动 Design-Audit 多轮对抗系统 · 5 层 8 机制 v1.0"
git push -u origin main
```

### Step 3 · 写 _TEMPLATE-README.md (代替默认 README)

放一份 template-friendly 的 README · 含:
- Quickstart (Use this template button → fork → 跑 PORT-CHECKLIST)
- 5 层架构图
- 适用场景 (✅/❌)
- 链接到 FRAMEWORK.md (原理)
- 链接到 PORT-CHECKLIST.md (操作)
- 链接到 PORT-FROM-LONGFENG-WRONGBOOK.md (真实案例)

模板见本目录 `_TEMPLATE-README.md` (我下面放一份)

### Step 4 · GitHub repo 设置

- **About** 描述: "全自动 Design-Audit 多轮对抗系统 · catch H5 实现跟设计稿不一致"
- **Topics**: `design-system` `design-audit` `playwright` `claude-code` `vrt` `pixel-diff` `ai-agent` `visual-regression`
- **Settings → Template repository**: ✅ 勾选

### Step 5 · 验证 template 可用

- 自己点 "Use this template" 创个 test repo
- 跟 PORT-CHECKLIST 走 10 步
- 全部通跑 = template 验证通过

---

## 路径 B 完整 starter (留 P1 · 1-2d)

如果要做完整可 fork 即跑的 demo:

### B.1 准备 minimal H5 demo

```bash
mkdir -p design-audit-template/{frontend/apps/h5,e2e,design/mockups,design/system/pages}

# 用 vite create
cd design-audit-template/frontend/apps/h5
pnpm create vite@latest . --template react-ts

# 加 1 个 page (HelloPage.tsx)
# 加 1 个 mockup (design/mockups/00_hello.html · 含 chrome attr)
# 加 1 个 spec (design/system/pages/P00.spec.md · 含 §15)
```

### B.2 配 e2e

```bash
cd ../../../e2e
pnpm init
pnpm add -D @playwright/test pixelmatch pngjs tsx
# 复 vrt-multi + mockup-vs-impl spec template (改 PAGES 为 [P00])
```

### B.3 加 demo workflow

`.github/workflows/demo-test.yml`: PR 时自动跑 vrt + mockup-diff (用作示例)

### B.4 README 含 demo gif / video

录一个 30s 屏幕录像 · 展示 fork 后 → 改 page → CI 自动 catch bug → 修复

---

## 推荐 · 路径 A 立刻做 · 路径 B 留有需求时再做

路径 A 在 1-2h 内做完 + push · 立刻能给同事用。
路径 B 含完整 demo 是社区 marketing 用 (truly open source) · 有团队真要才值得投入 1-2d。

---

## 长期愿景 · 演进路径

1. **v1 (本 commit)** · 项目内 portable bundle + 文档
2. **v1.5** · GitHub template repo (路径 A · 1-2h)
3. **v2** · GitHub template repo + 完整 demo (路径 B · 1-2d)
4. **v3** · NPM 包 `@design-audit/playwright` + `@design-audit/scripts` (拆细)
5. **v4** · Claude Code Plugin (装到 plugin marketplace · 一键 install)
6. **v5** · 跨平台支持 (RN / Flutter / Vue 适配 · 验证)

---

## 现在你可以做的 (无需我)

```bash
# 复 bundle 到新 dir 准备 push
cp -r /Users/allenwang/build/longfeng-wrongbook/tools/design-audit ~/design-audit-template
cd ~/design-audit-template

# 创 GitHub repo (web)
# git remote add + push
# 点 "Template repository" 设置

# 30 min 完成 v1.5 路径 A
```

或者 · 让我继续做 (我可以写更详细的 _TEMPLATE-README.md + 准备完整 push 命令)。
