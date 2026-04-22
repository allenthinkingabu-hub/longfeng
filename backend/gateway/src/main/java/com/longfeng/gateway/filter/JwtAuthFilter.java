package com.longfeng.gateway.filter;

import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * JWT RS256 authentication filter (order = -100) · 落地计划 §6.7 Step 7.
 *
 * <p>Rejects requests without a valid {@code Authorization: Bearer <token>} header.
 * {@code /actuator/**} and {@code /v3/api-docs} bypass authentication so ops/probes can reach
 * gateway health without a token.
 */
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

  private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
  public static final int ORDER = -100;

  private final RSAPublicKey publicKey;

  public JwtAuthFilter(RSAPublicKey publicKey) {
    this.publicKey = publicKey;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String path = exchange.getRequest().getURI().getPath();
    if (path.startsWith("/actuator/")
        || path.startsWith("/v3/api-docs")
        || path.startsWith("/swagger-ui")) {
      return chain.filter(exchange);
    }

    String auth = exchange.getRequest().getHeaders().getFirst("Authorization");
    if (auth == null || !auth.startsWith("Bearer ")) {
      return unauthorized(exchange);
    }
    String token = auth.substring("Bearer ".length()).trim();
    try {
      SignedJWT jwt = SignedJWT.parse(token);
      JWSVerifier verifier = new RSASSAVerifier(publicKey);
      if (!jwt.verify(verifier)) {
        return unauthorized(exchange);
      }
      JWTClaimsSet claims = jwt.getJWTClaimsSet();
      Date exp = claims.getExpirationTime();
      if (exp == null || exp.before(new Date())) {
        return unauthorized(exchange);
      }
      String userId = claims.getSubject();
      if (userId != null) {
        ServerHttpRequest mutated =
            exchange.getRequest().mutate().header("X-User-Id", userId).build();
        return chain.filter(exchange.mutate().request(mutated).build());
      }
      return chain.filter(exchange);
    } catch (Exception e) {
      log.debug("jwt verification failed: {}", e.getMessage());
      return unauthorized(exchange);
    }
  }

  private Mono<Void> unauthorized(ServerWebExchange exchange) {
    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
    String traceId = exchange.getResponse().getHeaders().getFirst("X-Trace-Id");
    byte[] body =
        ("{\"code\":40101,\"message\":\"UNAUTHORIZED\",\"data\":null,\"traceId\":\""
                + (traceId == null ? "" : traceId)
                + "\"}")
            .getBytes(StandardCharsets.UTF_8);
    DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
    return exchange.getResponse().writeWith(Mono.just(buffer));
  }

  @Override
  public int getOrder() {
    return -100;
  }
}
