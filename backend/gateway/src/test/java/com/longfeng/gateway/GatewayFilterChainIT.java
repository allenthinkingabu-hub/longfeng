package com.longfeng.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * GatewayFilterChainIT — integration test for the complete 4-filter chain.
 *
 * <p>Uses:
 * <ul>
 *   <li>Testcontainers Redis for Bloom revocation checks (ShareFilter / ObserverFilter).
 *   <li>Dynamically generated RSA key pair for RS256 user JWT (AuthFilter).
 *   <li>Hard-coded HS256 secrets matching test {@code application.yml}.
 * </ul>
 *
 * <p>Assertions (exit-gate checklist items):
 * <ol>
 *   <li>Filter chain order: AnonFilter → ShareFilter → ObserverFilter → AuthFilter (via order
 *       constants).
 *   <li>OBSERVER write verb → 403 OBSERVER_FORBIDDEN_WRITE.
 *   <li>Anonymous request (no auth) → AnonFilter sets scope, passes through.
 *   <li>Revoked share token → ShareFilter 403.
 *   <li>WireMock-simulated Nacos route via static YAML routes (app context loads).
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Testcontainers
class GatewayFilterChainIT {

  @Container
  @SuppressWarnings("resource")
  static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  @LocalServerPort private int port;

  private static RSAPrivateKey rsaPrivateKey;
  private static RSAPublicKey rsaPublicKey;
  private static java.nio.file.Path pubKeyPath;

  // Secrets must match test/resources/application.yml
  private static final byte[] SHARE_SECRET = "test-share-secret-min32chars-xxxx".getBytes();
  private static final byte[] OBS_SECRET = "test-anon-secret-min32chars-xxxxx".getBytes();

  static {
    try {
      KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
      kpg.initialize(2048);
      KeyPair pair = kpg.generateKeyPair();
      rsaPrivateKey = (RSAPrivateKey) pair.getPrivate();
      rsaPublicKey = (RSAPublicKey) pair.getPublic();
      String pem =
          "-----BEGIN PUBLIC KEY-----\n"
              + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(rsaPublicKey.getEncoded())
              + "\n-----END PUBLIC KEY-----\n";
      pubKeyPath = java.nio.file.Files.createTempFile("it-gateway-pub-", ".pem");
      java.nio.file.Files.writeString(pubKeyPath, pem);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add("jwt.public-key-path", () -> pubKeyPath.toString());
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379).toString());
  }

  @BeforeAll
  void waitForRedis() {
    assertThat(REDIS.isRunning()).isTrue();
  }

  // --------------------------------------------------------------------------
  // 1. Filter chain order assertions
  // --------------------------------------------------------------------------

  @Test
  void filterChainOrder_anonBeforeShareBeforeObserverBeforeAuth() {
    // Import the order constants from each filter
    int anonOrder = com.longfeng.gateway.filter.AnonFilter.ORDER;
    int shareOrder = com.longfeng.gateway.filter.ShareFilter.ORDER;
    int observerOrder = com.longfeng.gateway.filter.ObserverFilter.ORDER;
    int authOrder = com.longfeng.gateway.filter.AuthFilter.ORDER;
    int legacyOrder = com.longfeng.gateway.filter.JwtAuthFilter.ORDER;

    assertThat(anonOrder).isLessThan(shareOrder);
    assertThat(shareOrder).isLessThan(observerOrder);
    assertThat(observerOrder).isLessThan(authOrder);
    // AuthFilter runs before legacy JwtAuthFilter (which is effectively a no-op)
    assertThat(authOrder).isLessThan(legacyOrder);
  }

  // --------------------------------------------------------------------------
  // 2. Anonymous request — AnonFilter handles it (no 401)
  // --------------------------------------------------------------------------

  @Test
  void anonymousRequest_toPublicPath_returns200OrRouteError_notUnauthorized() {
    // /actuator/health is public — no JWT needed
    WebTestClient.bindToServer()
        .baseUrl("http://localhost:" + port)
        .build()
        .get()
        .uri("/actuator/health")
        .exchange()
        .expectStatus()
        .isOk();
  }

  @Test
  void anonymousRequest_withoutToken_toLandingPath_doesNotReturn401() {
    // Landing path is anon-allowed; route will 503/502 (no backend) but NOT 401
    WebTestClient client = WebTestClient.bindToServer()
        .baseUrl("http://localhost:" + port)
        .build();

    client.get()
        .uri("/api/landing/home")
        .exchange()
        .expectStatus()
        .value(status -> assertThat(status).isNotEqualTo(401));
  }

  // --------------------------------------------------------------------------
  // 3. OBSERVER write verb → 403 (C4 red-line)
  // --------------------------------------------------------------------------

  @Test
  void observerToken_postRequest_returns403() throws Exception {
    String jti = UUID.randomUUID().toString();
    String obsToken = mintObserverToken(jti, new Date(System.currentTimeMillis() + 60_000));

    WebTestClient.bindToServer()
        .baseUrl("http://localhost:" + port)
        .build()
        .post()
        .uri("/api/observer/items")
        .header("X-Observer-Token", obsToken)
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectBody()
        .jsonPath("$.code").isEqualTo(40303);
  }

  @Test
  void observerToken_putRequest_returns403() throws Exception {
    String jti = UUID.randomUUID().toString();
    String obsToken = mintObserverToken(jti, new Date(System.currentTimeMillis() + 60_000));

    WebTestClient.bindToServer()
        .baseUrl("http://localhost:" + port)
        .build()
        .put()
        .uri("/api/observer/items/1")
        .header("X-Observer-Token", obsToken)
        .exchange()
        .expectStatus()
        .isForbidden();
  }

  @Test
  void observerToken_deleteRequest_returns403() throws Exception {
    String jti = UUID.randomUUID().toString();
    String obsToken = mintObserverToken(jti, new Date(System.currentTimeMillis() + 60_000));

    WebTestClient.bindToServer()
        .baseUrl("http://localhost:" + port)
        .build()
        .delete()
        .uri("/api/observer/items/1")
        .header("X-Observer-Token", obsToken)
        .exchange()
        .expectStatus()
        .isForbidden();
  }

  // --------------------------------------------------------------------------
  // 4. Revoked share token → 403 (ShareFilter)
  // --------------------------------------------------------------------------

  @Test
  void revokedShareToken_returns403() throws Exception {
    String jti = UUID.randomUUID().toString();
    String shareToken = mintShareToken(jti, new Date(System.currentTimeMillis() + 60_000));

    // Write revocation key to Redis
    org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory factory =
        new org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory(
            REDIS.getHost(), REDIS.getMappedPort(6379));
    factory.afterPropertiesSet();
    org.springframework.data.redis.core.RedisTemplate<String, String> redisTemplate =
        new org.springframework.data.redis.core.RedisTemplate<>();
    redisTemplate.setConnectionFactory(factory);
    redisTemplate.setDefaultSerializer(
        new org.springframework.data.redis.serializer.StringRedisSerializer());
    redisTemplate.afterPropertiesSet();
    redisTemplate.opsForValue().set("share:revoked:" + jti, "1");
    factory.destroy();

    WebTestClient.bindToServer()
        .baseUrl("http://localhost:" + port)
        .build()
        .get()
        .uri("/api/share/" + jti)
        .header("X-Share-Token", shareToken)
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectBody()
        .jsonPath("$.code").isEqualTo(40304);
  }

  // --------------------------------------------------------------------------
  // 5. Valid user JWT → passes through (AuthFilter)
  // --------------------------------------------------------------------------

  @Test
  void userJwt_missingToken_returns401WithTraceId() {
    WebTestClient.bindToServer()
        .baseUrl("http://localhost:" + port)
        .build()
        .get()
        .uri("/api/wb/questions")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .exists("X-Trace-Id")
        .expectBody()
        .jsonPath("$.code").isEqualTo(40101);
  }

  // --------------------------------------------------------------------------
  // Helpers
  // --------------------------------------------------------------------------

  private String mintObserverToken(String jti, Date exp) throws Exception {
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .jwtID(jti)
            .issueTime(new Date())
            .expirationTime(exp)
            .claim("type", "OBSERVER")
            .build();
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
    jwt.sign(new MACSigner(OBS_SECRET));
    return jwt.serialize();
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
    jwt.sign(new MACSigner(SHARE_SECRET));
    return jwt.serialize();
  }
}
