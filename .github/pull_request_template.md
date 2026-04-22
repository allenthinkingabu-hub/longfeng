# Pull Request — 落地计划 §3.5 六条红线勾选

## 变更摘要

<!-- 一句话：这 PR 做了什么？ -->

## 关联章节

<!-- 方案 / 计划 / ADR 章节号（e.g. 方案 §2A.5 / 计划 §8.Step 9 / ADR 0006） -->
- Refs:

## 六条红线（合入 main 前必勾）

- [ ] CI 全绿（build + unit + contract + SAST + license）
- [ ] E2E 冒烟（`pnpm e2e:smoke` · SC-01/02/05/11/12/13 六份）< 8 min 全绿（S9 生效前允许 N/A 并注明理由）
- [ ] 代码覆盖率：后端 ≥ 70% · 前端 ≥ 65%（新增代码不低于 80%）（S1 前允许 N/A）
- [ ] 至少一人 Approve（anonymous-service / 安全代码需 @security-team 必 Approve）
- [ ] 无 `TODO` / `FIXME` 未带 ticket
- [ ] Commit message 符合 Conventional Commits（`<type>(<scope>): <subject>`）

## Design Gate（如本 PR 属 S3/S4/S5/S7/S8/S11 领域重镇 Phase · §1.7）

- [ ] `design/arch/<phase-id>.md` `gate_status: approved`
- [ ] `biz_gate: approved`（G-Biz 已签字）
- [ ] `special_requirements` 字段非空或显式 `none`（v1.5 §1.7 规则 A'）
- [ ] `ops/scripts/check-arch-consistency.sh <phase-id>` 返回 0

## 工具白名单（§1.6）

- [ ] `ops/scripts/check-allowlist.sh <phase-id>` 返回 0
- [ ] 未使用 §1.6 规则 B 中的禁用工具 / 模式（MyBatis / Seata / Netflix OSS / LangChain4j / latest tag / sudo）

## Context & Continuity（§1.8）

- [ ] 若触及 Phase state：`state/phase-<phase-id>.yml` 已通过 `state-advance.sh` 更新 · 未直接 `echo >>`
- [ ] 若新产出跨 Phase 契约：`state/interfaces.yml` 已 append 且 `sha256` 匹配实际文件
- [ ] 若本 PR 是 Phase 收尾：`bash ops/scripts/check-continuity.sh <phase-id>` 返回 0

## 回滚预案

<!-- 出问题怎么撤？参考 Phase §4.10 / §5.10 等局部回滚章节 -->
