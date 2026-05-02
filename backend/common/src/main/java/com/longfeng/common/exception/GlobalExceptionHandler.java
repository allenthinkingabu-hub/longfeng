package com.longfeng.common.exception;

import com.longfeng.common.dto.ApiResult;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * Global exception handler for servlet (MVC) services · TDD §3.1 / plan §5.S0 BE-01.
 *
 * <p>Only activates when {@code DispatcherServlet} is on the classpath (i.e. services using
 * {@code spring-boot-starter-web}). Gateway is WebFlux-only so this advice is silent there.
 *
 * <p>Handles all 22 {@link ErrCode} cases plus Spring framework exceptions. Every response
 * uses the unified {@link ApiResult} envelope {@code { code, message, data, traceId }}.
 *
 * <p>The {@code message} field is always a {@code msgkey:}-prefixed string (C8) so the frontend
 * can route it through i18n rendering.
 */
@Configuration
@ConditionalOnClass(DispatcherServlet.class)
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  // ── Primary handler: BusinessException (C8-compliant, msgkey guaranteed) ──

  /**
   * Handles {@link BusinessException} and all its sub-classes.
   * The message is guaranteed to carry a {@code msgkey:} prefix (enforced at construction time).
   */
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResult<Void>> handleBusiness(BusinessException e) {
    log.warn("business-error errCode={} httpStatus={} msg={}", e.errCode(), e.httpStatus(), e.getMessage());
    return ResponseEntity.status(e.httpStatus())
        .body(ApiResult.fail(e.code(), e.getMessage()));
  }

  // ── Legacy handler: BizException (kept for backward compat with pre-S0 code) ──

  /**
   * Handles legacy {@link BizException}. Wraps the message in a msgkey if it does not already
   * carry the prefix (graceful degradation — do not use for new code).
   *
   * @deprecated Use {@link BusinessException} instead.
   */
  @Deprecated
  @ExceptionHandler(BizException.class)
  public ResponseEntity<ApiResult<Void>> handleLegacyBiz(BizException e) {
    log.warn("legacy-biz-error code={} httpStatus={} msg={}", e.code(), e.httpStatus(), e.getMessage());
    String message = e.getMessage();
    if (message == null || !message.startsWith(BusinessException.MSGKEY_PREFIX)) {
      message = BusinessException.MSGKEY_PREFIX + "common.error.internal";
    }
    return ResponseEntity.status(e.httpStatus()).body(ApiResult.fail(e.code(), message));
  }

  // ── Spring framework exceptions ────────────────────────────────────────

  /** Let Spring's own {@link ResponseStatusException} propagate with its intended HTTP status. */
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ApiResult<Void>> handleResponseStatus(ResponseStatusException e) {
    String reason = e.getReason() != null ? e.getReason() : e.getMessage();
    String message = reason != null && reason.startsWith(BusinessException.MSGKEY_PREFIX)
        ? reason
        : BusinessException.MSGKEY_PREFIX + "common.error.http_error";
    return ResponseEntity.status(e.getStatusCode())
        .body(ApiResult.fail(e.getStatusCode().value() * 100, message));
  }

  /**
   * Handles Bean Validation failures (both {@code @RequestBody} and {@code @Validated} path/param).
   * Maps to {@link ErrCode#VALIDATION_FAILED} (40001 / HTTP 400).
   */
  @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
  public ResponseEntity<ApiResult<Void>> handleValidation(Exception e) {
    log.warn("validation-error msg={}", e.getMessage());
    return ResponseEntity.status(ErrCode.VALIDATION_FAILED.httpStatus())
        .body(ApiResult.fail(ErrCode.VALIDATION_FAILED.code(),
            ErrCode.VALIDATION_FAILED.defaultMsgkey()));
  }

  /**
   * Fallback handler for any unhandled exception.
   * Maps to {@link ErrCode#INTERNAL_ERROR} (50000 / HTTP 500).
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResult<Void>> handleAny(Exception e) {
    log.error("internal-error msg={}", e.getMessage(), e);
    return ResponseEntity.status(ErrCode.INTERNAL_ERROR.httpStatus())
        .body(ApiResult.fail(ErrCode.INTERNAL_ERROR.code(),
            ErrCode.INTERNAL_ERROR.defaultMsgkey()));
  }
}
