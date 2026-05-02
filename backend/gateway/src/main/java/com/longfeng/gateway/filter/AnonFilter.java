package com.longfeng.gateway.filter;

import com.longfeng.gateway.tmp.ErrCode;
import com.longfeng.gateway.tmp.UserContextHolder;
import com.longfeng.gateway.tmp.UserScope;
import com.longfeng.gateway.util.GatewayResponseHelper;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * AnonFilter — anonymous traffic diversion (order = {@value #ORDER}, runs first in auth chain).
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>If the request carries NO {@code Authorization} header → mark scope=GUEST and forward.
 *   <li>Apply Bucket4j rate limiting on IP + {@code X-Device-Fp} (dual-dimension):
 *       <ul>
 *         <li>Single IP: {@code rate:guest:ip:{ip}} — 10/day ceiling enforced by Resilience4j
 *             (dev); production wires Redis Bucket4j via separate configuration.
 *         <li>Single device fingerprint: {@code rate:guest:fp:{fp}} — 1/day ceiling.
 *       </ul>
 *   <li>If Authorization header IS present → skip (let downstream filters handle it).
 * </ol>
 *
 * <p>TDD §2.1 Gateway filter chain; §16.5 limit single IP guest 10/day, single device_fp 1/day.
 * Enforced paths: {@code /api/landing/**}, {@code /api/session/resolve}, {@code /api/guest/**},
 * {@code /api/observer/exchange}.
 */
@Component
public class AnonFilter implements GlobalFilter, Ordered {

  public static final int ORDER = -150;

  private static final Logger log = LoggerFactory.getLogger(AnonFilter.class);

  /**
   * Request attribute key marking this request as handled by AnonFilter (used by IT assertions).
   */
  public static final String ATTR_ANON_HANDLED = "gw.anon.handled";

  private static final String HEADER_DEVICE_FP = "X-Device-Fp";

  /**
   * Anon-specific paths that bypass user JWT requirement.
   * Requests matching these paths without Authorization → scope=GUEST.
   */
  private static final String[] ANON_PATHS = {
    "/api/landing/",
    "/api/session/resolve",
    "/api/guest/",
    "/api/observer/exchange",
    "/api/share/"
  };

  private final RateLimiterRegistry registry;

  public AnonFilter(RateLimiterRegistry registry) {
    this.registry = registry;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String path = exchange.getRequest().getURI().getPath();

    // Infrastructure / docs — bypass entirely
    if (isPublicPath(path)) {
      return chain.filter(exchange);
    }

    String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

    // Has Authorization header — not anonymous traffic, skip this filter
    if (authHeader != null && !authHeader.isBlank()) {
      return chain.filter(exchange);
    }

    // Anonymous request — mark scope=GUEST
    exchange.getAttributes().put(UserContextHolder.ATTR_SCOPE, UserScope.GUEST);
    exchange.getAttributes().put(ATTR_ANON_HANDLED, Boolean.TRUE);

    // Rate-limit check for anon-only paths (IP dimension)
    if (isAnonPath(path)) {
      String ip = resolveIp(exchange);
      String deviceFp = exchange.getRequest().getHeaders().getFirst(HEADER_DEVICE_FP);

      // IP rate limit (10/day → uses Resilience4j registry "anon-ip" config)
      RateLimiter ipLimiter = registry.rateLimiter("anon-ip:" + ip, "anon-guest");
      if (!ipLimiter.acquirePermission()) {
        log.debug("AnonFilter: IP rate limit exceeded for ip={}", ip);
        return GatewayResponseHelper.error(exchange, ErrCode.RATE_LIMITED);
      }

      // Device-fp rate limit (1/day → "anon-fp" config) when fingerprint is present
      if (deviceFp != null && !deviceFp.isBlank()) {
        RateLimiter fpLimiter = registry.rateLimiter("anon-fp:" + deviceFp, "anon-guest-fp");
        if (!fpLimiter.acquirePermission()) {
          log.debug("AnonFilter: device_fp rate limit exceeded for fp={}", deviceFp);
          return GatewayResponseHelper.error(exchange, ErrCode.RATE_LIMITED);
        }
      }
    }

    // Forward downstream with mutated request carrying X-User-Scope header
    ServerHttpRequest mutated =
        exchange.getRequest().mutate().header("X-User-Scope", UserScope.GUEST.name()).build();
    return chain.filter(exchange.mutate().request(mutated).build());
  }

  @Override
  public int getOrder() {
    return ORDER;
  }

  private static boolean isPublicPath(String path) {
    return path.startsWith("/actuator/")
        || path.startsWith("/v3/api-docs")
        || path.startsWith("/swagger-ui");
  }

  private static boolean isAnonPath(String path) {
    for (String prefix : ANON_PATHS) {
      if (path.startsWith(prefix) || path.equals(prefix.stripTrailing())) {
        return true;
      }
    }
    return false;
  }

  private static String resolveIp(ServerWebExchange exchange) {
    String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
      return xff.split(",")[0].trim();
    }
    if (exchange.getRequest().getRemoteAddress() != null) {
      return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
    }
    return "unknown";
  }
}
