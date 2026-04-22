package com.longfeng.common.context;

/**
 * ThreadLocal tenant / user / trace context · 落地计划 §6.6.
 *
 * <p>Filter chain (servlet TraceIdFilter + gateway JwtAuthFilter) populates; business code reads;
 * web layer MUST call {@link #clear()} at request end to avoid thread pool leakage.
 */
public final class TenantContext {

  private static final ThreadLocal<String> TID = new ThreadLocal<>();
  private static final ThreadLocal<String> UID = new ThreadLocal<>();
  private static final ThreadLocal<String> TRACE = new ThreadLocal<>();

  private TenantContext() {}

  public static void set(String tenantId, String userId, String traceId) {
    TID.set(tenantId);
    UID.set(userId);
    TRACE.set(traceId);
  }

  public static void setTraceId(String traceId) {
    TRACE.set(traceId);
  }

  public static String tenant() {
    return TID.get();
  }

  public static String userId() {
    return UID.get();
  }

  public static String traceId() {
    return TRACE.get();
  }

  public static void clear() {
    TID.remove();
    UID.remove();
    TRACE.remove();
  }
}
