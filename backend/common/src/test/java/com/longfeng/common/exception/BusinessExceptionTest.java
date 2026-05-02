package com.longfeng.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link BusinessException} · TDD §3.1 / C8 red-line / plan §5.S0 BE-01.
 *
 * <p>Covers:
 * <ul>
 *   <li>C8: msgkey prefix enforcement (missing prefix → IllegalArgumentException)</li>
 *   <li>Default msgkey from ErrCode</li>
 *   <li>Custom msgkey override</li>
 *   <li>ErrCode + cause construction</li>
 *   <li>httpStatus and code delegation to ErrCode</li>
 * </ul>
 */
class BusinessExceptionTest {

  // ── C8: msgkey prefix enforcement ─────────────────────────────────────

  @Test
  void constructWithDefaultMsgkey_usesErrCodeDefaultMsgkey() {
    BusinessException ex = new BusinessException(ErrCode.NOT_FOUND);

    assertThat(ex.getMessage()).startsWith(BusinessException.MSGKEY_PREFIX);
    assertThat(ex.getMessage()).isEqualTo(ErrCode.NOT_FOUND.defaultMsgkey());
    assertThat(ex.errCode()).isEqualTo(ErrCode.NOT_FOUND);
  }

  @Test
  void constructWithValidMsgkey_succeeds() {
    BusinessException ex = new BusinessException(ErrCode.VALIDATION_FAILED,
        "msgkey:test.custom.key");

    assertThat(ex.getMessage()).isEqualTo("msgkey:test.custom.key");
    assertThat(ex.errCode()).isEqualTo(ErrCode.VALIDATION_FAILED);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"no-prefix", "MSGKEY:wrong-case", "msg:almost", " msgkey:space-prefix"})
  void constructWithoutMsgkeyPrefix_throwsIllegalArgument(String badMessage) {
    assertThatThrownBy(() -> new BusinessException(ErrCode.INTERNAL_ERROR, badMessage))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("msgkey:");
  }

  @Test
  void constructWithCause_msgkeyEnforcedEvenWithCause() {
    RuntimeException cause = new RuntimeException("root cause");

    assertThatThrownBy(() ->
        new BusinessException(ErrCode.AI_PROVIDER_UNAVAILABLE, "no-prefix-here", cause))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void constructWithValidMsgkeyAndCause_succeeds() {
    RuntimeException cause = new RuntimeException("ai provider down");
    BusinessException ex = new BusinessException(ErrCode.AI_PROVIDER_UNAVAILABLE,
        "msgkey:ai.error.provider_unavailable", cause);

    assertThat(ex.getMessage()).isEqualTo("msgkey:ai.error.provider_unavailable");
    assertThat(ex.getCause()).isEqualTo(cause);
    assertThat(ex.errCode()).isEqualTo(ErrCode.AI_PROVIDER_UNAVAILABLE);
  }

  // ── ErrCode delegation ────────────────────────────────────────────────

  @Test
  void httpStatus_delegatesToErrCode() {
    BusinessException ex = new BusinessException(ErrCode.UNAUTHORIZED);
    assertThat(ex.httpStatus()).isEqualTo(401);
  }

  @Test
  void code_delegatesToErrCode() {
    BusinessException ex = new BusinessException(ErrCode.UNAUTHORIZED);
    assertThat(ex.code()).isEqualTo(40101);
  }

  @Test
  void errCode_notNull() {
    assertThatThrownBy(() -> new BusinessException(null))
        .isInstanceOf(NullPointerException.class);
  }

  // ── All 22 ErrCode entries are constructable ──────────────────────────

  @Test
  void allErrCodes_defaultMsgkeyStartsWithPrefix() {
    for (ErrCode code : ErrCode.values()) {
      assertThat(code.defaultMsgkey())
          .as("ErrCode %s defaultMsgkey must start with msgkey:", code.name())
          .startsWith(BusinessException.MSGKEY_PREFIX);
    }
  }

  @Test
  void allErrCodes_canBeUsedToConstructBusinessException() {
    for (ErrCode code : ErrCode.values()) {
      BusinessException ex = new BusinessException(code);
      assertThat(ex.getMessage()).startsWith(BusinessException.MSGKEY_PREFIX);
      assertThat(ex.errCode()).isEqualTo(code);
    }
  }

  // ── isInstanceOf RuntimeException (never checked) ─────────────────────

  @Test
  void businessException_isRuntimeException() {
    BusinessException ex = new BusinessException(ErrCode.INTERNAL_ERROR);
    assertThat(ex).isInstanceOf(RuntimeException.class);
  }
}
