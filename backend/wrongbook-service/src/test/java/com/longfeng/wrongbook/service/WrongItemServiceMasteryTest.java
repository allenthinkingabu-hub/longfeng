package com.longfeng.wrongbook.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Unit tests for {@link WrongItemService#mapMastery(Short)}.
 * Verifies S7 Issue 2: mastery quantity 0-2 → 0-100 mapping.
 */
class WrongItemServiceMasteryTest {

  @ParameterizedTest(name = "mastery {0} → {1}")
  @CsvSource({
    "0, 0",
    "1, 50",
    "2, 100",
  })
  @DisplayName("S7 Issue 2 · mapMastery: internal 0-2 maps to 0-100")
  void mapMasteryScale(short input, int expected) {
    assertThat(WrongItemService.mapMastery(input)).isEqualTo(expected);
  }

  @Test
  @DisplayName("S7 Issue 2 · mapMastery: null mastery defaults to 0")
  void mapMasteryNull() {
    assertThat(WrongItemService.mapMastery(null)).isEqualTo(0);
  }
}
