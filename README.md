# Longfeng — AI 错题本

> AI 驱动的错题本应用 · 基于通用日历系统与艾宾浩斯复习曲线
> 当前阶段：**S0 · 仓库骨架** — 空骨架就绪；业务代码待后续 Phase 落地

## 文档入口

| 类别 | 路径 | 说明 |
|---|---|---|
| **落地计划** | [`design/落地实施计划_v1.0_AI自动执行.md`](./design/落地实施计划_v1.0_AI自动执行.md) | 本仓库的执行蓝本 · AI Agent 按 §4..§16 顺序推进 |
| 业务方案 | `design/业务与技术解决方案_AI错题本_基于日历系统.md` | v1.2 · SC-01..SC-15 |
| 视觉基线 | `design/mockups/wrongbook/00..18.html` | 19 张 HTML mockup |
| Sd 决策 | `design/Sd设计阶段_决策备忘_v1.0.md` | Code-as-Design 栈 |
| 设计系统 | `design-system/MASTER.md` | 并行 Sd Track 产物 |
| ADR | `docs/adr/` | 架构决策记录 0001..0010 |
| 工具白名单 | `docs/allowlist/` | `global.yml` · `global-deny.yml` · `s<N>.yml` |

## 顶层结构

```
longfeng-wrongbook/
├── backend/           # Multi-module Maven (Spring Boot 3.2.5 + Cloud 2023.0.1 + Alibaba 2023.0.1.0 + Spring AI 1.0.0-M1)
│   ├── pom.xml        # Parent BOM (§1.3.1)
│   ├── checkstyle.xml
│   ├── common/ gateway/ wrongbook-service/ ai-analysis-service/
│   ├── review-plan-service/ file-service/ anonymous-service/
├── frontend/          # pnpm workspace (apps + packages)
├── e2e/               # Playwright · S9 填充用例
├── helm/              # Helm Charts · 伞形 + 7 子 Chart
├── infra/             # ArgoCD ApplicationSet + Terraform 占位
├── ops/scripts/       # bootstrap / smoke / gate / check-allowlist / state-* / arch-consistency
├── design/            # 方案 / 计划 / mockup / arch/<phase-id>.md
├── design-system/     # Sd Track 产物
├── docs/              # ADR / allowlist / runbook / 验收模板
├── state/             # §1.8 Phase 状态机 YAML（不 .gitignore）
├── logs/              # 失败现场 · 每 Phase `phase-sX-<run>.md`
└── .github/workflows/ # ci.yml · nightly.yml · release.yml
```

## 当前阶段门禁状态

| Phase | Gate | 状态 |
|---|---|---|
| **S0** | Design Gate | **豁免**（§1.5 C 级 · `design/arch/s0-bootstrap.md` `exempt: true`）|
| S0 | DoD | 本地已绿 · `s0-done` tag 本地已打 · 远端 push 待用户提供 remote URL |
| S1 | DoR | 待 S0 push 完成 |

## 本地跑通 S0

```bash
# 后端骨架
(cd backend && mvn -q -T 1C -DskipTests package)

# 前端骨架（需 pnpm install 首次拉依赖）
(cd frontend && pnpm install && pnpm -r build)

# 工具白名单自检
ops/scripts/check-allowlist.sh s0

# 架构一致性（S0 豁免，直接放行）
ops/scripts/check-arch-consistency.sh s0
```

## 贡献 / AI Agent 约束

严格遵守 `design/落地实施计划_v1.0_AI自动执行.md` §1.4/§1.5/§1.6/§1.7/§1.8：

1. **工具白名单硬规则**（§1.6）· 禁止 Figma / MyBatis / Seata / Netflix OSS / LangChain4j / latest tag / sudo
2. **Design Gate 双闸**（§1.7）· S3/S4/S5/S7/S8/S11 强制 `gate_status: approved`
3. **Context & Continuity**（§1.8）· Agent 冷启动 5 步读入 · 状态机外化 `state/phase-<id>.yml`
4. **提交规范**（§3.3）· Conventional Commits · `<type>(<scope>): <subject>` + footer 引用方案章节号

## License

详见 [`LICENSE`](./LICENSE)。
