package com.longfeng.gateway.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * HMAC verifiers for Share and Observer JWT tokens (HS256).
 *
 * <p>TDD §0.9 D-Anon-JWT: share and observer tokens use independent secrets to limit blast radius.
 * Both keys are distinct from the RS256 user JWT key (D-Auth).
 *
 * <p>Config keys:
 * <ul>
 *   <li>{@code longfeng.jwt.share.secret} — HS256 secret for share tokens
 *   <li>{@code longfeng.jwt.anon.secret} — HS256 secret for observer tokens
 * </ul>
 */
@Configuration
public class AnonJwtConfig {

  @Value("${longfeng.jwt.share.secret:changeme-share-secret-min32chars!!}")
  private String shareSecret;

  @Value("${longfeng.jwt.anon.secret:changeme-anon-secret-min32chars!!!}")
  private String anonSecret;

  /**
   * HMAC verifier for share tokens (HS256, key = {@code longfeng.jwt.share.secret}).
   */
  @Bean(name = "shareHmacVerifier")
  public MACVerifier shareHmacVerifier() throws Exception {
    byte[] keyBytes = shareSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    if (keyBytes.length < 32) {
      throw new IllegalArgumentException(
          "longfeng.jwt.share.secret must be ≥ 32 bytes (got " + keyBytes.length + ")");
    }
    return new MACVerifier(keyBytes);
  }

  /**
   * HMAC verifier for observer tokens (HS256, key = {@code longfeng.jwt.anon.secret}).
   */
  @Bean(name = "observerHmacVerifier")
  public MACVerifier observerHmacVerifier() throws Exception {
    byte[] keyBytes = anonSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    if (keyBytes.length < 32) {
      throw new IllegalArgumentException(
          "longfeng.jwt.anon.secret must be ≥ 32 bytes (got " + keyBytes.length + ")");
    }
    return new MACVerifier(keyBytes);
  }

  /** Expose the raw share secret so test utilities can mint tokens. */
  @Bean(name = "shareJwtSecret")
  public String shareJwtSecret() {
    return shareSecret;
  }

  /** Expose the raw anon/observer secret so test utilities can mint tokens. */
  @Bean(name = "anonJwtSecret")
  public String anonJwtSecret() {
    return anonSecret;
  }
}
