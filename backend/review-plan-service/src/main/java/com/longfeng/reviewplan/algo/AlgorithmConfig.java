package com.longfeng.reviewplan.algo;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * SM-2 算法 guard-rail 参数 · Nacos 动态下发 · §9.4 禁止硬编码阈值。
 *
 * <p>ADR 0013 · Q-C 决策：quality&lt;3 reset 到 easeInit / 1 day。
 */
@Validated
@ConfigurationProperties(prefix = "review.sm2")
public record AlgorithmConfig(
    @NotNull @DecimalMin("1.3") @DecimalMax("2.5") BigDecimal easeMin,
    @NotNull @DecimalMin("1.3") @DecimalMax("2.5") BigDecimal easeMax,
    @NotNull @DecimalMin("1.3") @DecimalMax("2.5") BigDecimal easeInit,
    @NotNull @Min(1) @Max(365) Integer intervalMaxDays,
    @NotNull @DecimalMin("0.01") @DecimalMax("1.0") BigDecimal qualityPenaltyStep) {

  /** Default preset used when application.yml omits config (tests / unit). */
  public static AlgorithmConfig defaults() {
    return new AlgorithmConfig(
        new BigDecimal("1.3"),
        new BigDecimal("2.5"),
        new BigDecimal("2.5"),
        60,
        new BigDecimal("0.2"));
  }
}
