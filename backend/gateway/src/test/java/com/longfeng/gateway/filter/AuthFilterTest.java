package com.longfeng.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.longfeng.gateway.tmp.UserContextHolder;
import com.longfeng.gateway.tmp.UserScope;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit tests for {@link AuthFilter}.
 */
class AuthFilterTest {

  private static RSAPrivateKey privateKey;
  private static RSAPublicKey publicKey;
  private static AuthFilter filter;

  @BeforeAll
  static void setUp() throws Exception {
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048);
    KeyPair pair = kpg.generateKeyPair();
    privateKey = (RSAPrivateKey) pair.getPrivate();
    publicKey = (RSAPublicKey) pair.getPublic();
    filter = new AuthFilter(publicKey);
  }

  private String mintUserToken(String subject, String tier) throws Exception {
    JWTClaimsSet.Builder b =
        new JWTClaimsSet.Builder()
            .subject(subject)
            .issueTime(new Date())
            .expirationTime(new Date(System.currentTimeMillis() + 60_000));
    if (tier != null) {
      b.claim("tier", tier);
    }
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), b.build());
    jwt.sign(new RSASSASigner(privateKey));
    return jwt.serialize();
  }

  @Test
  void missingToken_returns401() {
    MockServerHttpRequest request =
        MockServerHttpRequest.get("/api/wb/questions").build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);
    GatewayFilterChain chain = ex -> Mono.empty();

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void validToken_setsUserScope() throws Exception {
    String token = mintUserToken("user-123", null);
    MockServerHttpRequest request =
        MockServerHttpRequest.get("/api/wb/questions")
            .header("Authorization", "Bearer " + token)
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    final ServerWebExchange[] passedEx = {null};
    GatewayFilterChain chain = ex -> {
      passedEx[0] = ex;
      return Mono.empty();
    };

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    assertThat((Object) exchange.getAttribute(UserContextHolder.ATTR_SCOPE)).isEqualTo(UserScope.USER);
    assertThat((Object) exchange.getAttribute(UserContextHolder.ATTR_USER_ID)).isEqualTo("user-123");
    assertThat(passedEx[0].getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("user-123");
    assertThat(passedEx[0].getRequest().getHeaders().getFirst("X-User-Scope")).isEqualTo("USER");
  }

  @Test
  void validToken_withTierClaim_propagatesXUserTierHeader() throws Exception {
    String token = mintUserToken("vip-user", "VIP");
    MockServerHttpRequest request =
        MockServerHttpRequest.get("/api/ai/analyze")
            .header("Authorization", "Bearer " + token)
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    final ServerWebExchange[] passedEx = {null};
    GatewayFilterChain chain = ex -> {
      passedEx[0] = ex;
      return Mono.empty();
    };

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    assertThat(passedEx[0].getRequest().getHeaders().getFirst("X-User-Tier")).isEqualTo("VIP");
  }

  @Test
  void tokenFromQueryParam_isAccepted() throws Exception {
    String token = mintUserToken("sse-user", null);
    MockServerHttpRequest request =
        MockServerHttpRequest.get("/api/ai/stream/task1?token=" + token).build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    final ServerWebExchange[] passedEx = {null};
    GatewayFilterChain chain = ex -> {
      passedEx[0] = ex;
      return Mono.empty();
    };

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    assertThat((Object) exchange.getAttribute(UserContextHolder.ATTR_SCOPE)).isEqualTo(UserScope.USER);
  }

  @Test
  void expiredToken_returns401() throws Exception {
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject("old-user")
            .expirationTime(new Date(System.currentTimeMillis() - 1000))
            .build();
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
    jwt.sign(new RSASSASigner(privateKey));

    MockServerHttpRequest request =
        MockServerHttpRequest.get("/api/wb/questions")
            .header("Authorization", "Bearer " + jwt.serialize())
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);
    GatewayFilterChain chain = ex -> Mono.empty();

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void actuatorPath_passesWithoutToken() {
    MockServerHttpRequest request =
        MockServerHttpRequest.get("/actuator/health").build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);
    GatewayFilterChain chain = ex -> Mono.empty();

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
    // No 401 for actuator
    assertThat(exchange.getResponse().getStatusCode()).isNull();
  }

  @Test
  void existingScope_skipsFilter() throws Exception {
    // Pre-set scope (simulating AnonFilter already ran)
    MockServerHttpRequest request =
        MockServerHttpRequest.get("/api/guest/session").build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);
    exchange.getAttributes().put(UserContextHolder.ATTR_SCOPE, UserScope.GUEST);

    GatewayFilterChain chain = ex -> Mono.empty();

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
    // No 401 — scope was already set
    assertThat(exchange.getResponse().getStatusCode()).isNull();
  }

  @Test
  void filterOrder_isLastAuthFilter() {
    assertThat(filter.getOrder()).isEqualTo(AuthFilter.ORDER);
    assertThat(AuthFilter.ORDER).isGreaterThan(ObserverFilter.ORDER);
    // AuthFilter runs before the legacy JwtAuthFilter
    assertThat(AuthFilter.ORDER).isLessThan(JwtAuthFilter.ORDER);
  }
}
