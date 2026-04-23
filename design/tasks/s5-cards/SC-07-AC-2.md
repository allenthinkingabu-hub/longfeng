---
ac_id: SC-07.AC-2
critical: false
commit_prefix: "[SC-07-AC2]"
related_files:
  - backend/review-plan-service/src/main/java/com/longfeng/reviewplan/algo/SM2Algorithm.java
  - backend/review-plan-service/src/main/java/com/longfeng/reviewplan/algo/SM2Result.java
  - backend/review-plan-service/src/main/java/com/longfeng/reviewplan/algo/AlgorithmConfig.java
  - backend/review-plan-service/src/test/java/com/longfeng/reviewplan/algo/SM2AlgorithmUT.java
  - backend/review-plan-service/src/main/resources/application.yml
---

# SC-07.AC-2 · SM2Algorithm 纯函数 + AlgorithmConfig guard-rail

> **critical: false** · Verifier 30% 抽样复写（≥ 2 matrix 行）

## AC 定义（business-analysis.yml 摘录）

> SM2Algorithm 纯函数 · 输入 (easeFactor, intervalDays, quality, cfg) · 输出 (nextEaseFactor, nextIntervalDays)。quality<3 时 nextEaseFactor=cfg.easeInit(2.5) / nextIntervalDays=1（Q-C reset）；quality≥3 按论文公式 + ease clamp [1.3, 2.5]、interval ≤ 60d（Q-C/F）。

## arch.md §1.4 五行摘录（SC-07.AC-2）

- **API**：`SM2Algorithm.compute(BigDecimal ease, Integer interval, Integer quality, AlgorithmConfig cfg) → SM2Result(nextEase, nextInterval)`
- **Domain**：`quality<3 → reset ease=2.5, interval=1`；`quality≥3 → nextEase = clamp(ease + (0.1 - (5-q)*(0.08+(5-q)*0.02)), easeMin, easeMax)`, `nextInterval = min(intervalMaxDays, round(interval * nextEase))`
- **Event**：无（纯函数 · 无副作用）
- **Error**：`IllegalArgumentException` for quality ∉ [0, 5]
- **NFR**：单次 ≤ 10ms · 无 IO

## verification_matrix（5 行 · Builder 分 commit）

| # | category | given (ease, interval, q) | when | then | commit |
|---|---|---|---|---|---|
| 1 | happy_path.0 | (2.5, 0, 5) | compute | `nextEase=clamp(2.6,1.3,2.5)=2.5` · `nextInterval=1` | **A** |
| 2 | error_paths.0 | (2.5, 0, -1) 或 (2.5, 0, 6) | compute | `IllegalArgumentException` | **A** |
| 3 | boundary.0 | (1.3, 30, 0) · ease 已地板 · quality<3 reset | compute | `nextEase=2.5`（reset · Q-C）· `nextInterval=1` | **A** |
| 4 | boundary.1 | (2.5, 60, 5) · interval 已顶板 | compute | `nextInterval=60`（不升 · clamp） | **A** |
| 5 | observable.0 | 1000 次执行 | 采样性能 | 单次 ≤ 10ms · 无 allocation | **B** |

## Builder 实施步骤（§9.7 Step 2/3/11）

### commit A · 核心算法 + AlgorithmConfig + ≥20 @Test

```
feat(s5): [SC-07-AC2] SM2Algorithm 纯函数 + AlgorithmConfig guard-rail

- algo/AlgorithmConfig.java @ConfigurationProperties("review.sm2")
  - easeMin=1.3 · easeMax=2.5 · easeInit=2.5 · intervalMaxDays=60 · qualityPenaltyStep=0.2
  - @Validated · @Min/@Max 约束
- algo/SM2Result.java record (BigDecimal nextEase, int nextInterval)
- algo/SM2Algorithm.java public static compute(...) 纯函数
  - quality<3: return SM2Result(easeInit, 1) [reset · Q-C]
  - quality≥3: 按论文公式 + clamp + round
  - 首次 (interval=0): nextInterval=1
- application.yml 补 review.sm2.* 默认值
- test/algo/SM2AlgorithmUT.java ≥ 20 @Test
  - quality=0/1/2/3/4/5 × ease=2.5/1.8/1.3 × interval=0/1/5/30/60 矩阵
  - happy_path.0 + error_paths.0 + boundary.0 + boundary.1
- 每 @Test 含 @CoversAC("SC-07.AC-2#<category>.<index>")
```

### commit B · 性能 observable

```
feat(s5): [SC-07-AC2] SM2Algorithm 性能基准

- test/algo/SM2AlgorithmPerfTest.java
  - @Test observable_0_performance_under_10ms
  - 1000 次 compute · System.nanoTime 均值 ≤ 10ms
  - @CoversAC("SC-07.AC-2#observable.0")
```

## Definition of Card Done

- [ ] 2 个 commit 推 · 前缀 `[SC-07-AC2]`
- [ ] `SM2AlgorithmUT` ≥ 20 @Test + 5 matrix 行 `@CoversAC` 注解齐全
- [ ] DoD-S5-02 达标（V-S5-02 `grep -c '@Test'` ≥ 20）
- [ ] mvn -pl review-plan-service test -Dtest='SM2Algorithm*' 绿
- [ ] Verifier 抽样复写 ≥ 2 行（建议 error_paths.0 + boundary.0）

## Verifier 抽样要求（critical = false · ≥ 30%）

- **必抽 error_paths.0**（校验异常分支）· **必抽 boundary.0**（SM-2 reset 语义是 Q-C 决策核心）
- 可选抽 happy_path.0 或 boundary.1
- Verifier 可直接写 junit · 不依赖 Builder fixture
