package com.longfeng.reviewplan.algo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.longfeng.common.test.CoversAC;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SM-2 算法纯函数单测 · 落地计划 §9.8 V-S5-02 · ≥ 20 @Test · DoD-S5-02.
 *
 * <p>matrix 覆盖：SC-07.AC-2 · happy_path.0 · error_paths.0 · boundary.0 · boundary.1 ·
 * observable.0（性能在 SM2AlgorithmPerfTest · 单独 commit B）。
 */
class SM2AlgorithmUT {

  private static final AlgorithmConfig CFG = AlgorithmConfig.defaults();

  // ============================================================
  // happy_path · SM-2 canonical update (quality≥3)
  // ============================================================

  @Test
  @DisplayName("happy_path.0 · first review · ease=2.5 · q=5 → clamp 2.5 · interval=1d")
  @CoversAC("SC-07.AC-2#happy_path.0")
  void scenario_sc07_ac2_happy_path_0_first_review_q5() {
    SM2Result r = SM2Algorithm.compute(new BigDecimal("2.5"), 0, 5, CFG);
    assertThat(r.nextEaseFactor()).isEqualByComparingTo("2.5"); // clamp at easeMax
    assertThat(r.nextIntervalDays()).isEqualTo(1); // first review rule
  }

  @Test
  @DisplayName("second review · ease=2.5 · interval=1 · q=5 → ease stays 2.5 · next=round(1*2.5)=3d")
  @CoversAC("SC-07.AC-2#happy_path.0")
  void second_review_q5_interval_grows() {
    SM2Result r = SM2Algorithm.compute(new BigDecimal("2.5"), 1, 5, CFG);
    assertThat(r.nextEaseFactor()).isEqualByComparingTo("2.5");
    assertThat(r.nextIntervalDays()).isEqualTo(3);
  }

  @Test
  @DisplayName("mid-interval · ease=2.2 · interval=7 · q=4 → ease clamp · next grows")
  void mid_interval_q4() {
    SM2Result r = SM2Algorithm.compute(new BigDecimal("2.2"), 7, 4, CFG);
    // adjustment(q=4) = 0.1 - 1*(0.08 + 1*0.02) = 0.1 - 0.1 = 0.0
    // nextEase = 2.2 + 0.0 = 2.2
    assertThat(r.nextEaseFactor()).isEqualByComparingTo("2.2");
    // nextInterval = round(7 * 2.2) = round(15.4) = 15
    assertThat(r.nextIntervalDays()).isEqualTo(15);
  }

  @Test
  @DisplayName("quality=3 · minimum good quality · ease drop but clamp at easeMin")
  void quality_3_ease_drops_but_clamp() {
    // adjustment(q=3) = 0.1 - 2*(0.08 + 2*0.02) = 0.1 - 0.24 = -0.14
    SM2Result r = SM2Algorithm.compute(new BigDecimal("1.4"), 10, 3, CFG);
    // 1.4 - 0.14 = 1.26 → clamp to 1.3
    assertThat(r.nextEaseFactor()).isEqualByComparingTo("1.3");
  }

  // ============================================================
  // error_paths · invalid quality
  // ============================================================

  @Test
  @DisplayName("error_paths.0 · quality=-1 → IllegalArgumentException")
  @CoversAC("SC-07.AC-2#error_paths.0")
  void scenario_sc07_ac2_error_paths_0_quality_negative() {
    assertThatThrownBy(() -> SM2Algorithm.compute(new BigDecimal("2.5"), 1, -1, CFG))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("quality");
  }

  @Test
  @DisplayName("error_paths.0 · quality=6 → IllegalArgumentException")
  @CoversAC("SC-07.AC-2#error_paths.0")
  void scenario_sc07_ac2_error_paths_0_quality_too_high() {
    assertThatThrownBy(() -> SM2Algorithm.compute(new BigDecimal("2.5"), 1, 6, CFG))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ============================================================
  // boundary.0 · quality<3 reset (Q-C 核心)
  // ============================================================

  @Test
  @DisplayName("boundary.0 · ease=1.3 · interval=30 · q=0 → reset ease=2.5 · interval=1")
  @CoversAC("SC-07.AC-2#boundary.0")
  void scenario_sc07_ac2_boundary_0_q0_resets_to_init() {
    SM2Result r = SM2Algorithm.compute(new BigDecimal("1.3"), 30, 0, CFG);
    assertThat(r.nextEaseFactor()).isEqualByComparingTo("2.5");
    assertThat(r.nextIntervalDays()).isEqualTo(1);
  }

  @Test
  @DisplayName("quality=1 (rank in forget band) → same reset as q=0")
  @CoversAC("SC-07.AC-2#boundary.0")
  void quality_1_resets() {
    SM2Result r = SM2Algorithm.compute(new BigDecimal("2.0"), 14, 1, CFG);
    assertThat(r.nextEaseFactor()).isEqualByComparingTo("2.5");
    assertThat(r.nextIntervalDays()).isEqualTo(1);
  }

  @Test
  @DisplayName("quality=2 (last forget threshold) → reset")
  @CoversAC("SC-07.AC-2#boundary.0")
  void quality_2_resets() {
    SM2Result r = SM2Algorithm.compute(new BigDecimal("1.8"), 7, 2, CFG);
    assertThat(r.nextEaseFactor()).isEqualByComparingTo("2.5");
    assertThat(r.nextIntervalDays()).isEqualTo(1);
  }

  @Test
  @DisplayName("reset is independent of current ease · even 2.5/60 gets reset")
  void reset_ignores_current_state() {
    SM2Result r = SM2Algorithm.compute(new BigDecimal("2.5"), 60, 0, CFG);
    assertThat(r.nextEaseFactor()).isEqualByComparingTo("2.5");
    assertThat(r.nextIntervalDays()).isEqualTo(1);
  }

  // ============================================================
  // boundary.1 · interval max cap
  // ============================================================

  @Test
  @DisplayName("boundary.1 · interval=60 (cap) · ease=2.5 · q=5 → interval stays 60")
  @CoversAC("SC-07.AC-2#boundary.1")
  void scenario_sc07_ac2_boundary_1_interval_cap_holds() {
    SM2Result r = SM2Algorithm.compute(new BigDecimal("2.5"), 60, 5, CFG);
    // round(60 * 2.5) = 150 → cap to 60
    assertThat(r.nextIntervalDays()).isEqualTo(60);
  }

  @Test
  @DisplayName("boundary · interval=30 · ease=2.5 · q=5 → next=60 cap (75→60)")
  @CoversAC("SC-07.AC-2#boundary.1")
  void boundary_interval_exceeds_cap_clamped() {
    SM2Result r = SM2Algorithm.compute(new BigDecimal("2.5"), 30, 5, CFG);
    assertThat(r.nextIntervalDays()).isEqualTo(60); // round(75) cap
  }

  @Test
  @DisplayName("boundary · ease at easeMin 1.3 · q=3 → drops · but clamped")
  void ease_at_floor_quality_3() {
    SM2Result r = SM2Algorithm.compute(new BigDecimal("1.3"), 10, 3, CFG);
    assertThat(r.nextEaseFactor()).isEqualByComparingTo("1.3"); // clamp
    // round(10 * 1.3) = 13
    assertThat(r.nextIntervalDays()).isEqualTo(13);
  }

  @Test
  @DisplayName("ease at easeMax 2.5 · q=5 → clamp preserves")
  void ease_at_ceiling_quality_5() {
    SM2Result r = SM2Algorithm.compute(new BigDecimal("2.5"), 5, 5, CFG);
    assertThat(r.nextEaseFactor()).isEqualByComparingTo("2.5"); // clamp
    assertThat(r.nextIntervalDays()).isEqualTo(13); // round(5*2.5)
  }

  // ============================================================
  // misc · ease recovery trajectory
  // ============================================================

  @Test
  @DisplayName("ease=1.8 · q=5 → ease climbs toward ceiling by 0.1")
  void ease_climbs_after_perfect_recall() {
    SM2Result r = SM2Algorithm.compute(new BigDecimal("1.8"), 3, 5, CFG);
    // 1.8 + 0.1 = 1.9
    assertThat(r.nextEaseFactor()).isEqualByComparingTo("1.9");
    // round(3 * 1.9) = 6
    assertThat(r.nextIntervalDays()).isEqualTo(6);
  }

  @Test
  @DisplayName("ease=1.5 · q=4 → no adjustment (q=4 delta=0)")
  void quality_4_no_ease_change() {
    SM2Result r = SM2Algorithm.compute(new BigDecimal("1.5"), 2, 4, CFG);
    assertThat(r.nextEaseFactor()).isEqualByComparingTo("1.5");
    // round(2 * 1.5) = 3
    assertThat(r.nextIntervalDays()).isEqualTo(3);
  }

  @Test
  @DisplayName("first review quality=3 → ease drops · interval=1 (first review rule)")
  void first_review_quality_3_ease_drop() {
    SM2Result r = SM2Algorithm.compute(new BigDecimal("2.5"), 0, 3, CFG);
    // 2.5 - 0.14 = 2.36 → scale=3 → 2.360
    assertThat(r.nextEaseFactor()).isEqualByComparingTo("2.36");
    assertThat(r.nextIntervalDays()).isEqualTo(1); // first review rule
  }

  @Test
  @DisplayName("ease=2.3 · interval=1 · q=5 → ease 2.4 · next=round(1*2.4)=2")
  void scenario_ease_gradual_climb() {
    SM2Result r = SM2Algorithm.compute(new BigDecimal("2.3"), 1, 5, CFG);
    assertThat(r.nextEaseFactor()).isEqualByComparingTo("2.4");
    assertThat(r.nextIntervalDays()).isEqualTo(2);
  }

  @Test
  @DisplayName("ease=2.45 · q=5 · rounding HALF_UP · ease=2.5 (cap)")
  void ease_rounding_half_up() {
    SM2Result r = SM2Algorithm.compute(new BigDecimal("2.45"), 1, 5, CFG);
    // 2.45 + 0.1 = 2.55 → clamp 2.5
    assertThat(r.nextEaseFactor()).isEqualByComparingTo("2.5");
  }

  @Test
  @DisplayName("result record equality works")
  void sm2_result_record_equality() {
    SM2Result a = new SM2Result(new BigDecimal("2.5"), 1);
    SM2Result b = new SM2Result(new BigDecimal("2.5"), 1);
    assertThat(a).isEqualTo(b);
  }

  @Test
  @DisplayName("config defaults have expected values")
  void config_defaults_sanity() {
    AlgorithmConfig c = AlgorithmConfig.defaults();
    assertThat(c.easeInit()).isEqualByComparingTo("2.5");
    assertThat(c.easeMin()).isEqualByComparingTo("1.3");
    assertThat(c.easeMax()).isEqualByComparingTo("2.5");
    assertThat(c.intervalMaxDays()).isEqualTo(60);
  }

  @Test
  @DisplayName("nextInterval floor = 1 · never 0 after quality≥3")
  void next_interval_never_zero() {
    SM2Result r = SM2Algorithm.compute(new BigDecimal("1.3"), 0, 3, CFG);
    assertThat(r.nextIntervalDays()).isGreaterThanOrEqualTo(1);
  }
}
