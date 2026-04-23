package com.longfeng.wrongbook.controller;

import com.longfeng.common.dto.ApiResult;
import com.longfeng.common.exception.ErrorCode;
import com.longfeng.wrongbook.service.WrongItemService;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * wrongbook-specific advice layered above common's handler — maps domain 404 and JPA optimistic
 * locking to the right HTTP status (§7.7 Step 10).
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WrongbookExceptionHandler {

  @ExceptionHandler(WrongItemService.NotFoundException.class)
  public ResponseEntity<ApiResult<Void>> handleNotFound(WrongItemService.NotFoundException e) {
    return ResponseEntity.status(ErrorCode.NOT_FOUND_404.httpStatus())
        .body(ApiResult.fail(ErrorCode.NOT_FOUND_404.code(), e.getMessage()));
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  public ResponseEntity<ApiResult<Void>> handleOptimistic(
      ObjectOptimisticLockingFailureException e) {
    return ResponseEntity.status(ErrorCode.CONFLICT_409.httpStatus())
        .body(
            ApiResult.fail(
                ErrorCode.CONFLICT_409.code(),
                "version mismatch — concurrent update detected"));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ApiResult<Void>> handleIllegalState(IllegalStateException e) {
    return ResponseEntity.status(ErrorCode.VALIDATION_400.httpStatus())
        .body(ApiResult.fail(ErrorCode.VALIDATION_400.code(), e.getMessage()));
  }
}
