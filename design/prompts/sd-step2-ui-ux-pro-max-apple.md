# Sd · Step 2 执行 Prompt — ui-ux-pro-max × apple.design.md

> 用法：在 Claude Code（或具备 ui-ux-pro-max-skill 的任意 Claude 实例）中新开一个会话，**整段复制**以下 "===== PROMPT START =====" 到 "===== PROMPT END =====" 之间的内容，作为首条消息发送。
>
> 前置假设（请你手动确认）：
> - ✅ `ui-ux-pro-max-skill` 已通过 `/plugin marketplace add nextlevelbuilder/ui-ux-pro-max-skill` + `/plugin install ui-ux-pro-max` 装好
> - ✅ `design/system/inspiration/apple.design.md` 已从 `getdesign.md/apple/design-md` 保存到 Git
> - ✅ 仓库根目录 = `/Users/allenwang/build/longfeng`
> - ✅ 当前分支 = `feature/design-system`（从 §16 Sd.DoR `git checkout -b` 来）
> - ✅ 《落地实施计划 v1.4》§1.5 / §1.6 / §1.7 / §1.8 / §16 Sd 可被 Agent 按章节号 Read

---

===== PROMPT START =====

你是《AI 错题本》项目的 **Design Agent**（按落地实施计划 v1.4 §1.2 RACI）。当前任务是执行 Sd 阶段的 **Step 2：用 ui-ux-pro-max-skill 消费 apple.design.md，生成设计系统主档与 Token JSON 初稿**（对应 Sd.1 的前半 + Sd.2 的骨架）。

## 冷启动 5 步读入（§1.8 规则 D · 顺序不得颠倒）

按下列顺序 Read，其余一律不读：

1. `design/落地实施计划_v1.0_AI自动执行.md` 的 **§1.4 / §1.5 / §1.6 / §1.7 / §1.8 / §16（Phase Sd）**
2. `design/Sd设计阶段_决策备忘_v1.0.md` 全文（215 行）
3. `design/system/inspiration/apple.design.md`（视觉基因来源）
4. `design/AI上下文与连贯性设计_v1.0.md`（对 §1.8 的补充释义）
5. `state/phase-sd.yml`（若存在。若不存在，进入 "state-lite 模式"：手工按下文 Schema 创建一份）

读完后禁止回读无关章节。禁止"我记得上次说过 X" —— 一切跨 Phase 状态必须 grep Git。

## 任务契约（Task Contract）

**Task ID**：`sd-t02-tokens-from-apple-design-md`
**范围**：Sd.1 Token JSON 初稿 + Sd.2 组件清册骨架（不产出组件源码，仅产出 `components.md` + variant/state 清单）
**输出所在分支**：`feature/design-system`
**工具白名单**（本 Task 唯一允许调用）：

- `ui-ux-pro-max-skill`（通过 `/ui-ux-pro-max` 或描述触发）
- `Read` / `Write` / `Edit`（文件工具）
- `Bash` 仅限：`git` · `pnpm` · `node` · `mkdir` · `sha256sum` · `style-dictionary`
- **禁止**：Figma · Sketch · curl 远端、任何 `sudo`、任何"latest" 镜像标签

违反白名单即本 Task 立即 STOP + 写 `logs/phase-sd-<run-id>.md`。

## 输入（Inputs · 必须原样消费，不可改写）

| 路径 | 角色 |
|---|---|
| `design/system/inspiration/apple.design.md` | 视觉基因来源 —— 色板 / 字体 / voice / spacing 的第一参考 |
| `design/mockups/wrongbook/00..18.html` | 19 张已终稿 mockup，用于校验 token 是否足以支撑真实页面 |
| `design/业务与技术解决方案_AI错题本_基于日历系统.md` §2A.5（`tag_taxonomy`） | 学科/标签分类，驱动 **subject 色板破例** |
| `design/Sd设计阶段_决策备忘_v1.0.md` §3 | Sd.1 / Sd.2 的权威契约 |
| `design/落地实施计划_v1.0_AI自动执行.md` §16.2 | Sd 九产出 · Sd.1 / Sd.2 的格式硬要求 |

## 产出（Artifacts · 本 Task 结束时 Git 上必须多出的文件）

```
design-system/
  MASTER.md                              # ui-ux-pro-max 生成的设计系统主档
  apple-base.decision.md                 # 基于 Apple 的关键决策摘要（由你写，不由 skill 生成）

design/system/tokens/
  color.json                             # 核心色 + 语义色 + subject 色板（Apple + 破例 1）
  typography.json                        # 字体族 / 字号 / 字重 / 行高
  spacing.json                           # 4pt / 8pt 网格
  radius.json                            # 圆角等级
  shadow.json                            # 阴影等级
  motion.json                            # 动效曲线 + 时长
  README.md                              # 6 个 JSON 的"单一源"声明

design/system/components.md              # ~20 组件的清册骨架（variants / states / props / a11y role / H5 等价物 / 小程序等价物）

state/
  phase-sd.yml                           # 本 Task 的状态登记（或在已存在文件的 tasks[] 里新增 sd-t02-* 条目）
```

## 关键约束（Hard Constraints · 来自 v1.4 / Sd 备忘 / Apple 决策）

1. **Apple 风格作为基底**，但有**两处破例**必须固化到 token 层：
   - **破例 1 · subject 色板**：在 `color.json` 的 `--tkn-subject-*` 命名空间下单开四色（`math` / `physics` / `chemistry` / `english`），不套 Apple 灰蓝主色，采用高辨识度饱和色（各色与浅白背景对比度 ≥ 4.5:1）。理由：`tag_taxonomy` 要求 ≥ 4 色可辨。
   - **破例 2 · 小程序端饱和度微调**：token JSON 里新增 `_meta.platform_overrides.wechat.saturation_delta = +12%`（Style Dictionary 转译到 `tokens.wxss` 时应用）。理由：微信小程序生态视觉偏饱和，纯 Apple 冷白在小程序上显得"脏"。
2. **Token 命名规约**：全部使用 `--tkn-<group>-<name>-<variant>` 前缀（例：`--tkn-color-primary-500`、`--tkn-subject-math`、`--tkn-spacing-md`）。禁止泛型名（`--blue`、`--big`）。
3. **禁止 Tailwind arbitrary values**（§1.6 规则 B）：不生成 `w-[37px]` 类代码；所有数值走 token。
4. **A11y 前置**（Sd.7 G3）：`color.json` 每一个语义色（primary / success / warning / danger / info）必须附 `contrast_on_light` 与 `contrast_on_dark` 字段，值 ≥ 4.5:1（正文）/ ≥ 3:1（大字）；未达标即标 `_warning: "below-AA"` 不得悄悄放行。
5. **双端一致性**：`color.json` 不同平台差异仅在 `_meta.platform_overrides` 表达，禁止出现"H5 独有"或"小程序独有"的顶层字段。
6. **Code-as-Design**：不得引入 Figma、Sketch、XD；不得生成 `.fig` / `.sketch` 文件。
7. **Style Dictionary 4.x 兼容**：JSON 结构必须能被 `@tokens-studio/sd-transforms` 或原生 SD 4.x 直接消费（根是 group、叶子是 `{ "value": "#RRGGBB", "type": "color" }` 或等价结构）。
8. **必须在输出顶部注明来源**：`design-system/MASTER.md` 与 `design/system/tokens/README.md` 顶部注明 `source: getdesign.md/apple/design-md` + `saved_local: design/system/inspiration/apple.design.md` + `generated_by: ui-ux-pro-max-skill v2.5.0` + `human_overrides: [subject-palette, miniprogram-saturation]`。

## 执行步骤（Numbered Steps · 幂等）

1. **确认环境**：`pwd` 应在仓库根；`git branch --show-current` 应为 `feature/design-system`；`ls design/system/inspiration/apple.design.md` 必须存在。任一失败即 STOP 并在 `logs/phase-sd-<run-id>.md` 写明。
2. **初始化 state**（若 `state/phase-sd.yml` 不存在）：
   ```yaml
   phase_id: sd
   run_id: <本次执行时间戳>
   updated_at: <now>
   upstream_tags_verified: [s0-done]
   design_gate:
     biz_gate: exempt   # Sd 豁免 Design Gate（§1.5 C 级豁免）
     arch_gate: exempt
   tasks:
     - id: sd-t02-tokens-from-apple-design-md
       status: in_progress
       started_at: <now>
       attempt: 1
       next_step: step-03-invoke-skill
   ```
3. **调用 ui-ux-pro-max-skill**：触发一次技能调用，显式传入：
   - `--context design/system/inspiration/apple.design.md`
   - `--stack html+tailwind`（H5 侧优先 · 小程序侧走 Style Dictionary 后转译）
   - `--output design-system/MASTER.md`
   - 在对话中强制要求 skill 产出"**67 UI 风格里选 Minimalism & Swiss Style 作为 base**，引用 apple.design.md 的 palette / typography 做特化"
4. **审阅 skill 产出**：Read 一遍 `design-system/MASTER.md`。凡出现以下之一 = 回到 Step 3 复跑：
   - 生成了 emoji 作为 icon（违反 skill 内置守则）
   - 任何颜色给出了 hex 但未给对比度
   - 给了 Tailwind arbitrary values
   - 没有输出到 `design-system/MASTER.md` 这个路径
5. **手写 6 个 token JSON**：基于 MASTER.md 的 palette / type / spacing，按 Style Dictionary 4.x 结构写 `design/system/tokens/{color,typography,spacing,radius,shadow,motion}.json`。**color.json 里必须含 `--tkn-subject-{math,physics,chemistry,english}` 四色**。**motion.json 必须含 `ease-apple-standard` 与 `duration-150/250/400`**（Apple HIG 基线）。
6. **写 `color.json` 的 `_meta.platform_overrides`**：明示 `wechat.saturation_delta: 12` 与 `wechat.shadow_alpha_delta: 0.04`（Apple 冷白阴影在小程序上容易消失，微调透明度）。
7. **手工计算并填写对比度**：用 WCAG 公式（或调用 `node -e` 脚本）算出每个语义色的 `contrast_on_light` / `contrast_on_dark`，达不到 4.5:1 的改值、不得打标。
8. **写 `design/system/components.md`**：按 Sd.2 要求列 ~20 组件骨架（Button / Input / Card / Toast / Modal / Sheet / TabBar / NavBar / Skeleton / Empty / Badge / Avatar / Divider / Banner / Tag / Picker / DatePicker / Stepper / Switch / Progress），每个含 `variants / states / props / a11y_role / h5_equivalent / miniprogram_equivalent` 六个字段。**不**生成任何 `.tsx` / `.vue` 源码。
9. **写 `design-system/apple-base.decision.md`**：记录"选 Apple 的理由 + 两处破例 + 风险点 + 后续 Review 观察项"，≤ 300 字。
10. **写 `design/system/tokens/README.md`**：声明 6 个 JSON 是"唯一源"，Style Dictionary 4.x 转译目标 `tokens.css` / `tokens.wxss` / `tokens.ts` 由 Sd Step 3（后续 Task）执行，本 Task **不**跑 `style-dictionary build`。
11. **自校验**：
    - `jq . design/system/tokens/*.json` 全部能通过（JSON 合法）
    - `grep -r "subject-math\|subject-physics\|subject-chemistry\|subject-english" design/system/tokens/color.json` 四行齐
    - `grep "_meta.platform_overrides" design/system/tokens/color.json` 命中
    - `design/system/components.md` 组件数 ≥ 20
    - `design-system/MASTER.md` 不含 `w-\[` 模式
    - `design-system/MASTER.md` 不含中文硬编码（文案走 Sd.8，不在本 Task）
12. **更新 state**：把 `state/phase-sd.yml` 的 `sd-t02-*` 条目改为 `status: done`，填 `outputs` 清单与 `outputs_hash`（`sha256sum` 每个产出文件）。
13. **commit**：一个 commit 搞定，message：`feat(sd): Sd.1 tokens draft + Sd.2 components skeleton from apple.design.md via ui-ux-pro-max v2.5.0`。**不**打 `sd-done` tag（Sd 整体完成后才打，不是本 Task）。

## DoD（Definition of Done · 本 Task 完成判定）

- [ ] 13 个产出文件全部落地 Git（见"产出"清单）
- [ ] 11 条自校验命令全部 exit 0
- [ ] `state/phase-sd.yml` 本 Task 条目 `status=done`
- [ ] commit 已推到 `feature/design-system` 分支
- [ ] 未打 tag（本 Task 不是 Phase 终点）

## 失败回滚（Local Rollback）

```bash
git reset --hard HEAD                          # 放弃所有改动
rm -rf design-system design/system/tokens design/system/components.md
# state/phase-sd.yml 改回 status=pending · attempt+1
```

## Reset 协议（§1.8 规则 E）

任一命中即停机 + 调用 `handoff.sh`（若存在），否则手写 `state/scratch_summary_sd_sd-t02-tokens-from-apple-design-md.md`：

- tool_call 次数 > 80
- token 使用 > 60% 上下文窗口
- 同一 step 重试 ≥ 3 次仍失败（例：skill 反复不认 apple.design.md）

handoff 文件写"下一步从 Step N 继续 + 关键临时发现（比如 skill 不吃某 MD 格式，必须把 apple.design.md 先转成 markdown table 再喂）"。

## 禁止事项（一击 STOP）

- ❌ 修改 `apple.design.md`（这是只读视觉基因）
- ❌ 修改方案 / Sd 备忘 / 落地计划（跨章边界，走 ADR）
- ❌ 跑 `style-dictionary build`（那是下一个 Task 的活）
- ❌ 生成任何 `.tsx` / `.vue` / `.wxml` 组件源码（Sd.2 骨架只要 MD）
- ❌ 打 `sd-done` tag
- ❌ 凭记忆说"Apple 主色是 #007AFF" —— 必须从 `apple.design.md` grep 出来才算数

现在按上述步骤开工。第一条动作是执行"冷启动 5 步读入"。

===== PROMPT END =====

---

## 使用小贴士

1. **冷启动读完后 Agent 会先给你一个"我打算这么做"的回执**（§1.4 第 12 条要求冷启动完整性）。看一眼，没问题就放它走。
2. **skill 调用那一步（Step 3）**最容易漂移。如果 skill 生成的 MASTER.md 把 Apple 写成了"Glassmorphism + Apple"混合体，打断它、让它只保留"Minimalism & Swiss Style + Apple 特化"。
3. **Step 7 的对比度计算**可以让 Agent 直接写个一次性的 `node -e` 脚本算 —— 不需要安装额外包，用相对亮度公式即可。
4. **若 Agent 在 Step 5 乱填 subject 色板**（比如四个都用蓝色系），让它去翻 Material Design 的 "category color" 推荐值，或直接指定 `#FF6B6B / #4ECDC4 / #FFE66D / #95E1D3` 四色起步。
5. **Task 结束时不打 tag** —— `sd-done` 是整个 Sd（全部 9 产出都走完 Review Gate 后）的事，本 Task 只是其中一小步。

## 运行后你需要人工 Review 的两点

- `design-system/apple-base.decision.md` 三百字的决策摘要 —— 这是 v1.4 §1.8 L1 层的 "Phase 契约"，后续所有 Sd Task 都要读这份
- `design/system/tokens/color.json` 的 subject 四色饱和度与辨识度 —— 学科色板是破例，需要你肉眼过一遍

## 跑完之后的下一步（Step 3 预告）

本 Task 产出 token JSON；下一个 Task 应该是 "Sd.1 后半：Style Dictionary 4.x 转译 + 双端 CSS 文件生成"，把 `design/system/tokens/*.json` 转成 `frontend/packages/ui-kit/src/tokens.css` / `tokens.wxss` / `tokens.ts`。那个 Task 的 prompt 等本 Task 通过你 Review 之后再写，**不要提前开**（避免跨 Task 上下文污染，§1.8 规则 F）。
