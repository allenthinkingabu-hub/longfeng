package com.longfeng.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Unit tests for {@link ErrCode} · TDD §B (22-entry registry) / plan §5.S0 BE-01.
 *
 * <p>Covers:
 * <ul>
 *   <li>Exactly 22 enum values (not more, not less — per spec)</li>
 *   <li>All entries have msgkey prefix</li>
 *   <li>HTTP status codes are sane (400/401/403/404/409/410/429/500)</li>
 *   <li>All 22 specific codes from TDD §B exist with correct values</li>
 * </ul>
 */
class ErrCodeTest {

  @Test
  void errCode_hasExactly22Entries() {
    assertThat(ErrCode.values()).hasSize(22);
  }

  @ParameterizedTest
  @EnumSource(ErrCode.class)
  void allEntries_defaultMsgkeyStartsWithMsgkeyPrefix(ErrCode errCode) {
    assertThat(errCode.defaultMsgkey())
        .as("%s.defaultMsgkey()", errCode.name())
        .startsWith("msgkey:");
  }

  @ParameterizedTest
  @EnumSource(ErrCode.class)
  void allEntries_httpStatusIsSane(ErrCode errCode) {
    int status = errCode.httpStatus();
    assertThat(status)
        .as("%s.httpStatus()", errCode.name())
        .isIn(400, 401, 403, 404, 409, 410, 429, 500);
  }

  @ParameterizedTest
  @EnumSource(ErrCode.class)
  void allEntries_codeIsPositive(ErrCode errCode) {
    assertThat(errCode.code())
        .as("%s.code()", errCode.name())
        .isPositive();
  }

  // ── Specific code values from TDD §B ─────────────────────────────────

  @Test
  void validationFailed_codeIs40001_httpIs400() {
    assertThat(ErrCode.VALIDATION_FAILED.code()).isEqualTo(40001);
    assertThat(ErrCode.VALIDATION_FAILED.httpStatus()).isEqualTo(400);
  }

  @Test
  void unauthorized_codeIs40101_httpIs401() {
    assertThat(ErrCode.UNAUTHORIZED.code()).isEqualTo(40101);
    assertThat(ErrCode.UNAUTHORIZED.httpStatus()).isEqualTo(401);
  }

  @Test
  void deviceMismatch_codeIs40301_httpIs403() {
    assertThat(ErrCode.DEVICE_MISMATCH.code()).isEqualTo(40301);
    assertThat(ErrCode.DEVICE_MISMATCH.httpStatus()).isEqualTo(403);
  }

  @Test
  void anonymousWriteForbidden_codeIs40302_httpIs403() {
    assertThat(ErrCode.ANONYMOUS_WRITE_FORBIDDEN.code()).isEqualTo(40302);
    assertThat(ErrCode.ANONYMOUS_WRITE_FORBIDDEN.httpStatus()).isEqualTo(403);
  }

  @Test
  void observerForbiddenWrite_codeIs40303_httpIs403() {
    assertThat(ErrCode.OBSERVER_FORBIDDEN_WRITE.code()).isEqualTo(40303);
    assertThat(ErrCode.OBSERVER_FORBIDDEN_WRITE.httpStatus()).isEqualTo(403);
  }

  @Test
  void observerRevoked_codeIs40304_httpIs403() {
    assertThat(ErrCode.OBSERVER_REVOKED.code()).isEqualTo(40304);
    assertThat(ErrCode.OBSERVER_REVOKED.httpStatus()).isEqualTo(403);
  }

  @Test
  void nonVipModelDenied_codeIs40305_httpIs403() {
    assertThat(ErrCode.NON_VIP_MODEL_DENIED.code()).isEqualTo(40305);
    assertThat(ErrCode.NON_VIP_MODEL_DENIED.httpStatus()).isEqualTo(403);
  }

  @Test
  void modelNotInCatalog_codeIs40306_httpIs400() {
    assertThat(ErrCode.MODEL_NOT_IN_CATALOG.code()).isEqualTo(40306);
    assertThat(ErrCode.MODEL_NOT_IN_CATALOG.httpStatus()).isEqualTo(400);
  }

  @Test
  void notFound_codeIs40401_httpIs404() {
    assertThat(ErrCode.NOT_FOUND.code()).isEqualTo(40401);
    assertThat(ErrCode.NOT_FOUND.httpStatus()).isEqualTo(404);
  }

  @Test
  void guestAlreadyClaimed_codeIs40901_httpIs409() {
    assertThat(ErrCode.GUEST_ALREADY_CLAIMED.code()).isEqualTo(40901);
    assertThat(ErrCode.GUEST_ALREADY_CLAIMED.httpStatus()).isEqualTo(409);
  }

  @Test
  void guestSessionExpired_codeIs41001_httpIs410() {
    assertThat(ErrCode.GUEST_SESSION_EXPIRED.code()).isEqualTo(41001);
    assertThat(ErrCode.GUEST_SESSION_EXPIRED.httpStatus()).isEqualTo(410);
  }

  @Test
  void tokenExpired_codeIs41002_httpIs410() {
    assertThat(ErrCode.TOKEN_EXPIRED.code()).isEqualTo(41002);
    assertThat(ErrCode.TOKEN_EXPIRED.httpStatus()).isEqualTo(410);
  }

  @Test
  void aiRateLimit_codeIs42901_httpIs429() {
    assertThat(ErrCode.AI_RATE_LIMIT.code()).isEqualTo(42901);
    assertThat(ErrCode.AI_RATE_LIMIT.httpStatus()).isEqualTo(429);
  }

  @Test
  void guestQuotaExhausted_codeIs42902_httpIs429() {
    assertThat(ErrCode.GUEST_QUOTA_EXHAUSTED.code()).isEqualTo(42902);
    assertThat(ErrCode.GUEST_QUOTA_EXHAUSTED.httpStatus()).isEqualTo(429);
  }

  @Test
  void landingRateLimit_codeIs42903_httpIs429() {
    assertThat(ErrCode.LANDING_RATE_LIMIT.code()).isEqualTo(42903);
    assertThat(ErrCode.LANDING_RATE_LIMIT.httpStatus()).isEqualTo(429);
  }

  @Test
  void aiProviderUnavailable_codeIs50010_httpIs500() {
    assertThat(ErrCode.AI_PROVIDER_UNAVAILABLE.code()).isEqualTo(50010);
    assertThat(ErrCode.AI_PROVIDER_UNAVAILABLE.httpStatus()).isEqualTo(500);
  }

  @Test
  void aiJsonParseFailed_codeIs50011_httpIs500() {
    assertThat(ErrCode.AI_JSON_PARSE_FAILED.code()).isEqualTo(50011);
    assertThat(ErrCode.AI_JSON_PARSE_FAILED.httpStatus()).isEqualTo(500);
  }

  @Test
  void promptInjectionDetected_codeIs50012_httpIs500() {
    assertThat(ErrCode.PROMPT_INJECTION_DETECTED.code()).isEqualTo(50012);
    assertThat(ErrCode.PROMPT_INJECTION_DETECTED.httpStatus()).isEqualTo(500);
  }

  @Test
  void ossCallbackSignFailed_codeIs50020_httpIs500() {
    assertThat(ErrCode.OSS_CALLBACK_SIGN_FAILED.code()).isEqualTo(50020);
    assertThat(ErrCode.OSS_CALLBACK_SIGN_FAILED.httpStatus()).isEqualTo(500);
  }

  @Test
  void ebbinghausNodeExpired_codeIs50030_httpIs500() {
    assertThat(ErrCode.EBBINGHAUS_NODE_EXPIRED.code()).isEqualTo(50030);
    assertThat(ErrCode.EBBINGHAUS_NODE_EXPIRED.httpStatus()).isEqualTo(500);
  }

  @Test
  void nodeInvalidTransition_codeIs50031_httpIs500() {
    assertThat(ErrCode.NODE_INVALID_TRANSITION.code()).isEqualTo(50031);
    assertThat(ErrCode.NODE_INVALID_TRANSITION.httpStatus()).isEqualTo(500);
  }

  @Test
  void internalError_codeIs50000_httpIs500() {
    assertThat(ErrCode.INTERNAL_ERROR.code()).isEqualTo(50000);
    assertThat(ErrCode.INTERNAL_ERROR.httpStatus()).isEqualTo(500);
  }
}
