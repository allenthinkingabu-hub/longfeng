package com.longfeng.reviewplan.exception;

import com.longfeng.common.dto.ApiResult;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * S5 专用 exception handler · 映射 {@link PlanNotFoundException}, {@link PlanMasteredException},
 * {@link OptimisticLockingFailureException} 到 HTTP 状态码 · 不与 common {@code
 * GlobalExceptionHandler} 冲突（通过 scope 区分）。
 */
@RestControllerAdvice(basePackages = "com.longfeng.reviewplan")
public class ReviewPlanExceptionHandler {

  @ExceptionHandler(PlanNotFoundException.class)
  public ResponseEntity<ApiResult<Void>> handleNotFound(PlanNotFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ApiResult.fail(40401, "PLAN_NOT_FOUND: " + e.planId()));
  }

  @ExceptionHandler(PlanMasteredException.class)
  public ResponseEntity<ApiResult<Void>> handleMastered(PlanMasteredException e) {
    return ResponseEntity.status(HttpStatus.GONE)
        .body(ApiResult.fail(41001, "PLAN_MASTERED: " + e.planId()));
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ResponseEntity<ApiResult<Void>> handleConflict(OptimisticLockingFailureException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ApiResult.fail(40901, "dispatch_version conflict (concurrent complete)"));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResult<Void>> handleValidation(MethodArgumentNotValidException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResult.fail(40001, "INVALID_QUALITY: " + e.getBindingResult().getAllErrors()));
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiResult<Void>> handleConstraint(ConstraintViolationException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResult.fail(40002, "INVALID_ARG: " + e.getMessage()));
  }

  @ExceptionHandler(InvalidRangeException.class)
  public ResponseEntity<ApiResult<Void>> handleInvalidRange(InvalidRangeException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResult.fail(40003, "INVALID_RANGE: " + e.range()));
  }
}
