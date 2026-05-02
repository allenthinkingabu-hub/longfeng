package com.longfeng.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.longfeng.gateway.tmp.UserContextHolder;
import com.longfeng.gateway.tmp.UserScope;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit tests for {@link AnonFilter}.
 */
class AnonFilterTest {

  private AnonFilter filter;
  private RateLimiterRegistry registry;

  @BeforeEach
  void setUp() {
    RateLimiterConfig cfg =
        RateLimiterConfig.custom()
            .limitForPeriod(100)
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .timeoutDuration(Duration.ZERO)
            .build();
    registry = RateLimiterRegistry.of(cfg);
    registry.addConfiguration("anon-guest", cfg);
    registry.addConfiguration("anon-guest-fp", cfg);
    filter = new AnonFilter(registry);
  }

  @Test
  void requestWithAuthHeader_skipsByFilter() {
    MockServerHttpRequest request =
        MockServerHttpRequest.get("/api/wb/questions")
            .header(HttpHeaders.AUTHORIZATION, "Bearer sometoken")
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    GatewayFilterChain chain = ex -> {
      // scope should NOT be set by AnonFilter when Authorization header present
      return Mono.empty();
    };

    StepVerifier.create(filter.filter(exchange, chain))
        .verifyComplete();

    // AnonFilter should NOT set scope when auth header is present
    assertThat((Object) exchange.getAttribute(UserContextHolder.ATTR_SCOPE)).isNull();
  }

  @Test
  void requestWithoutAuthHeader_setsGuestScope() {
    MockServerHttpRequest request =
        MockServerHttpRequest.get("/api/landing/home").build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    final ServerWebExchange[] passedExchange = {null};
    GatewayFilterChain chain = ex -> {
      passedExchange[0] = ex;
      return Mono.empty();
    };

    StepVerifier.create(filter.filter(exchange, chain))
        .verifyComplete();

    assertThat((Object) exchange.getAttribute(UserContextHolder.ATTR_SCOPE)).isEqualTo(UserScope.GUEST);
    assertThat((Object) exchange.getAttribute(AnonFilter.ATTR_ANON_HANDLED)).isEqualTo(Boolean.TRUE);
  }

  @Test
  void actuatorPath_passesWithoutScopeSet() {
    MockServerHttpRequest request =
        MockServerHttpRequest.get("/actuator/health").build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    GatewayFilterChain chain = ex -> Mono.empty();

    StepVerifier.create(filter.filter(exchange, chain))
        .verifyComplete();

    // Actuator paths are never touched by AnonFilter
    assertThat((Object) exchange.getAttribute(UserContextHolder.ATTR_SCOPE)).isNull();
  }

  @Test
  void anonRequest_propagatesXUserScopeHeader() {
    MockServerHttpRequest request =
        MockServerHttpRequest.get("/api/guest/session").build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    final ServerWebExchange[] passedExchange = {null};
    GatewayFilterChain chain = ex -> {
      passedExchange[0] = ex;
      return Mono.empty();
    };

    StepVerifier.create(filter.filter(exchange, chain))
        .verifyComplete();

    assertThat(passedExchange[0]).isNotNull();
    assertThat(passedExchange[0].getRequest().getHeaders().getFirst("X-User-Scope"))
        .isEqualTo("GUEST");
  }

  @Test
  void rateLimitExceeded_returns429() {
    // Create a registry where anon-guest config allows only 1 permit
    RateLimiterConfig tightCfg =
        RateLimiterConfig.custom()
            .limitForPeriod(1)
            .limitRefreshPeriod(Duration.ofMinutes(1))
            .timeoutDuration(Duration.ZERO)
            .build();
    RateLimiterRegistry tightRegistry = RateLimiterRegistry.of(tightCfg);
    tightRegistry.addConfiguration("anon-guest", tightCfg);
    tightRegistry.addConfiguration("anon-guest-fp", tightCfg);
    AnonFilter tightFilter = new AnonFilter(tightRegistry);

    // First request — consumes the 1 permit
    MockServerHttpRequest req1 =
        MockServerHttpRequest.get("/api/landing/").remoteAddress(new InetSocketAddress("1.2.3.4", 0)).build();
    MockServerWebExchange ex1 = MockServerWebExchange.from(req1);
    GatewayFilterChain chain = e -> Mono.empty();
    StepVerifier.create(tightFilter.filter(ex1, chain)).verifyComplete();

    // Second request — rate limit exceeded → 429
    MockServerHttpRequest req2 =
        MockServerHttpRequest.get("/api/landing/").remoteAddress(new InetSocketAddress("1.2.3.4", 0)).build();
    MockServerWebExchange ex2 = MockServerWebExchange.from(req2);
    StepVerifier.create(tightFilter.filter(ex2, chain)).verifyComplete();

    assertThat(ex2.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
  }

  @Test
  void filterOrder_isFirstInAuthChain() {
    assertThat(filter.getOrder()).isEqualTo(AnonFilter.ORDER);
    assertThat(AnonFilter.ORDER).isLessThan(ShareFilter.ORDER);
    assertThat(AnonFilter.ORDER).isLessThan(ObserverFilter.ORDER);
    assertThat(AnonFilter.ORDER).isLessThan(AuthFilter.ORDER);
    assertThat(AnonFilter.ORDER).isLessThan(JwtAuthFilter.ORDER);
  }
}
