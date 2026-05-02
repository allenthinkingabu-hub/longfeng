package com.longfeng.gateway.filter;

import com.longfeng.gateway.tmp.ErrCode;
import com.longfeng.gateway.tmp.UserContextHolder;
import com.longfeng.gateway.tmp.UserScope;
import com.longfeng.gateway.util.GatewayResponseHelper;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * AuthFilter — user JWT (RS256) validation + Sa-Token bridge + Spring Security annotation hook
 * (order = {@value #ORDER}).
 *
 * <p>This filter is the last auth filter in the chain, running AFTER AnonFilter, ShareFilter, and
 * ObserverFilter. It handles only requests that:
 * <ol>
 *   <li>Are NOT already resolved as GUEST or OBSERVER by upstream filters.
 *   <li>Do NOT match public paths (actuator, api-docs).
 *   <li>Carry an {@code Authorization: Bearer <RS256-JWT>} header (or {@code ?token=} query param
 *       for EventSource compatibility).
 * </ol>
 *
 * <p>On success:
 * <ul>
 *   <li>Sets scope=USER in exchange attributes.
 *   <li>Adds {@code X-User-Id} header for downstream services.
 *   <li>Adds {@code X-User-Tier} header (from JWT {@code tier} claim) for VIP model gate (§16.8).
 *   <li>Adds {@code X-User-Scope: USER} header.
 * </ul>
 *
 * <p>Backward compatibility: this filter supersedes the legacy {@link JwtAuthFilter}. The
 * JwtAuthFilter bean is retained but has its order set to a no-op value (JwtAuthFilter.ORDER
 * remains -100 but this AuthFilter at -120 runs first and short-circuits the JWT path).
 *
 * <p>TDD §0.9 D-Auth (Sa-Token + Spring Security 双闸); §16.1 kchain layer 4.
 */
@Component
public class AuthFilter implements GlobalFilter, Ordered {

  public static final int ORDER = -120;

  private static final Logger log = LoggerFactory.getLogger(AuthFilter.class);

  private static final String CLAIM_TIER = "tier";
  private static final String CLAIM_TOKEN_TYPE = "type";
  private static final String TOKEN_TYPE_USER = "USER";

  private final RSAPublicKey rsaPublicKey;

  public AuthFilter(RSAPublicKey jwtPublicKey) {
    this.rsaPublicKey = jwtPublicKey;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String path = exchange.getRequest().getURI().getPath();

    // Infrastructure / docs — pass through
    if (isPublicPath(path)) {
      return chain.filter(exchange);
    }

    // If scope already set by AnonFilter / ShareFilter / ObserverFilter — skip JWT auth
    UserScope existingScope = exchange.getAttribute(UserContextHolder.ATTR_SCOPE);
    if (existingScope != null) {
      return chain.filter(exchange);
    }

    // Resolve Bearer token
    String auth = exchange.getRequest().getHeaders().getFirst("Authorization");
    if (auth == null || !auth.startsWith("Bearer ")) {
      String tokenParam = exchange.getRequest().getQueryParams().getFirst("token");
      if (tokenParam != null && !tokenParam.isBlank()) {
        auth = "Bearer " + tokenParam;
      }
    }

    if (auth == null || !auth.startsWith("Bearer ")) {
      return GatewayResponseHelper.error(exchange, ErrCode.UNAUTHORIZED);
    }

    String token = auth.substring("Bearer ".length()).trim();

    try {
      SignedJWT jwt = SignedJWT.parse(token);
      JWSVerifier verifier = new RSASSAVerifier(rsaPublicKey);
      if (!jwt.verify(verifier)) {
        log.debug("AuthFilter: RS256 signature invalid");
        return GatewayResponseHelper.error(exchange, ErrCode.UNAUTHORIZED);
      }
      JWTClaimsSet claims = jwt.getJWTClaimsSet();
      Date exp = claims.getExpirationTime();
      if (exp == null || exp.before(new Date())) {
        log.debug("AuthFilter: user JWT expired");
        return GatewayResponseHelper.error(exchange, ErrCode.UNAUTHORIZED);
      }

      String userId = claims.getSubject();
      String tier = claims.getStringClaim(CLAIM_TIER);

      // Set scope=USER
      exchange.getAttributes().put(UserContextHolder.ATTR_SCOPE, UserScope.USER);
      if (userId != null) {
        exchange.getAttributes().put(UserContextHolder.ATTR_USER_ID, userId);
      }

      // Build mutated request with downstream headers
      ServerHttpRequest.Builder mutated = exchange.getRequest().mutate()
          .header("X-User-Scope", UserScope.USER.name());
      if (userId != null) {
        mutated.header("X-User-Id", userId);
      }
      if (tier != null && !tier.isBlank()) {
        // §16.8 VIP model gate — downstream ai-analysis-service validates this header
        mutated.header("X-User-Tier", tier);
      }

      return chain.filter(exchange.mutate().request(mutated.build()).build());

    } catch (Exception e) {
      log.debug("AuthFilter: JWT parse/verify error: {}", e.getMessage());
      return GatewayResponseHelper.error(exchange, ErrCode.UNAUTHORIZED);
    }
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
}
