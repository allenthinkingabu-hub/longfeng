package com.longfeng.common.config;

/**
 * MDC logging contract marker · 落地计划 §6.6.
 *
 * <p>No runtime bean — this class documents the required slf4j MDC keys so services / ops can
 * align logback pattern layouts. Actual pattern lives in each module's {@code logback-spring.xml}
 * when logging format becomes a S9 concern.
 *
 * <p>MDC keys populated by {@link com.longfeng.common.filter.TraceIdFilter}:
 * <ul>
 *   <li>{@code traceId} — propagated via {@code X-Trace-Id} header, 36-char UUID</li>
 * </ul>
 *
 * <p>Recommended pattern fragment:
 * {@code %d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%X{traceId:-}] %logger{36} - %msg%n}
 */
public final class MdcLoggingConfig {

  public static final String TRACE_ID_KEY = "traceId";

  private MdcLoggingConfig() {}
}
