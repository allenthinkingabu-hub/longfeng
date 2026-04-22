package com.longfeng.common.exception;

/**
 * Business exception · 落地计划 §6.6.
 *
 * <p>Throw this (never {@link RuntimeException}) when business invariants fail.
 * {@link GlobalExceptionHandler} maps it to HTTP status + {@link com.longfeng.common.dto.ApiResult}
 * with the stored {@link ErrorCode#code()} as the JSON {@code code}.
 */
public class BizException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final ErrorCode errorCode;
  private final int httpStatus;
  private final int code;

  public BizException(ErrorCode errorCode) {
    this(errorCode, errorCode.message());
  }

  public BizException(ErrorCode errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
    this.httpStatus = errorCode.httpStatus();
    this.code = errorCode.code();
  }

  public ErrorCode errorCode() {
    return errorCode;
  }

  public int httpStatus() {
    return httpStatus;
  }

  public int code() {
    return code;
  }
}
