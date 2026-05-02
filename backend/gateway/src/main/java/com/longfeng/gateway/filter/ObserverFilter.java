package com.longfeng.gateway.filter;

import com.longfeng.gateway.service.BloomRevocationService;
import com.longfeng.gateway.tmp.ErrCode;
import com.longfeng.gateway.tmp.UserContextHolder;
import com.longfeng.gateway.tmp.UserScope;
import com.longfeng.gateway.util.GatewayResponseHelper;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.util.Date;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * ObserverFilter — validates OBSERVER JWT (HS256) and enforces scope=READ (order = {@value
 * #ORDER}).
 *
 * <p>Triggered when the request carries a {@code X-Observer-Token} header or the path starts with
 * {@code /api/observer/} (excluding the public exchange endpoint already handled by AnonFilter).
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Extract observer JWT from {@code X-Observer-Token} header or {@code Authorization: Bearer}
 *       when path is {@code /api/observer/**}.
 *   <li>Verify HS256 signature (key = {@code longfeng.jwt.anon.secret}) and expiry.
 *   <li>Check Redis {@code obs:revoked:{jti}} Bloom key — if found → 403.
 *   <li><strong>C4 RED LINE</strong>: any write verb (POST/PUT/DELETE/PATCH) → 403
 *       {@code OBSERVER_FORBIDDEN_WRITE} regardless of token validity.
 *   <li>Set scope=OBSERVER + store JTI in exchange attribute.
 *   <li>Pass downstream with {@code X-User-Scope: OBSERVER} + {@code X-Observer-Jti} headers.
 * </ol>
 *
 * <p>TDD §16.2 OBSERVER 三重防护 — this filter is the first of three layers (gateway → business
 * annotation → frontend ARIA).
 */
@Component
public class ObserverFilter implements GlobalFilter, Ordered {

  public static final int ORDER = -130;

  private static final Logger log = LoggerFactory.getLogger(ObserverFilter.class);

  /** HTTP verbs that constitute a "write" — all must be blocked for OBSERVER scope (C4). */
  private static final Set<HttpMethod> WRITE_METHODS =
      Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.PATCH);

  private static final String HEADER_OBSERVER_TOKEN = "X-Observer-Token";
  private static final String HEADER_OBSERVER_JTI = "X-Observer-Jti";
  /** Observer API path prefix (excludes /api/observer/exchange which is AnonFilter territory). */
  private static final String OBSERVER_PATH_PREFIX = "/api/observer/";

  /** Claim key inside observer JWT that marks token type. */
  private static final String CLAIM_TOKEN_TYPE = "type";
  private static final String TOKEN_TYPE_OBSERVER = "OBSERVER";

  private final JWSVerifier observerVerifier;
  private final BloomRevocationService revocationService;

  public ObserverFilter(
      @Qualifier("observerHmacVerifier") JWSVerifier observerVerifier,
      BloomRevocationService revocationService) {
    this.observerVerifier = observerVerifier;
    this.revocationService = revocationService;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String path = exchange.getRequest().getURI().getPath();

    // Only engage for observer paths or explicit observer token header
    String observerToken = exchange.getRequest().getHeaders().getFirst(HEADER_OBSERVER_TOKEN);

    boolean isObserverPath =
        path.startsWith(OBSERVER_PATH_PREFIX) && !path.equals("/api/observer/exchange");

    if (observerToken == null || observerToken.isBlank()) {
      // Check Authorization header if this is an observer path
      if (isObserverPath) {
        String auth = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
          observerToken = auth.substring("Bearer ".length()).trim();
        }
      }
    }

    if (observerToken == null || observerToken.isBlank()) {
      // Not an observer request; pass through
      return chain.filter(exchange);
    }

    final String tokenValue = observerToken;

    // Parse and verify HS256 JWT
    JWTClaimsSet claims;
    String jti;
    try {
      SignedJWT jwt = SignedJWT.parse(tokenValue);
      if (!jwt.verify(observerVerifier)) {
        log.debug("ObserverFilter: invalid HS256 signature");
        return GatewayResponseHelper.error(exchange, ErrCode.OBSERVER_TOKEN_INVALID);
      }
      claims = jwt.getJWTClaimsSet();
      Date exp = claims.getExpirationTime();
      if (exp == null || exp.before(new Date())) {
        log.debug("ObserverFilter: observer token expired");
        return GatewayResponseHelper.error(exchange, ErrCode.OBSERVER_TOKEN_INVALID);
      }
      jti = claims.getJWTID();
      if (jti == null || jti.isBlank()) {
        log.debug("ObserverFilter: observer token missing jti");
        return GatewayResponseHelper.error(exchange, ErrCode.OBSERVER_TOKEN_INVALID);
      }
    } catch (Exception e) {
      log.debug("ObserverFilter: token parse/verify error: {}", e.getMessage());
      return GatewayResponseHelper.error(exchange, ErrCode.OBSERVER_TOKEN_INVALID);
    }

    final String jtiValue = jti;

    // C4 RED LINE: write verb check BEFORE Bloom lookup (fast-fail, no Redis round-trip needed)
    HttpMethod method = exchange.getRequest().getMethod();
    if (WRITE_METHODS.contains(method)) {
      log.debug(
          "ObserverFilter: C4 red-line — write method {} blocked for observer jti={}", method, jtiValue);
      return GatewayResponseHelper.error(exchange, ErrCode.OBSERVER_FORBIDDEN_WRITE);
    }

    // Bloom revocation check
    return revocationService
        .isObserverTokenRevoked(jtiValue)
        .flatMap(
            revoked -> {
              if (revoked) {
                log.debug("ObserverFilter: observer token revoked, jti={}", jtiValue);
                return GatewayResponseHelper.error(exchange, ErrCode.OBSERVER_TOKEN_REVOKED);
              }

              // Valid observer token — set scope=OBSERVER + store JTI
              exchange.getAttributes().put(UserContextHolder.ATTR_SCOPE, UserScope.OBSERVER);
              exchange.getAttributes().put(UserContextHolder.ATTR_OBSERVER_JTI, jtiValue);

              ServerHttpRequest mutated =
                  exchange
                      .getRequest()
                      .mutate()
                      .header("X-User-Scope", UserScope.OBSERVER.name())
                      .header(HEADER_OBSERVER_JTI, jtiValue)
                      // Remove raw Authorization header to prevent downstream confusion
                      .headers(h -> h.remove("X-Observer-Token"))
                      .build();
              return chain.filter(exchange.mutate().request(mutated).build());
            });
  }

  @Override
  public int getOrder() {
    return ORDER;
  }
}
