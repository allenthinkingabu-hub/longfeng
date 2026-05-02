package com.longfeng.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.longfeng.gateway.service.BloomRevocationService;
import com.longfeng.gateway.tmp.UserContextHolder;
import com.longfeng.gateway.tmp.UserScope;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit tests for {@link ObserverFilter}.
 *
 * <p>Key C4 red-line: any write verb (POST/PUT/DELETE/PATCH) with a valid observer token must
 * return 403 {@code OBSERVER_FORBIDDEN_WRITE}.
 */
class ObserverFilterTest {

  private static final byte[] SECRET = "test-anon-secret-min32chars-xxxxx".getBytes();

  private ObserverFilter filter;
  private final Map<String, Boolean> revokedTokens = new HashMap<>();

  @BeforeEach
  void setUp() throws Exception {
    revokedTokens.clear();
    BloomRevocationService revocationService =
        new BloomRevocationService(null) {
          @Override
          public Mono<Boolean> isObserverTokenRevoked(String jti) {
            return Mono.just(revokedTokens.getOrDefault(jti, false));
          }
        };
    MACVerifier verifier = new MACVerifier(SECRET);
    filter = new ObserverFilter(verifier, revocationService);
  }

  private String mintObserverToken(String jti, Date exp) throws Exception {
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .jwtID(jti)
            .issueTime(new Date())
            .expirationTime(exp)
            .claim("type", "OBSERVER")
            .build();
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
    jwt.sign(new MACSigner(SECRET));
    return jwt.serialize();
  }

  @Test
  void noObserverToken_passesThrough() {
    MockServerHttpRequest request =
        MockServerHttpRequest.get("/api/wb/questions").build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);
    GatewayFilterChain chain = ex -> Mono.empty();

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
    assertThat((Object) exchange.getAttribute(UserContextHolder.ATTR_SCOPE)).isNull();
  }

  @Test
  void validObserverToken_getRequest_setsObserverScope() throws Exception {
    String jti = UUID.randomUUID().toString();
    String token = mintObserverToken(jti, new Date(System.currentTimeMillis() + 60_000));

    // (default false · no setup needed)

    MockServerHttpRequest request =
        MockServerHttpRequest.get("/api/observer/items")
            .header("X-Observer-Token", token)
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    final ServerWebExchange[] passedEx = {null};
    GatewayFilterChain chain = ex -> {
      passedEx[0] = ex;
      return Mono.empty();
    };

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    assertThat((Object) exchange.getAttribute(UserContextHolder.ATTR_SCOPE)).isEqualTo(UserScope.OBSERVER);
    assertThat((Object) exchange.getAttribute(UserContextHolder.ATTR_OBSERVER_JTI)).isEqualTo(jti);
    assertThat(passedEx[0].getRequest().getHeaders().getFirst("X-User-Scope")).isEqualTo("OBSERVER");
  }

  // ---- C4 RED LINE TESTS ----

  @Test
  void c4_postRequest_returns403ObserverForbiddenWrite() throws Exception {
    String jti = UUID.randomUUID().toString();
    String token = mintObserverToken(jti, new Date(System.currentTimeMillis() + 60_000));

    MockServerHttpRequest request =
        MockServerHttpRequest.post("/api/observer/items")
            .header("X-Observer-Token", token)
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);
    GatewayFilterChain chain = ex -> Mono.empty();

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void c4_putRequest_returns403() throws Exception {
    String jti = UUID.randomUUID().toString();
    String token = mintObserverToken(jti, new Date(System.currentTimeMillis() + 60_000));

    MockServerHttpRequest request =
        MockServerHttpRequest.put("/api/observer/items/1")
            .header("X-Observer-Token", token)
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);
    GatewayFilterChain chain = ex -> Mono.empty();

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void c4_deleteRequest_returns403() throws Exception {
    String jti = UUID.randomUUID().toString();
    String token = mintObserverToken(jti, new Date(System.currentTimeMillis() + 60_000));

    MockServerHttpRequest request =
        MockServerHttpRequest.delete("/api/observer/items/1")
            .header("X-Observer-Token", token)
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);
    GatewayFilterChain chain = ex -> Mono.empty();

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void c4_patchRequest_returns403() throws Exception {
    String jti = UUID.randomUUID().toString();
    String token = mintObserverToken(jti, new Date(System.currentTimeMillis() + 60_000));

    MockServerHttpRequest request =
        MockServerHttpRequest.patch("/api/observer/items/1")
            .header("X-Observer-Token", token)
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);
    GatewayFilterChain chain = ex -> Mono.empty();

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void revokedObserverToken_returns403() throws Exception {
    String jti = UUID.randomUUID().toString();
    String token = mintObserverToken(jti, new Date(System.currentTimeMillis() + 60_000));

    revokedTokens.put(jti, true);

    MockServerHttpRequest request =
        MockServerHttpRequest.get("/api/observer/items")
            .header("X-Observer-Token", token)
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);
    GatewayFilterChain chain = ex -> Mono.empty();

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void invalidSignature_returns401() throws Exception {
    byte[] wrongSecret = "wrong-anon-secret-min32chars-xxxx".getBytes();
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .jwtID(UUID.randomUUID().toString())
            .expirationTime(new Date(System.currentTimeMillis() + 60_000))
            .build();
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
    jwt.sign(new MACSigner(wrongSecret));

    MockServerHttpRequest request =
        MockServerHttpRequest.get("/api/observer/items")
            .header("X-Observer-Token", jwt.serialize())
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);
    GatewayFilterChain chain = ex -> Mono.empty();

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void expiredToken_returns401() throws Exception {
    String jti = UUID.randomUUID().toString();
    String token = mintObserverToken(jti, new Date(System.currentTimeMillis() - 1000));

    MockServerHttpRequest request =
        MockServerHttpRequest.get("/api/observer/items")
            .header("X-Observer-Token", token)
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);
    GatewayFilterChain chain = ex -> Mono.empty();

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void filterOrder_isAfterShareBeforeAuth() {
    assertThat(filter.getOrder()).isEqualTo(ObserverFilter.ORDER);
    assertThat(ObserverFilter.ORDER).isGreaterThan(ShareFilter.ORDER);
    assertThat(ObserverFilter.ORDER).isLessThan(AuthFilter.ORDER);
  }
}
