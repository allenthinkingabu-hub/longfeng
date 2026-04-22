package com.longfeng.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * GatewayRouteIT · 3 GlobalFilter 效果验证 · 落地计划 §6.7 Step 13.
 *
 * <p>Generates a fresh RSA key pair at class-load time; publishes the public key (PEM) to a temp
 * file and exposes its path through {@code jwt.public-key-path} so {@code JwtAuthFilter} accepts
 * our signed tokens. This lets the 429 test drive authenticated traffic through the rate limiter
 * (order=-60, after JWT at order=-100).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GatewayRouteIT {

  @LocalServerPort private int port;

  private static RSAPrivateKey privateKey;
  private static RSAPublicKey publicKey;
  private static java.nio.file.Path pubKeyPath;

  static {
    try {
      KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
      kpg.initialize(2048);
      KeyPair pair = kpg.generateKeyPair();
      privateKey = (RSAPrivateKey) pair.getPrivate();
      publicKey = (RSAPublicKey) pair.getPublic();
      String pem =
          "-----BEGIN PUBLIC KEY-----\n"
              + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(publicKey.getEncoded())
              + "\n-----END PUBLIC KEY-----\n";
      pubKeyPath = java.nio.file.Files.createTempFile("gateway-test-pub-", ".pem");
      java.nio.file.Files.writeString(pubKeyPath, pem);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @DynamicPropertySource
  static void overrideJwtKey(DynamicPropertyRegistry registry) {
    registry.add("jwt.public-key-path", () -> pubKeyPath.toString());
  }

  @BeforeAll
  static void check() {
    assertThat(privateKey).isNotNull();
    assertThat(publicKey).isNotNull();
  }

  @Test
  void missingTokenReturns401WithTraceId() {
    WebTestClient.bindToServer()
        .baseUrl("http://localhost:" + port)
        .build()
        .get()
        .uri("/api/v1/wrongbook/items")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .exists("X-Trace-Id")
        .expectBody()
        .jsonPath("$.traceId")
        .isNotEmpty();
  }

  @Test
  void rateLimitTriggers429OnBurst() throws Exception {
    String token = signToken("burst-user");
    WebTestClient client =
        WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .responseTimeout(java.time.Duration.ofSeconds(5))
            .build();

    int total = 60;
    AtomicInteger count429 = new AtomicInteger();
    CountDownLatch done = new CountDownLatch(total);
    ExecutorService pool = Executors.newFixedThreadPool(20);
    for (int i = 0; i < total; i++) {
      pool.submit(
          () -> {
            try {
              Integer status =
                  client
                      .get()
                      .uri("/api/v1/wrongbook/items")
                      .header("Authorization", "Bearer " + token)
                      .exchange()
                      .returnResult(Void.class)
                      .getStatus()
                      .value();
              if (status == 429) {
                count429.incrementAndGet();
              }
            } catch (Exception ignored) {
              // network errors from unroutable downstream don't count as 429
            } finally {
              done.countDown();
            }
          });
    }
    done.await(30, TimeUnit.SECONDS);
    pool.shutdownNow();
    assertThat(count429.get()).isGreaterThanOrEqualTo(1);
  }

  private String signToken(String subject) throws Exception {
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject(subject)
            .issueTime(new Date())
            .expirationTime(new Date(System.currentTimeMillis() + 60_000))
            .build();
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
    JWSSigner signer = new RSASSASigner(privateKey);
    jwt.sign(signer);
    return jwt.serialize();
  }
}
