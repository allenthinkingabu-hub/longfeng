package com.longfeng.reviewplan.algo;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * SM-2 算法纯函数 · 落地计划 §9.0.5 SC-07.AC-2 · ADR 0013.
 *
 * <p>决策锚点：
 *
 * <ul>
 *   <li>Q-C · quality&lt;3 reset 到 cfg.easeInit / interval=1d（SM-2 论文标准）
 *   <li>Q-F · 节点独立 SM-2 · 不级联后续节点
 *   <li>ease guard-rail ∈ [cfg.easeMin, cfg.easeMax] · interval ≤ cfg.intervalMaxDays
 * </ul>
 *
 * <p>本类为纯函数（stateless · 无 IO · 无 Spring bean）· 单次调用 &le; 10ms。
 */
public final class SM2Algorithm {

  private SM2Algorithm() {}

  /**
   * Apply one SM-2 step based on a quality rating.
   *
   * @param easeFactor current ease factor (stored BigDecimal)
   * @param intervalDays current interval days ({@code 0} means first-ever review)
   * @param quality review quality in {@code [0, 5]}
   * @param cfg algorithm config (guard-rails)
   * @return next ease + next interval
   * @throws IllegalArgumentException when {@code quality} not in {@code [0, 5]}
   */
  public static SM2Result compute(
      BigDecimal easeFactor, int intervalDays, int quality, AlgorithmConfig cfg) {
    if (quality < 0 || quality > 5) {
      throw new IllegalArgumentException("quality must be in [0, 5], got " + quality);
    }

    // Q-C: quality<3 → hard reset to easeInit + interval=1 (SM-2 paper).
    if (quality < 3) {
      return new SM2Result(cfg.easeInit(), 1);
    }

    // quality≥3: SM-2 canonical update + clamp + round.
    // nextEase = ease + (0.1 - (5-q)*(0.08 + (5-q)*0.02))
    int delta = 5 - quality;
    BigDecimal adjustment =
        new BigDecimal("0.1")
            .subtract(
                BigDecimal.valueOf(delta)
                    .multiply(new BigDecimal("0.08").add(BigDecimal.valueOf(delta).multiply(new BigDecimal("0.02")))));

    BigDecimal nextEase = easeFactor.add(adjustment);
    if (nextEase.compareTo(cfg.easeMin()) < 0) {
      nextEase = cfg.easeMin();
    } else if (nextEase.compareTo(cfg.easeMax()) > 0) {
      nextEase = cfg.easeMax();
    }
    nextEase = nextEase.setScale(3, RoundingMode.HALF_UP);

    // First-ever review (interval=0): force nextInterval=1 regardless of ease.
    int nextInterval;
    if (intervalDays == 0) {
      nextInterval = 1;
    } else {
      nextInterval =
          BigDecimal.valueOf(intervalDays)
              .multiply(nextEase)
              .setScale(0, RoundingMode.HALF_UP)
              .intValue();
    }
    if (nextInterval > cfg.intervalMaxDays()) {
      nextInterval = cfg.intervalMaxDays();
    }
    if (nextInterval < 1) {
      nextInterval = 1;
    }

    return new SM2Result(nextEase, nextInterval);
  }
}
