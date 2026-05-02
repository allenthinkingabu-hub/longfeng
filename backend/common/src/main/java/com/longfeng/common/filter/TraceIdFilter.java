package com.longfeng.common.filter;

import com.longfeng.common.context.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * Servlet trace-id filter for downstream services · TDD §3.1 / plan §5.S0 BE-01.
 *
 * <p>Runs only under servlet stacks (services, not gateway). Honours the incoming
 * {@code X-Trace-Id} header set by gateway; generates a new UUID-based trace-id on direct calls
 * (e.g. health probes, dev mode). Writes the trace-id to:
 * <ol>
 *   <li>the HTTP response header {@value #HEADER}</li>
 *   <li>SLF4J {@link MDC} under key {@value #MDC_KEY} for structured logs</li>
 *   <li>{@link TenantContext} for code that reads from context directly</li>
 * </ol>
 *
 * <p>Always clears MDC and context in the {@code finally} block to prevent thread-pool leakage.
 */
@Configuration
@ConditionalOnClass(DispatcherServlet.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

  /** HTTP request/response header carrying the trace identifier. */
  public static final String HEADER = "X-Trace-Id";

  /** MDC key for SLF4J structured logging. */
  public static final String MDC_KEY = "traceId";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String traceId = request.getHeader(HEADER);
    if (traceId == null || traceId.isBlank()) {
      // No upstream trace header — generate one (dev probe / direct call path)
      traceId = UUID.randomUUID().toString();
    }
    response.setHeader(HEADER, traceId);
    MDC.put(MDC_KEY, traceId);
    TenantContext.setTraceId(traceId);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_KEY);
      TenantContext.clear();
    }
  }
}
