package com.longfeng.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.longfeng.common.dto.ApiResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * Unit tests for {@link GlobalExceptionHandler} · TDD §3.1 / §14.2 (100% errcode coverage) / plan §5.S0 BE-01.
 *
 * <p>Tests the handler directly (without Spring MVC) to keep tests fast and dependency-light.
 * Each of the 22 ErrCodes gets at least one happy-path test.
 */
class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  // ── BusinessException: all 22 ErrCode entries ────────────────────────

  @ParameterizedTest
  @EnumSource(ErrCode.class)
  void handleBusiness_allErrCodes_returnsCorrectHttpStatusAndCode(ErrCode errCode) {
    BusinessException ex = new BusinessException(errCode);

    ResponseEntity<ApiResult<Void>> response = handler.handleBusiness(ex);

    assertThat(response.getStatusCode().value()).isEqualTo(errCode.httpStatus());
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo(errCode.code());
    assertThat(response.getBody().message()).startsWith(BusinessException.MSGKEY_PREFIX);
  }

  // ── Specific ErrCode assertions (22 individual tests) ─────────────────

  @Test
  void handleBusiness_validationFailed_returns400With40001() {
    BusinessException ex = new BusinessException(ErrCode.VALIDATION_FAILED);
    ResponseEntity<ApiResult<Void>> resp = handler.handleBusiness(ex);

    assertThat(resp.getStatusCode().value()).isEqualTo(400);
    assertThat(resp.getBody().code()).isEqualTo(40001);
  }

  @Test
  void handleBusiness_unauthorized_returns401With40101() {
    BusinessException ex = new BusinessException(ErrCode.UNAUTHORIZED);
    ResponseEntity<ApiResult<Void>> resp = handler.handleBusiness(ex);

    assertThat(resp.getStatusCode().value()).isEqualTo(401);
    assertThat(resp.getBody().code()).isEqualTo(40101);
  }

  @Test
  void handleBusiness_deviceMismatch_returns403With40301() {
    BusinessException ex = new BusinessException(ErrCode.DEVICE_MISMATCH);
    ResponseEntity<ApiResult<Void>> resp = handler.handleBusiness(ex);

    assertThat(resp.getStatusCode().value()).isEqualTo(403);
    assertThat(resp.getBody().code()).isEqualTo(40301);
  }

  @Test
  void handleBusiness_anonymousWriteForbidden_returns403With40302() {
    BusinessException ex = new BusinessException(ErrCode.ANONYMOUS_WRITE_FORBIDDEN);
    ResponseEntity<ApiResult<Void>> resp = handler.handleBusiness(ex);

    assertThat(resp.getStatusCode().value()).isEqualTo(403);
    assertThat(resp.getBody().code()).isEqualTo(40302);
  }

  @Test
  void handleBusiness_observerForbiddenWrite_returns403With40303() {
    BusinessException ex = new BusinessException(ErrCode.OBSERVER_FORBIDDEN_WRITE);
    ResponseEntity<ApiResult<Void>> resp = handler.handleBusiness(ex);

    assertThat(resp.getStatusCode().value()).isEqualTo(403);
    assertThat(resp.getBody().code()).isEqualTo(40303);
  }

  @Test
  void handleBusiness_observerRevoked_returns403With40304() {
    BusinessException ex = new BusinessException(ErrCode.OBSERVER_REVOKED);
    ResponseEntity<ApiResult<Void>> resp = handler.handleBusiness(ex);

    assertThat(resp.getStatusCode().value()).isEqualTo(403);
    assertThat(resp.getBody().code()).isEqualTo(40304);
  }

  @Test
  void handleBusiness_nonVipModelDenied_returns403With40305() {
    BusinessException ex = new BusinessException(ErrCode.NON_VIP_MODEL_DENIED);
    ResponseEntity<ApiResult<Void>> resp = handler.handleBusiness(ex);

    assertThat(resp.getStatusCode().value()).isEqualTo(403);
    assertThat(resp.getBody().code()).isEqualTo(40305);
  }

  @Test
  void handleBusiness_modelNotInCatalog_returns400With40306() {
    BusinessException ex = new BusinessException(ErrCode.MODEL_NOT_IN_CATALOG);
    ResponseEntity<ApiResult<Void>> resp = handler.handleBusiness(ex);

    assertThat(resp.getStatusCode().value()).isEqualTo(400);
    assertThat(resp.getBody().code()).isEqualTo(40306);
  }

  @Test
  void handleBusiness_notFound_returns404With40401() {
    BusinessException ex = new BusinessException(ErrCode.NOT_FOUND);
    ResponseEntity<ApiResult<Void>> resp = handler.handleBusiness(ex);

    assertThat(resp.getStatusCode().value()).isEqualTo(404);
    assertThat(resp.getBody().code()).isEqualTo(40401);
  }

  @Test
  void handleBusiness_guestAlreadyClaimed_returns409With40901() {
    BusinessException ex = new BusinessException(ErrCode.GUEST_ALREADY_CLAIMED);
    ResponseEntity<ApiResult<Void>> resp = handler.handleBusiness(ex);

    assertThat(resp.getStatusCode().value()).isEqualTo(409);
    assertThat(resp.getBody().code()).isEqualTo(40901);
  }

  @Test
  void handleBusiness_guestSessionExpired_returns410With41001() {
    BusinessException ex = new BusinessException(ErrCode.GUEST_SESSION_EXPIRED);
    ResponseEntity<ApiResult<Void>> resp = handler.handleBusiness(ex);

    assertThat(resp.getStatusCode().value()).isEqualTo(410);
    assertThat(resp.getBody().code()).isEqualTo(41001);
  }

  @Test
  void handleBusiness_tokenExpired_returns410With41002() {
    BusinessException ex = new BusinessException(ErrCode.TOKEN_EXPIRED);
    ResponseEntity<ApiResult<Void>> resp = handler.handleBusiness(ex);

    assertThat(resp.getStatusCode().value()).isEqualTo(410);
    assertThat(resp.getBody().code()).isEqualTo(41002);
  }

  @Test
  void handleBusiness_aiRateLimit_returns429With42901() {
    BusinessException ex = new BusinessException(ErrCode.AI_RATE_LIMIT);
    ResponseEntity<ApiResult<Void>> resp = handler.handleBusiness(ex);

    assertThat(resp.getStatusCode().value()).isEqualTo(429);
    assertThat(resp.getBody().code()).isEqualTo(42901);
  }

  @Test
  void handleBusiness_guestQuotaExhausted_returns429With42902() {
    BusinessException ex = new BusinessException(ErrCode.GUEST_QUOTA_EXHAUSTED);
    ResponseEntity<ApiResult<Void>> resp = handler.handleBusiness(ex);

    assertThat(resp.getStatusCode().value()).isEqualTo(429);
    assertThat(resp.getBody().code()).isEqualTo(42902);
  }

  @Test
  void handleBusiness_landingRateLimit_returns429With42903() {
    BusinessException ex = new BusinessException(ErrCode.LANDING_RATE_LIMIT);
    ResponseEntity<ApiResult<Void>> resp = handler.handleBusiness(ex);

    assertThat(resp.getStatusCode().value()).isEqualTo(429);
    assertThat(resp.getBody().code()).isEqualTo(42903);
  }

  @Test
  void handleBusiness_aiProviderUnavailable_returns500With50010() {
    BusinessException ex = new BusinessException(ErrCode.AI_PROVIDER_UNAVAILABLE);
    ResponseEntity<ApiResult<Void>> resp = handler.handleBusiness(ex);

    assertThat(resp.getStatusCode().value()).isEqualTo(500);
    assertThat(resp.getBody().code()).isEqualTo(50010);
  }

  @Test
  void handleBusiness_aiJsonParseFailed_returns500With50011() {
    BusinessException ex = new BusinessException(ErrCode.AI_JSON_PARSE_FAILED);
    ResponseEntity<ApiResult<Void>> resp = handler.handleBusiness(ex);

    assertThat(resp.getStatusCode().value()).isEqualTo(500);
    assertThat(resp.getBody().code()).isEqualTo(50011);
  }

  @Test
  void handleBusiness_promptInjectionDetected_returns500With50012() {
    BusinessException ex = new BusinessException(ErrCode.PROMPT_INJECTION_DETECTED);
    ResponseEntity<ApiResult<Void>> resp = handler.handleBusiness(ex);

    assertThat(resp.getStatusCode().value()).isEqualTo(500);
    assertThat(resp.getBody().code()).isEqualTo(50012);
  }

  @Test
  void handleBusiness_ossCallbackSignFailed_returns500With50020() {
    BusinessException ex = new BusinessException(ErrCode.OSS_CALLBACK_SIGN_FAILED);
    ResponseEntity<ApiResult<Void>> resp = handler.handleBusiness(ex);

    assertThat(resp.getStatusCode().value()).isEqualTo(500);
    assertThat(resp.getBody().code()).isEqualTo(50020);
  }

  @Test
  void handleBusiness_ebbinghausNodeExpired_returns500With50030() {
    BusinessException ex = new BusinessException(ErrCode.EBBINGHAUS_NODE_EXPIRED);
    ResponseEntity<ApiResult<Void>> resp = handler.handleBusiness(ex);

    assertThat(resp.getStatusCode().value()).isEqualTo(500);
    assertThat(resp.getBody().code()).isEqualTo(50030);
  }

  @Test
  void handleBusiness_nodeInvalidTransition_returns500With50031() {
    BusinessException ex = new BusinessException(ErrCode.NODE_INVALID_TRANSITION);
    ResponseEntity<ApiResult<Void>> resp = handler.handleBusiness(ex);

    assertThat(resp.getStatusCode().value()).isEqualTo(500);
    assertThat(resp.getBody().code()).isEqualTo(50031);
  }

  @Test
  void handleBusiness_internalError_returns500With50000() {
    BusinessException ex = new BusinessException(ErrCode.INTERNAL_ERROR);
    ResponseEntity<ApiResult<Void>> resp = handler.handleBusiness(ex);

    assertThat(resp.getStatusCode().value()).isEqualTo(500);
    assertThat(resp.getBody().code()).isEqualTo(50000);
  }

  // ── Validation exception handler ─────────────────────────────────────

  @Test
  void handleValidation_constraintViolation_returns400With40001() {
    // Use ConstraintViolationException (simpler to construct) to test validation handler path
    jakarta.validation.ConstraintViolationException ex =
        new jakarta.validation.ConstraintViolationException("field must not be blank",
            java.util.Collections.emptySet());

    ResponseEntity<ApiResult<Void>> resp = handler.handleValidation(ex);

    assertThat(resp.getStatusCode().value()).isEqualTo(400);
    assertThat(resp.getBody().code()).isEqualTo(40001);
    assertThat(resp.getBody().message()).startsWith(BusinessException.MSGKEY_PREFIX);
  }

  // ── Fallback handler ──────────────────────────────────────────────────

  @Test
  void handleAny_unhandledException_returns500With50000() {
    RuntimeException ex = new RuntimeException("unexpected");

    ResponseEntity<ApiResult<Void>> resp = handler.handleAny(ex);

    assertThat(resp.getStatusCode().value()).isEqualTo(500);
    assertThat(resp.getBody().code()).isEqualTo(50000);
    assertThat(resp.getBody().message()).startsWith(BusinessException.MSGKEY_PREFIX);
  }

  // ── ResponseStatusException handler ──────────────────────────────────

  @Test
  void handleResponseStatus_propagatesStatus() {
    ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_FOUND, "not found");

    ResponseEntity<ApiResult<Void>> resp = handler.handleResponseStatus(ex);

    assertThat(resp.getStatusCode().value()).isEqualTo(404);
  }

  // ── Response body structure ────────────────────────────────────────────

  @Test
  void handleBusiness_responseBodyHasMsgkeyMessage() {
    BusinessException ex = new BusinessException(ErrCode.NOT_FOUND,
        "msgkey:resource.not.found");

    ResponseEntity<ApiResult<Void>> resp = handler.handleBusiness(ex);

    assertThat(resp.getBody().message()).isEqualTo("msgkey:resource.not.found");
    assertThat(resp.getBody().data()).isNull();
  }

  // ── Legacy BizException handler ───────────────────────────────────────

  @Test
  void handleLegacyBiz_withNonMsgkeyMessage_fallsBackToInternalMsgkey() {
    BizException ex = new BizException(ErrorCode.INTERNAL_500, "some internal message");

    ResponseEntity<ApiResult<Void>> resp = handler.handleLegacyBiz(ex);

    assertThat(resp.getBody().message()).startsWith(BusinessException.MSGKEY_PREFIX);
  }
}
