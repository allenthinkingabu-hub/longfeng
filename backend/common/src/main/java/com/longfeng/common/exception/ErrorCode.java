package com.longfeng.common.exception;

/**
 * Business error code enum · 落地计划 §6.6.
 *
 * <p>Code layout: HTTP-status * 100 + business-sub-code. Keeps the mapping between {@code code} in
 * {@link com.longfeng.common.dto.ApiResult} and HTTP status explicit.
 */
public enum ErrorCode {
  AUTH_401(40101, 401, "UNAUTHORIZED"),
  FORBIDDEN_403(40301, 403, "FORBIDDEN"),
  VALIDATION_400(40001, 400, "VALIDATION_FAILED"),
  NOT_FOUND_404(40401, 404, "NOT_FOUND"),
  CONFLICT_409(40901, 409, "CONFLICT"),
  RATE_429(42901, 429, "RATE_LIMITED"),
  INTERNAL_500(50000, 500, "INTERNAL_ERROR");

  private final int code;
  private final int httpStatus;
  private final String message;

  ErrorCode(int code, int httpStatus, String message) {
    this.code = code;
    this.httpStatus = httpStatus;
    this.message = message;
  }

  public int code() {
    return code;
  }

  public int httpStatus() {
    return httpStatus;
  }

  public String message() {
    return message;
  }
}
