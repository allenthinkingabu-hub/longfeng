package com.longfeng.common.dto;

import com.longfeng.common.context.TenantContext;

/**
 * Unified API response envelope · 落地计划 §6.6.
 *
 * <p>{@code code=0} means success. Business error codes use the form {@code 4XXYY} / {@code 5XXYY}
 * (see {@link com.longfeng.common.exception.ErrorCode}). {@code traceId} is populated from
 * {@link TenantContext} so clients can correlate with server logs.
 */
public record ApiResult<T>(int code, String message, T data, String traceId) {

  public static <T> ApiResult<T> ok(T data) {
    return new ApiResult<>(0, "OK", data, TenantContext.traceId());
  }

  public static <T> ApiResult<T> fail(int code, String message) {
    return new ApiResult<>(code, message, null, TenantContext.traceId());
  }
}
