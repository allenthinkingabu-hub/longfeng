# AI 落地实施计划 · Phase SD · CI/CD 自动部署

> **文件定位**：继 S0~S4 之后插入的「专项阶段」，目的是把**开发者 `git push` → 生产/预发环境自动更新**的闭环接通。
>
> **与 `AI执行Runbook_git到部署闭环_3节点kubeadm.md` 的关系**：那份 Runbook 针对 **kubeadm + Harbor + Gitea** 自建栈；本计划针对**现状栈**：已在 `infra/rancher-k8s-install.sh` 安装的 **Rancher RKE1 v2.8.5 + K8s 1.28 集群**（3 节点 `longfeng-prod`），叠加 **阿里云 ACR + GitHub Actions + ArgoCD**。两者路径分叉，请勿混用。
>
> **为何插入此阶段**：原总计划把部署自动化放在 S10，但 S4~S9 缺少自动环境会反复堵塞手工部署。本阶段先搭好链条，S5+ 自然接入。
>
> **执行主体**：AI Agent
> **阶段编号**：SD（Deploy Phase）
> **估时**：AI 实操 ~90 min + 人工介入累计 ~12 min
> **风险等级**：中（涉及仓库 secrets 与生产集群，但所有步骤可回滚）

---

## 0. AI 工作契约

| 项 | 约定 |
|---|---|
| 状态载体 | `state/phase-sd.yml` —— 每个子任务一行 `status: todo/in_progress/done/blocked` |
| 进度汇报 | 每完成一个子任务在对话里汇报 `✅ SD.X.Y → 下一步 SD.X.Z`；长耗时（>2 min）每 2 min 心跳一次 |
| 卡死判定 | 单步无进展 > 10 min → 停止执行、列出 3 个候选处置方案、等人工拍板 |
| 幂等要求 | 所有脚本必须 `if exists then skip`，允许任意次重跑而不破坏状态 |
| 禁止动作 | 跨阶段混合提交、`git push --force`、`kubectl delete ns <非本阶段创建的>`、`helm uninstall` 未经确认 |
| 回滚单元 | 应用层（Helm/K8s 资源）→ ArgoCD revert git commit；基础设施层（ACR/ArgoCD 本体）→ 人工决策 |
| 凭据处理 | 所有密码/token 不落 git，只写 `state/phase-sd.yml.vault`（gitignore 已覆盖）或直接进 K8s Secret |

---

## 1. 前置硬门禁

开跑前必须满足：

1. `kubectl get nodes` 返回 3 台 Ready
2. 本机已配置 `KUBECONFIG` 能连 `longfeng-prod`
3. 当前分支是 `feature/phase-sd-*`，不是 `main`
4. `state/phase-s4.yml` 标记为 done 或冻结（本阶段独立于 S4~S9 主线）
5. 工具链：`kubectl` / `helm` / `argocd` CLI / `gh` CLI / `docker` / `jq` / `yq` 本机可用
6. 收到人工交付物 #1（见 §4）

**AI 在 SD.0 执行前逐项 check；任一不满足则停，不启动。**

---

## 2. 里程碑切分（5 个阶段，依次推进）

### SD.1 · 镜像仓库就位（ACR）

| 子任务 | 执行方 | 验收（自动化可判定） |
|---|---|---|
| SD.1.1 开通阿里云 ACR 个人版，创建命名空间 `longfeng`，生成固定密码 | **人工** | 口头交付：registry 地址 + 用户名 + 密码 |
| SD.1.2 本机 `docker login` 到 ACR | AI | 返回 `Login Succeeded` |
| SD.1.3 构建并 push 一个极简验证镜像（`alpine + echo`），打上 `ping` tag | AI | `docker pull` 同镜像成功 |
| SD.1.4 在集群创建 3 个 namespace（`argocd` / `longfeng-staging` / `longfeng-prod`） | AI | `kubectl get ns` 可见 |
| SD.1.5 在 3 个 namespace 中各创建 `imagePullSecret: acr-pull` | AI | `kubectl get secret acr-pull -n <ns>` 存在 |
| SD.1.6 更新 `state/phase-sd.yml`，SD.1 标记 done | AI | 文件更新、git commit（不 push） |

**阶段验收**：AI 在任一 ns 手动 `kubectl run test --image=<acr>/longfeng/ping --rm -it -- echo ok` 返回 ok。

**人工介入点 1**：SD.1.1 给凭据（~5 min）。

---

### SD.2 · ArgoCD 控制面就位

| 子任务 | 执行方 | 验收 |
|---|---|---|
| SD.2.1 helm repo add argo + helm install argocd（image 从 ACR 代理，避免国外拉取慢） | AI | 所有 `argocd-*` pod Ready |
| SD.2.2 取出初始 admin 密码，改为随机强密码写入 `state/phase-sd.yml.vault` | AI | 旧密码失效、新密码能 login |
| SD.2.3 暴露 UI：优先 Ingress（若集群已装 ingress-controller），否则 NodePort | AI | `curl -k https://<exposed>/healthz` → `ok` |
| SD.2.4 `argocd login` + `argocd cluster list` 能列出 in-cluster | AI | 命令成功 |
| SD.2.5 创建 ArgoCD Project `longfeng`（限定源 repo 与目标 ns） | AI | `argocd proj get longfeng` 存在 |
| SD.2.6 更新 `state/phase-sd.yml`，SD.2 标记 done | AI | 文件更新 |

**阶段验收**：ArgoCD UI 可登录、Project `longfeng` 可见、in-cluster 在 cluster 列表中。

**无需人工介入。**

---

### SD.3 · CI 流水线接入 build & push

| 子任务 | 执行方 | 验收 |
|---|---|---|
| SD.3.1 为 `backend/wrongbook-service` 写最小 Dockerfile（Spring Boot layered jar，基于 `eclipse-temurin:21-jre`） | AI | 本机 `docker build` 产出 <200MB 镜像 |
| SD.3.2 `.github/workflows/ci.yml` 新增 job `backend-image-wrongbook`（trigger：`push to main` 且 `backend/wrongbook-service/**` 有变更） | AI | YAML 通过 `actionlint`；CI 首跑绿 |
| SD.3.3 image tag 策略：`<commit-short-sha>` + `latest`（latest 仅 staging 用） | AI | 规则写入 `ops/scripts/build-tag.sh` |
| SD.3.4 在 GitHub repo 添加 Actions secrets：`ACR_REGISTRY` / `ACR_USER` / `ACR_PASS` | **人工** | AI 通过 `gh api repos/:owner/:repo/actions/secrets` 读到 3 项 |
| SD.3.5 AI 提 PR（分支 `feature/phase-sd-ci`），等 `allowlist-check` / `arch-consistency-check` / `backend-ci` 全绿 → 人工 review → merge | AI + **人工** | CI 全绿 + PR merged |
| SD.3.6 首次 main 构建后，ACR 里出现 `wrongbook-service:<sha>` 镜像 | AI | `gh api` + ACR API 交叉验证 |
| SD.3.7 更新 `state/phase-sd.yml`，SD.3 标记 done | AI | 文件更新 |

**阶段验收**：ACR 控制台中 `longfeng/wrongbook-service` 仓库里至少有 1 个带 commit SHA 的 tag，时间与最新 main commit 一致。

**人工介入点 2**：SD.3.4 填 3 个 Actions secrets（~2 min）；SD.3.5 PR review+merge（~3 min）。

---

### SD.4 · 首个服务 ArgoCD 接管（wrongbook-service → staging）

| 子任务 | 执行方 | 验收 |
|---|---|---|
| SD.4.1 补全 `helm/wrongbook-service/templates/`：Deployment / Service / ConfigMap / probe / resource / `imagePullSecrets: acr-pull` | AI | `helm lint` + `helm template` 无 error |
| SD.4.2 创建 `helm/umbrella/values-staging.yaml`（image repo+tag、DB 连接串占位、Java opts） | AI | `helm template umbrella -f values-staging.yaml` 通过 |
| SD.4.3 将 `infra/argocd/applications/staging.yaml` 的 placeholder 替换为真实值（repoURL、targetRevision、valueFiles 列表） | AI | 文件不再含 `PLACEHOLDER_*` |
| SD.4.4 `kubectl apply -f infra/argocd/applications/staging.yaml` | AI | ArgoCD 中出现 `longfeng-staging` Application |
| SD.4.5 触发首次 sync（应用 OutOfSync → Syncing → Synced） | AI | `argocd app get longfeng-staging` 显示 Healthy + Synced |
| SD.4.6 `kubectl get pod -n longfeng-staging` 看到 `wrongbook-service-*` Running | AI | readiness probe 通过 |
| SD.4.7 从集群内 curl `wrongbook-service` 健康端点 `/actuator/health` | AI | HTTP 200、body 含 `"status":"UP"` |
| SD.4.8 更新 `state/phase-sd.yml`，SD.4 标记 done | AI | 文件更新 |

**阶段验收**：ArgoCD UI 中 `longfeng-staging` 显示绿色 `Healthy`；`wrongbook-service` pod `/actuator/health` 返回 UP。

**无需人工介入。**

---

### SD.5 · 端到端闭环验证

| 子任务 | 执行方 | 验收 |
|---|---|---|
| SD.5.1 AI 在 `wrongbook-service` 新增一个无害 endpoint 或改 `/actuator/info` 的版本字符串，push 到 `feature/phase-sd-e2e` | AI | commit 推送成功 |
| SD.5.2 开 PR → CI 绿 → merge 到 main | AI + **人工** | PR merged |
| SD.5.3 观察 GitHub Actions 跑完 `backend-image-wrongbook` job、ACR 出现新 tag | AI | 新 tag SHA 等于 merge commit SHA |
| SD.5.4 AI 自动更新 `helm/umbrella/values-staging.yaml` 的 image tag（写入新 SHA）并 push（可借 renovate-style helper，或手工 commit） | AI | git log 看到 tag bump commit |
| SD.5.5 ArgoCD 检测到 Git 变化 → 自动 sync（或手动 `argocd app sync`） | AI | ArgoCD 显示新 Revision |
| SD.5.6 `kubectl rollout status deploy/wrongbook-service -n longfeng-staging` 显示滚动完成 | AI | 旧 pod Terminating，新 pod Running 且 image tag=新 SHA |
| SD.5.7 curl 改动过的 endpoint 看到新值 | AI | 返回新内容 |
| SD.5.8 产出闭环验证报告 `reports/sd-e2e-report.md`（包含耗时分布、步骤截图/日志摘要、验收结果） | AI | 文件存在 |
| SD.5.9 更新 `state/phase-sd.yml`，SD.5 标记 done、整个 Phase SD 标记 complete | AI | 文件更新 |

**阶段验收**：`reports/sd-e2e-report.md` 显示：commit SHA → CI 完成耗时 → ACR tag 出现 → values 更新 → ArgoCD sync → 新 pod Running → 端点返回新值，全链路 < 15 min。

**人工介入点 3**：SD.5.2 PR review+merge（~2 min）；SD.5.9 完结签收（~5 min）。

---

## 3. 强门禁（沿用 S0~S4 机制）

### 3.1 白名单（`ops/scripts/check-allowlist.sh sd`）

Phase SD 只允许修改以下路径：

```
.github/workflows/ci.yml
backend/wrongbook-service/Dockerfile
helm/umbrella/values-staging.yaml
helm/wrongbook-service/templates/**
helm/wrongbook-service/values.yaml
infra/argocd/applications/staging.yaml
ops/scripts/build-tag.sh
reports/sd-*.md
state/phase-sd.yml
```

**越界 → CI 红 → AI 不准合 PR。**

### 3.2 架构一致性（`ops/scripts/check-arch-consistency.sh sd`）

Phase SD 归为**基础设施阶段**，符号表对齐豁免（同 S0~S2）；但：
- `helm/wrongbook-service/values.yaml` 中的 `image.repository` 必须与 `backend/wrongbook-service/pom.xml` 的 `artifactId` 派生的镜像名一致。
- `infra/argocd/applications/staging.yaml` 的 `source.repoURL` 必须是当前 git remote `origin`。

AI 必须在提 PR 前本地跑上述两项，失败则本地修。

---

## 4. 人工介入清单（全程 3 次 ≈ 12 min）

| 介入点 | 在哪个子任务 | 你要做什么 | 预估耗时 |
|---|---|---|---|
| #1 | SD.1.1 之前 | 登陆阿里云 → 开通 ACR 个人版 → 建 namespace `longfeng` → 设固定密码 → 交付给 AI：registry 地址 + 用户名 + 密码 | 5 min |
| #2 | SD.3.4 | 去 GitHub repo → Settings → Secrets and variables → Actions → 添加 3 个 secrets（`ACR_REGISTRY` / `ACR_USER` / `ACR_PASS`） | 2 min |
| #3 | SD.3.5 & SD.5.2 | PR review 并点 Merge（2 次各 ~3 min） | 6 min |
| 收尾 | SD.5.9 之后 | 看 `reports/sd-e2e-report.md` → 签收 or 打回 | 自选 |

**任何超出上表的"AI 请给我 X"，都视为 AI 违约，请打回让它自己想办法或停止汇报。**

---

## 5. AI 的失败契约（必须遵守）

| 情境 | AI 必须这么做 |
|---|---|
| 网络慢：镜像拉取 < 1 MB/min 持续 5 min | 自动切换策略（ACR 代理 / 内网 `docker save \| docker load` / 换 dockerhub 镜像源），不等死 |
| 权限错误（HTTP 401/403） | 立即停止、打印完整错误、**不**盲目重试 |
| K8s 资源冲突（already exists / immutable field） | 读取现有资源 diff 后判断：可合并 → 合并；不可合并 → 停止、列候选方案 |
| Helm schema error / chart render 失败 | 打印 diff、给出 3 个修复选项让人工挑，**不**自行大改 |
| pod CrashLoopBackOff | 读 `kubectl logs --previous` + events、给出根因判断，**不**盲目 `kubectl delete pod` |
| CI 红（非白名单/arch-consistency 导致） | 读 log、定位根因、如为本阶段脚本 bug 则修并重推；如为上游脚本 bug 则停止、上报 |
| Unknown unknown（从未见过的错误） | 停止、打包 `kubectl get events`、`docker logs`、`argocd app get -o yaml` 到 `reports/sd-debug-<timestamp>.txt` 等人工 |

---

## 6. 状态机（`state/phase-sd.yml` schema）

```yaml
phase: SD
title: CI/CD 自动部署
owner: ai-agent
started_at: <ISO8601>
completed_at: null
milestones:
  SD.1:
    title: 镜像仓库就位
    status: todo | in_progress | done | blocked
    started_at: ...
    completed_at: ...
    sub_tasks:
      SD.1.1: { status, note }
      ...
  SD.2: ...
  SD.3: ...
  SD.4: ...
  SD.5: ...
vault:  # .gitignore 覆盖，不入 git
  acr:
    registry: <value>
    user: <value>
    password_ref: <k8s secret name>
  argocd:
    admin_password_ref: <k8s secret name>
blockers: []
```

---

## 7. 与总计划的关系

| 阶段 | 原计划位置 | 本方案动作 |
|---|---|---|
| S0~S4 | 已完成 / 进行中 | 不动 |
| **SD** | **新插入** | **本文档** |
| S5~S9（其余业务 service） | 沿用 S 编号 | 完成 SD 后，每个新服务只需：加 Dockerfile + helm chart + 复制 staging.yaml 即可自动部署，**不再需要新建 ArgoCD App 手工流程** |
| S10 多环境/灰度/回滚 | 原计划保留 | SD 只做 staging 单环境；生产 + 灰度 + 回滚策略在 S10 补齐 |

---

## 8. 推进建议（不是命令，是建议）

1. **先跑 SD.1 + SD.2**：一个下午（~35 min AI 实操 + 5 min 人工）即完，全毁重来的代价低（`helm uninstall argocd && kubectl delete ns argocd` 清零）。
2. **SD.1 + SD.2 跑顺后再批准 SD.3~SD.5**：避免一次性批准 5 个阶段，AI 若在 SD.3 方向跑偏，回滚成本大。
3. **SD 完成后**：在 `design/落地实施计划_v1.0_AI自动执行.md` 的版本更新记录里加一行"插入 Phase SD"，保持总账册真实。

---

## 9. 完成标志

- [ ] `state/phase-sd.yml` 所有 milestones status=done
- [ ] `reports/sd-e2e-report.md` 存在且验收通过
- [ ] `wrongbook-service` 在 `longfeng-staging` ns 中 Healthy 运行 ≥ 24 h
- [ ] 任意一次 main 分支的 commit 能在 ≤ 15 min 内反映到 staging 的 pod 上
- [ ] 人工在 PR 中签字 `Phase SD · signed off by <name> at <timestamp>`
