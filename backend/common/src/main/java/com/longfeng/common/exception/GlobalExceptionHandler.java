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
 * Global exception handler for servlet (MVC) services · 落地计划 §6.6.
 *
 * <p>Only activates when {@code DispatcherServlet} is on the classpath (i.e. services using
 * {@code spring-boot-starter-web}). Gateway is WebFlux-only so this advice is silent there.
 */
@Configuration
@ConditionalOnClass(DispatcherServlet.class)
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /** Let Spring's own ResponseStatusException propagate with its intended HTTP status code. */
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ApiResult<Void>> handleResponseStatus(ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode())
        .body(ApiResult.fail(e.getStatusCode().value(), e.getReason() != null ? e.getReason() : e.getMessage()));
  }

  @ExceptionHandler(BizException.class)
  public ResponseEntity<ApiResult<Void>> handleBiz(BizException e) {
    log.warn("biz-error code={} httpStatus={} msg={}", e.code(), e.httpStatus(), e.getMessage());
    return ResponseEntity.status(e.httpStatus()).body(ApiResult.fail(e.code(), e.getMessage()));
  }

  @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
  public ResponseEntity<ApiResult<Void>> handleValidation(Exception e) {
    log.warn("validation-error msg={}", e.getMessage());
    return ResponseEntity.status(ErrorCode.VALIDATION_400.httpStatus())
        .body(ApiResult.fail(ErrorCode.VALIDATION_400.code(), e.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResult<Void>> handleAny(Exception e) {
    log.error("internal-error msg={}", e.getMessage(), e);
    return ResponseEntity.status(ErrorCode.INTERNAL_500.httpStatus())
        .body(ApiResult.fail(ErrorCode.INTERNAL_500.code(), ErrorCode.INTERNAL_500.message()));
  }
}
