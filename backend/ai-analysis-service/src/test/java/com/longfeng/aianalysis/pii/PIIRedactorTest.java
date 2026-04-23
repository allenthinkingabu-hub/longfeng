package com.longfeng.aianalysis.pii;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * V-S4-04 backing test · asserts the three PII patterns go into literal placeholders. Does not
 * require DB — student-name lookup is bypassed when JdbcTemplate is null.
 */
class PIIRedactorTest {

  private final PIIRedactor redactor = new PIIRedactor(null);

  @Test
  void phoneNumberRedacted() {
    String out = redactor.redact("请联系 13800138000 张老师", null);
    assertThat(out).contains(PIIRedactor.PLACEHOLDER_PHONE).doesNotContain("13800138000");
  }

  @Test
  void idCardRedacted() {
    String out = redactor.redact("身份证 110101199003074612 请核验", null);
    assertThat(out).contains(PIIRedactor.PLACEHOLDER_IDCARD).doesNotContain("110101199003074612");
  }

  @Test
  void bothPatternsRedactedOnceEach() {
    String out =
        redactor.redact(
            "学生联系 13800138000 · 身份证 110101199003074612 · 另一条 110101199003074613",
            null);
    assertThat(out).doesNotContain("13800138000");
    assertThat(out).doesNotContain("110101199003074612");
    assertThat(out).doesNotContain("110101199003074613");
    assertThat(out).contains(PIIRedactor.PLACEHOLDER_PHONE);
    assertThat(out).contains(PIIRedactor.PLACEHOLDER_IDCARD);
  }

  @Test
  void nullAndEmptyUnchanged() {
    assertThat(redactor.redact(null, null)).isNull();
    assertThat(redactor.redact("", null)).isEmpty();
  }
}
