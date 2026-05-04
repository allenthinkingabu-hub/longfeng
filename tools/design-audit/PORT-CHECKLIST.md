# 10 步迁移 Checklist · port Design-Audit System 到新项目

> **目标**: 在新项目里装上 ABCDEFGH 8 机制 4 重防护链 · 30-60 min 完成。
>
> **前提**: React + vite + playwright + Claude Code (其他 stack 见 README §跨技术栈适配)。

---

## ☐ Step 1 · 拷 4 类 source 文件

```bash
TARGET=/path/to/your-project
SRC=/path/to/longfeng-wrongbook/tools/design-audit  # 或 fork 后的 bundle 路径

mkdir -p $TARGET/.claude/agents
mkdir -p $TARGET/.github/workflows

cp $SRC/agents/*.md         $TARGET/.claude/agents/
cp $SRC/e2e-templates/*.ts  $TARGET/e2e/specs/
cp $SRC/scripts/*           $TARGET/scripts/
cp $SRC/workflows/*.yml     $TARGET/.github/workflows/
```

**Verify**: `ls $TARGET/.claude/agents/` 看到 3 个 .md (design-reviewer / design-audit-orchestrator / page-fixer)

---

## ☐ Step 2 · 安装 NPM 依赖

```bash
cd $TARGET/e2e
pnpm add -D @playwright/test pixelmatch pngjs tsx
```

**Verify**: `pnpm exec playwright --version` + `node_modules/pixelmatch` 存在

---

## ☐ Step 3 · 改 PAGE_MAP (3 处)

每个 file 内有 `PAGE_MAP` 或 `PAGES` 数组 · 改成你项目的 page 命名:

### 3a. `e2e/specs/mockup-vs-impl.spec.ts` (PAGES 数组)

```typescript
const PAGES: PageDef[] = [
  // 你项目的 page 列表 · 每条 4 字段
  { id: 'P-LANDING',     mockup: '_archive/14_landing.html',  route: '/welcome',   pageRootSelector: '[data-testid="landing-page"]', tolerance: 0.05 },
  { id: 'P-HOME',        mockup: '_archive/01_home.html',     route: '/',          pageRootSelector: '[data-testid="home-page"]',    tolerance: 0.05 },
  // ... 你的所有 page
];
```

### 3b. `scripts/design-precommit.sh` (PAGE_MAP bash 关联数组 · ~line 30)

```bash
declare -A PAGE_MAP=(
  ["Landing"]="P-LANDING"      # ← Landing 是 frontend/apps/h5/src/pages/Landing/ 的 dir 名
  ["Home"]="P-HOME"
  # ... 你的所有 dir → page_id 映射
)
```

### 3c. `scripts/pr-screenshots.ts` (PAGE_MAP 对象 · ~line 25)

```typescript
const PAGE_MAP: Record<string, { id: string; mockup: string; route: string }> = {
  Landing: { id: 'P-LANDING', mockup: '_archive/14_landing.html', route: '/welcome' },
  // ... 你的所有 page
};
```

**Verify**: 3 处 PAGE_MAP 一致 · 每个 page 在 3 个文件都有对应 entry

---

## ☐ Step 4 · 改 npm scripts (e2e/package.json)

```json
{
  "scripts": {
    "e2e:vrt-multi": "E2E_TRACK=vrt playwright test --grep @vrt-multi --update-snapshots=missing",
    "e2e:vrt-multi-check": "E2E_TRACK=vrt playwright test --grep @vrt-multi",
    "e2e:mockup-diff": "E2E_TRACK=design playwright test --grep @mockup-diff --reporter=list",
    "e2e:design-audit": "E2E_TRACK=design playwright test --grep '@vrt-multi|@mockup-diff' --reporter=list"
  }
}
```

**Verify**: `pnpm e2e:mockup-diff --help` 不报错 (即使没 vite · 也应该 list 加载 spec)

---

## ☐ Step 5 · 改 vite proxy / 路径常量 (e2e-templates 配置)

`e2e/specs/mockup-vs-impl.spec.ts` 顶部:

```typescript
const MOCKUP_DIR = path.join(REPO_ROOT, 'design/mockups/wrongbook');  // ← 改成你项目 mockup 路径
const BASE_URL = process.env.BASE_URL ?? 'http://localhost:5173';     // ← 改成你 vite 端口
```

`e2e/specs/vrt-multi-viewport.spec.ts` VRT_PAGES 数组 · 改你的 page route。

**Verify**: 路径 / 端口跟你项目实际一致

---

## ☐ Step 6 · CLAUDE.md append H 机制 §2.0 + §2.11

```bash
cat $SRC/snippets/CLAUDE-SNIPPET.md >> $TARGET/CLAUDE.md
```

或手动 copy `snippets/CLAUDE-SNIPPET.md` 内容 append 到目标项目 CLAUDE.md 末尾。

**Verify**: `grep "§2.0 边界识别" $TARGET/CLAUDE.md` 找到

---

## ☐ Step 7 · spec.md template append §15 实现边界段

如果你项目有 `design/system/pages/_template.spec.md` (单页规格卡模板):

```bash
cat $SRC/snippets/SPEC-§15-SNIPPET.md >> $TARGET/design/system/pages/_template.spec.md
```

并 append 到所有现有 spec (一次性 migrate):

```bash
cat $SRC/snippets/SPEC-§15-SNIPPET.md | tee -a $TARGET/design/system/pages/*.spec.md > /dev/null
```

**Verify**: `grep "§15 实现边界" $TARGET/design/system/pages/*.spec.md | wc -l` 等于 spec 数

---

## ☐ Step 8 · 给所有 mockup HTML 加 chrome attr (F 机制)

参照 `snippets/MOCKUP-EXAMPLE.html` 的标记法:

```html
<div class="phone" data-mockup-chrome="iphone-frame">
  <div class="statusbar" data-mockup-chrome="iphone-statusbar">...</div>
  <div class="content"><!-- 你的真实页面内容 · 不加 attr --></div>
  <div class="homebar" data-mockup-chrome="iphone-homebar"></div>
</div>
```

**3 种 chrome 类型**:
- `iphone-frame` · phone wrapper (含 border / radius / box-shadow inset 装饰)
- `iphone-statusbar` · 9:41 + 信号 + 电池
- `iphone-homebar` · 底部 home indicator

如果有 19 张 mockup · 派 sub-agent 批改 · 参考 longfeng-wrongbook 这次的做法 (见 commit `3add048` 完整描述)。

**Verify**: `grep -c data-mockup-chrome design/mockups/**/*.html` 看每个 mockup 至少 1 个 attr

---

## ☐ Step 9 · 安装 pre-commit hook (E 机制)

```bash
cd $TARGET
chmod +x scripts/design-precommit.sh scripts/install-design-hooks.sh
bash scripts/install-design-hooks.sh
```

**Verify**: `cat .git/hooks/pre-commit | grep design-precommit` 找到

---

## ☐ Step 10 · 生成 baseline + 验证全套

```bash
# 启 vite
cd frontend/apps/h5 && pnpm dev &

# 等 vite ready (curl :5173)

# 1. A 机制: 生成 N 页 × 4 viewport baseline
cd $TARGET/e2e && pnpm e2e:vrt-multi --update-snapshots

# 2. B 机制: 跑 mockup-vs-impl 看 diff%
pnpm e2e:mockup-diff

# 3. C 机制: 派 design-reviewer agent 验证 (Claude Code 内)
#    Agent({subagent_type: "design-reviewer", prompt: "Review P-LANDING"})

# 4. D 机制: 提一个 demo PR 看 GH workflow 自动 comment 三联截图

# 5. G 机制: 给 demo PR 不打 designer-approved label · 看 merge gate 阻断
```

**Verify**: 全部 5 个验证通过 · 系统可用

---

## 排错

### `pnpm e2e:mockup-diff` 报 `Cannot find module pixelmatch`

→ Step 2 没装 deps · 跑 `pnpm add -D pixelmatch pngjs`

### 跑 vrt-multi · baseline 路径不对

→ playwright snapshot 命名跟 project name 关联 · 检查 `playwright.config.ts` 的 projects 数组 · 或第一次跑用 `--update-snapshots` 覆盖

### design-reviewer agent 不被识别

→ Claude Code session 启动时加载 .claude/agents/* · 装完后**重启 Claude Code session**

### pre-commit hook 没触发

→ 检查 `.git/hooks/pre-commit` 是否 executable: `chmod +x .git/hooks/pre-commit`

### CI workflow 不跑

→ workflow 必须 push 到默认 branch (main / master) 才生效 · merge 前先 push 到 main

---

## 完成后

回到 [README.md](./README.md) §4 重防护链 看你项目自动获得的能力。

如果遇到问题 · 参考 [longfeng-wrongbook 这次的实施记录](https://github.com/longfeng/wrongbook) commit 历史 (5e2a526 → 0d50c5c · 7 commit 全完整流程)。
