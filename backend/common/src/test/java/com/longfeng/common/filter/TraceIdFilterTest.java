package com.longfeng.common.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.longfeng.common.context.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * Unit tests for {@link TraceIdFilter} · TDD §3.1 / plan §5.S0 BE-01.
 *
 * <p>Covers:
 * <ul>
 *   <li>Upstream has X-Trace-Id header → passes it through unchanged</li>
 *   <li>Upstream has no X-Trace-Id header → generates a new UUID-based trace-id</li>
 *   <li>MDC is set during filter execution and cleared after</li>
 *   <li>TenantContext is set during filter execution and cleared after</li>
 *   <li>Response header is set with the trace-id</li>
 * </ul>
 */
class TraceIdFilterTest {

  private final TraceIdFilter filter = new TraceIdFilter();

  @AfterEach
  void cleanup() {
    MDC.clear();
    TenantContext.clear();
  }

  // ── Path 1: upstream provides X-Trace-Id ────────────────────────────

  @Test
  void whenUpstreamProvidesTraceId_thenPropagatesItUnchanged() throws Exception {
    String upstreamTraceId = "upstream-trace-12345";
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    when(request.getHeader(TraceIdFilter.HEADER)).thenReturn(upstreamTraceId);

    filter.doFilterInternal(request, response, chain);

    // Response header must echo the upstream trace id
    verify(response).setHeader(TraceIdFilter.HEADER, upstreamTraceId);
    // FilterChain must be invoked
    verify(chain).doFilter(request, response);
  }

  @Test
  void whenUpstreamProvidesTraceId_mdcAndTenantContextAreSetDuringExecution() throws Exception {
    String upstreamTraceId = "trace-mdc-test";
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);

    when(request.getHeader(TraceIdFilter.HEADER)).thenReturn(upstreamTraceId);

    // Capture MDC and TenantContext state during chain execution
    String[] capturedMdc = new String[1];
    String[] capturedTenantTrace = new String[1];

    FilterChain capturingChain = (req, res) -> {
      capturedMdc[0] = MDC.get(TraceIdFilter.MDC_KEY);
      capturedTenantTrace[0] = TenantContext.traceId();
    };

    filter.doFilterInternal(request, response, capturingChain);

    assertThat(capturedMdc[0]).isEqualTo(upstreamTraceId);
    assertThat(capturedTenantTrace[0]).isEqualTo(upstreamTraceId);
  }

  // ── Path 2: upstream provides NO X-Trace-Id ─────────────────���────────

  @Test
  void whenNoUpstreamTraceId_thenGeneratesNewUuid() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    when(request.getHeader(TraceIdFilter.HEADER)).thenReturn(null);

    filter.doFilterInternal(request, response, chain);

    // Response header must be set with a generated trace id (we cannot predict UUID, just check non-null/non-blank)
    org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(response).setHeader(org.mockito.ArgumentMatchers.eq(TraceIdFilter.HEADER), captor.capture());
    assertThat(captor.getValue()).isNotBlank();
    // Should be a UUID-like value (32 hex chars + 4 dashes = 36)
    assertThat(captor.getValue()).hasSize(36);
  }

  @Test
  void whenBlankUpstreamTraceId_thenGeneratesNewUuid() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    when(request.getHeader(TraceIdFilter.HEADER)).thenReturn("   ");

    filter.doFilterInternal(request, response, chain);

    org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(response).setHeader(org.mockito.ArgumentMatchers.eq(TraceIdFilter.HEADER), captor.capture());
    assertThat(captor.getValue()).isNotBlank();
    assertThat(captor.getValue()).isNotEqualTo("   ");
  }

  @Test
  void whenNoUpstreamTraceId_mdcIsSetWithGeneratedId() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);

    when(request.getHeader(TraceIdFilter.HEADER)).thenReturn(null);

    String[] capturedMdc = new String[1];
    FilterChain capturingChain = (req, res) -> capturedMdc[0] = MDC.get(TraceIdFilter.MDC_KEY);

    filter.doFilterInternal(request, response, capturingChain);

    assertThat(capturedMdc[0]).isNotBlank();
  }

  // ── Cleanup after filter ───────────────────��──────────────────────────

  @Test
  void afterFilter_mdcIsCleared() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    when(request.getHeader(TraceIdFilter.HEADER)).thenReturn("cleanup-test");

    // Pre-condition: MDC is empty
    assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();

    filter.doFilterInternal(request, response, chain);

    // Post-condition: MDC must be cleared
    assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
  }

  @Test
  void afterFilter_tenantContextIsCleared() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    when(request.getHeader(TraceIdFilter.HEADER)).thenReturn("context-cleanup-test");

    filter.doFilterInternal(request, response, chain);

    assertThat(TenantContext.traceId()).isNull();
  }

  @Test
  void contextCleared_evenWhenChainThrows() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);

    when(request.getHeader(TraceIdFilter.HEADER)).thenReturn("throw-test");

    FilterChain throwingChain = (req, res) -> {
      throw new jakarta.servlet.ServletException("simulated exception");
    };

    try {
      filter.doFilterInternal(request, response, throwingChain);
    } catch (jakarta.servlet.ServletException ignored) {
      // Expected
    }

    // Context must be cleared even after exception
    assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
    assertThat(TenantContext.traceId()).isNull();
  }
}
