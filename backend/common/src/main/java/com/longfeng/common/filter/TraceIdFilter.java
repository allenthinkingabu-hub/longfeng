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
 * Servlet trace-id filter for downstream services · 落地计划 §6.6.
 *
 * <p>Runs only under servlet stacks (services, not gateway). Honours the incoming
 * {@code X-Trace-Id} header set by gateway; generates one on direct calls (eg probes). Writes to
 * both the response header and slf4j {@link MDC} so logs can be grepped by trace.
 */
@Configuration
@ConditionalOnClass(DispatcherServlet.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

  public static final String HEADER = "X-Trace-Id";
  public static final String MDC_KEY = "traceId";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String traceId = request.getHeader(HEADER);
    if (traceId == null || traceId.isBlank()) {
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
