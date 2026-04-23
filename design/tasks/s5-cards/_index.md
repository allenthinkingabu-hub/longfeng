# S5 Planner 切卡索引（§25 Playbook Step 7）

**切卡策略**：原 v1.8 "每 AC × matrix 1 行 = 1 卡" 严格做法产 28 卡 · 本 Phase 务实合并为 **5 张 AC 级卡** · 每卡内嵌完整 matrix 清单 · Builder 按卡内 matrix 行分 commit（每行 ≥1 commit · 符合 v1.8 #13 精神）。

## 卡片清单

| 卡片 | AC | Critical | Builder commit 前缀 | matrix 行数 |
|---|---|---|---|---|
| [SC-07-AC-1](./SC-07-AC-1.md) | Consumer 幂等 7 行 | ✅ | `[SC-07-AC1]` | 5 |
| [SC-07-AC-2](./SC-07-AC-2.md) | SM2Algorithm 纯函数 | ❌ | `[SC-07-AC2]` | 5 |
| [SC-08-AC-1](./SC-08-AC-1.md) | POST complete + mastered | ✅ | `[SC-08-AC1]` | 7 |
| [SC-09-AC-1](./SC-09-AC-1.md) | GET /review-stats 聚合 | ❌ | `[SC-09-AC1]` | 6 |
| [SC-10-AC-1](./SC-10-AC-1.md) | Feign + Sentinel + Caffeine | ✅ | `[SC-10-AC1]` | 5 |

**合计 28 matrix 行** · Builder commit 最低 28 次（每行 1 commit）· 实际可合并同 AC 的 happy_path + boundary/observable 为 1 commit · 但 error_paths 单独（便于验证）。

## Builder Prompt 硬约束（§1.5 约束 #13 + §25 Playbook Step 7）

1. **Commit message 前缀**：`[SC-XX-ACY]` 必带 · 例 `[SC-07-AC1] feat: WrongItemAnalyzedConsumer 幂等 7 行 INSERT`
2. **测试注解**：`@CoversAC("SC-XX.AC-Y#<category>.<index>")` · 如 `@CoversAC("SC-07.AC-1#happy_path.0")`
3. **上下文硬隔离**：Builder 读**只本卡片**的内容 · 不得翻阅其他 AC 卡或全文档
4. **文件范围**：`related_files` 硬白名单（每卡列明）· 越界触发 pre-commit 拒收
5. **测试命名**：`scenario_sc<XX>_ac<Y>_<category>_<index>_<descriptor>` · 如 `scenario_sc07_ac1_happy_path_0_consumer_inserts_seven_rows`

## Verifier 独立复写（§25 Playbook Step 8）

| AC | Critical | Verifier 动作 |
|---|---|---|
| SC-07.AC-1 | ✅ | **100% 复写** · 独立会话写断言（不复用 Builder 测试） |
| SC-07.AC-2 | ❌ | 30% 抽样复写（≥ 2 matrix 行） |
| SC-08.AC-1 | ✅ | **100% 复写** |
| SC-09.AC-1 | ❌ | 30% 抽样复写（≥ 2 matrix 行） |
| SC-10.AC-1 | ✅ | **100% 复写** |

Verifier 产 `reports/verifier/s5-verifier.md` · 绿灯后方可打 `s5-done`。

## 产出物根目录

```
backend/review-plan-service/src/main/java/com/longfeng/reviewplan/
├── algo/            # SC-07.AC-2 owner
├── entity/          # SC-07.AC-1 + SC-08.AC-1 共享
├── repo/            # 同上
├── service/         # SC-07.AC-1 + SC-08.AC-1 + SC-09.AC-1
├── consumer/        # SC-07.AC-1 owner
├── job/             # Support SC-07 节点驱动
├── feign/           # SC-10.AC-1 owner + wrongbook/notification 配套
├── controller/      # SC-08.AC-1 + SC-09.AC-1 owner
└── config/          # 跨 AC · AlgorithmConfig + FeignConfig + CaffeineConfig

backend/review-plan-service/src/test/java/com/longfeng/reviewplan/
├── algo/SM2AlgorithmUT.java                # SC-07.AC-2 · ≥ 20 @Test
├── service/ReviewPlanServiceIT.java        # SC-07.AC-1 + SC-08.AC-1 · Testcontainers
├── controller/ReviewPlanControllerIT.java  # SC-08.AC-1 + SC-09.AC-1 · MockMvc
├── feign/CalendarFeignClientIT.java        # SC-10.AC-1 · Sentinel mock
└── ReviewFlowE2EIT.java                    # 跨 AC · 端到端

backend/common/src/main/resources/db/migration/
├── V1.0.053__review_outcome.sql            # ADR 0014
├── V1.0.054__review_plan_outbox.sql        # ADR 0014
└── V1.0.055__review_plan_mastered_index.sql # ADR 0014

docs/allowlist/s5.yml
```
