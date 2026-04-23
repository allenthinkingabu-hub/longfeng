#!/usr/bin/env bash
# 落地计划 §10.5 S5.5 后端联调闸
# 用法：backend-integration-gate.sh [--dry-run]
# 退出码：0=通过，1=违规
#
# 核心职责（S0 骨架 · S5.5 Phase 节点执行时补全）：
# 主链路 3×3（错题主域 × AI 解析 × 复习计划）+ 降级 3 场景（LLM 超时/匿名上限/OSS 不可达）
# 1. 启动 docker compose 依赖栈（postgres-pgvector · rocketmq · nacos · sentinel · minio-oss）
# 2. 启动 4 个服务（gateway/wrongbook/ai-analysis/review-plan）
# 3. 跑 E2E 脚本：创建错题 → 触发 AI 解析 → 生成复习计划 → 验证 outbox + 事件链
# 4. 降级场景：mock LLM 超时 → 降级占位卡片；匿名 quota 满 → 429；OSS 不可达 → 文件域降级为 skip
set -euo pipefail

DRY_RUN=false
for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN=true ;;
    *) echo "unknown flag: $arg"; exit 2 ;;
  esac
done

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"

if $DRY_RUN; then
  echo "[backend-integration-gate] dry-run skeleton OK · 实际编排在 S5.5 节点执行时补"
  exit 0
fi

# TODO: S5.5 Phase 节点补：
# 1. cd $REPO_ROOT/backend && docker compose -f ../ops/compose.it.yml up -d
# 2. waitForHealthy postgres rocketmq nacos
# 3. 4 个 Spring Boot app 启动 · 等待 /actuator/health
# 4. 跑 e2e/backend-integration/specs/*.spec.ts（scenario 3×3 + degrade 3）
# 5. 产 reports/backend-integration-gate-<timestamp>.json · 覆盖率 + 延迟分布 + 降级触发证据
# 6. 任一 scenario 失败 · exit 1；全绿 · exit 0
echo "[backend-integration-gate] skeleton OK · TODO: S5.5 节点执行时补主链路 3×3 + 降级 3 场景编排"
exit 0
