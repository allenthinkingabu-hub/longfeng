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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * ShareFilter — validates share tokens (HS256) and checks Redis revocation list (order = {@value
 * #ORDER}).
 *
 * <p>Triggered when the request carries a {@code X-Share-Token} header or the path starts with
 * {@code /api/share/}. Share tokens are short-lived HS256 JWTs signed with
 * {@code longfeng.jwt.share.secret}.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Extract the share token from {@code X-Share-Token} header or {@code shareToken} query param.
 *   <li>Verify HS256 signature and expiry.
 *   <li>Check Redis {@code share:revoked:{jti}} Bloom key — if found → 403.
 *   <li>Set scope=GUEST + store share token JTI in exchange attribute.
 *   <li>Pass downstream with {@code X-User-Scope: GUEST} + {@code X-Share-Jti} headers.
 * </ol>
 *
 * <p>TDD §0.9 D-Share; §16.1 kchain layer 2; §12.9 share link preview.
 */
@Component
public class ShareFilter implements GlobalFilter, Ordered {

  public static final int ORDER = -140;

  private static final Logger log = LoggerFactory.getLogger(ShareFilter.class);

  private static final String HEADER_SHARE_TOKEN = "X-Share-Token";
  private static final String HEADER_SHARE_JTI = "X-Share-Jti";
  private static final String SHARE_PATH_PREFIX = "/api/share/";

  private final JWSVerifier shareVerifier;
  private final BloomRevocationService revocationService;

  public ShareFilter(
      @Qualifier("shareHmacVerifier") JWSVerifier shareVerifier,
      BloomRevocationService revocationService) {
    this.shareVerifier = shareVerifier;
    this.revocationService = revocationService;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String path = exchange.getRequest().getURI().getPath();

    // Only engage for share-related paths or requests carrying a share token header
    String shareToken = exchange.getRequest().getHeaders().getFirst(HEADER_SHARE_TOKEN);
    if (shareToken == null || shareToken.isBlank()) {
      shareToken = exchange.getRequest().getQueryParams().getFirst("shareToken");
    }

    boolean isSharePath = path.startsWith(SHARE_PATH_PREFIX);

    if (shareToken == null || shareToken.isBlank()) {
      // No share token present — not a share request, pass through
      return chain.filter(exchange);
    }

    final String tokenValue = shareToken;

    // Parse and verify HS256 JWT
    JWTClaimsSet claims;
    String jti;
    try {
      SignedJWT jwt = SignedJWT.parse(tokenValue);
      if (!jwt.verify(shareVerifier)) {
        log.debug("ShareFilter: invalid HS256 signature");
        return GatewayResponseHelper.error(exchange, ErrCode.SHARE_TOKEN_INVALID);
      }
      claims = jwt.getJWTClaimsSet();
      Date exp = claims.getExpirationTime();
      if (exp == null || exp.before(new Date())) {
        log.debug("ShareFilter: share token expired");
        return GatewayResponseHelper.error(exchange, ErrCode.SHARE_TOKEN_INVALID);
      }
      jti = claims.getJWTID();
      if (jti == null || jti.isBlank()) {
        log.debug("ShareFilter: share token missing jti");
        return GatewayResponseHelper.error(exchange, ErrCode.SHARE_TOKEN_INVALID);
      }
    } catch (Exception e) {
      log.debug("ShareFilter: token parse/verify error: {}", e.getMessage());
      return GatewayResponseHelper.error(exchange, ErrCode.SHARE_TOKEN_INVALID);
    }

    final String jtiValue = jti;

    // Bloom revocation check (async, Redis)
    return revocationService
        .isShareTokenRevoked(jtiValue)
        .flatMap(
            revoked -> {
              if (revoked) {
                log.debug("ShareFilter: share token revoked, jti={}", jtiValue);
                return GatewayResponseHelper.error(exchange, ErrCode.SHARE_TOKEN_REVOKED);
              }

              // Valid share token — set scope=GUEST + store JTI
              exchange.getAttributes().put(UserContextHolder.ATTR_SCOPE, UserScope.GUEST);
              exchange.getAttributes().put(UserContextHolder.ATTR_SHARE_TOKEN_JTI, jtiValue);

              ServerHttpRequest mutated =
                  exchange
                      .getRequest()
                      .mutate()
                      .header("X-User-Scope", UserScope.GUEST.name())
                      .header(HEADER_SHARE_JTI, jtiValue)
                      .build();
              return chain.filter(exchange.mutate().request(mutated).build());
            });
  }

  @Override
  public int getOrder() {
    return ORDER;
  }
}
