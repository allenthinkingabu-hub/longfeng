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
 * Unit tests for {@link ShareFilter}.
 */
class ShareFilterTest {

  private static final byte[] SECRET = "test-share-secret-min32chars-xxxx".getBytes();

  private ShareFilter filter;
  private final Map<String, Boolean> revokedTokens = new HashMap<>();

  @BeforeEach
  void setUp() throws Exception {
    revokedTokens.clear();
    BloomRevocationService revocationService =
        new BloomRevocationService(null) {
          @Override
          public Mono<Boolean> isShareTokenRevoked(String jti) {
            return Mono.just(revokedTokens.getOrDefault(jti, false));
          }
        };
    MACVerifier verifier = new MACVerifier(SECRET);
    filter = new ShareFilter(verifier, revocationService);
  }

  private String mintShareToken(String jti, Date exp) throws Exception {
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .jwtID(jti)
            .issueTime(new Date())
            .expirationTime(exp)
            .claim("type", "SHARE")
            .build();
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
    jwt.sign(new MACSigner(SECRET));
    return jwt.serialize();
  }

  @Test
  void noShareToken_passesThrough() {
    MockServerHttpRequest request = MockServerHttpRequest.get("/api/wb/questions").build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);
    GatewayFilterChain chain = ex -> Mono.empty();

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
    assertThat((Object) exchange.getAttribute(UserContextHolder.ATTR_SCOPE)).isNull();
  }

  @Test
  void validShareToken_setsGuestScope() throws Exception {
    String jti = UUID.randomUUID().toString();
    String token = mintShareToken(jti, new Date(System.currentTimeMillis() + 60_000));

    // (default false · no setup needed)

    MockServerHttpRequest request =
        MockServerHttpRequest.get("/api/share/abc")
            .header("X-Share-Token", token)
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    final ServerWebExchange[] passedEx = {null};
    GatewayFilterChain chain = ex -> {
      passedEx[0] = ex;
      return Mono.empty();
    };

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    assertThat((Object) exchange.getAttribute(UserContextHolder.ATTR_SCOPE)).isEqualTo(UserScope.GUEST);
    assertThat((Object) exchange.getAttribute(UserContextHolder.ATTR_SHARE_TOKEN_JTI)).isEqualTo(jti);
    assertThat(passedEx[0].getRequest().getHeaders().getFirst("X-Share-Jti")).isEqualTo(jti);
    assertThat(passedEx[0].getRequest().getHeaders().getFirst("X-User-Scope")).isEqualTo("GUEST");
  }

  @Test
  void revokedShareToken_returns403() throws Exception {
    String jti = UUID.randomUUID().toString();
    String token = mintShareToken(jti, new Date(System.currentTimeMillis() + 60_000));

    revokedTokens.put(jti, true);

    MockServerHttpRequest request =
        MockServerHttpRequest.get("/api/share/abc")
            .header("X-Share-Token", token)
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);
    GatewayFilterChain chain = ex -> Mono.empty();

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void invalidSignatureShareToken_returns401() throws Exception {
    // Mint with a different secret
    byte[] wrongSecret = "wrong-share-secret-min32chars-xxx".getBytes();
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .jwtID(UUID.randomUUID().toString())
            .expirationTime(new Date(System.currentTimeMillis() + 60_000))
            .build();
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
    jwt.sign(new MACSigner(wrongSecret));
    String token = jwt.serialize();

    MockServerHttpRequest request =
        MockServerHttpRequest.get("/api/share/abc")
            .header("X-Share-Token", token)
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);
    GatewayFilterChain chain = ex -> Mono.empty();

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void expiredShareToken_returns401() throws Exception {
    String jti = UUID.randomUUID().toString();
    String token = mintShareToken(jti, new Date(System.currentTimeMillis() - 1000)); // already expired

    MockServerHttpRequest request =
        MockServerHttpRequest.get("/api/share/abc")
            .header("X-Share-Token", token)
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);
    GatewayFilterChain chain = ex -> Mono.empty();

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void filterOrder_isAfterAnonBeforeObserver() {
    assertThat(filter.getOrder()).isEqualTo(ShareFilter.ORDER);
    assertThat(ShareFilter.ORDER).isGreaterThan(AnonFilter.ORDER);
    assertThat(ShareFilter.ORDER).isLessThan(ObserverFilter.ORDER);
  }
}
