package com.longfeng.auth.util;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * JWT RS256 signing utility · auth-service · S7 BUG-LF-09.
 *
 * <p>Loads the RSA private key from {@code auth.jwt.private-key-path} (dev: docs/dev/jwt-dev-privkey.pem).
 * Uses nimbus-jose-jwt to sign JWTs (same library as gateway uses for verification).
 */
@Component
public class JwtUtils {

  private static final Logger log = LoggerFactory.getLogger(JwtUtils.class);

  @Value("${auth.jwt.private-key-path:docs/dev/jwt-dev-privkey.pem}")
  private String privateKeyPath;

  private volatile RSAPrivateKey privateKey;

  /**
   * Signs a JWT with the given claims using RS256.
   *
   * @param userId subject (user id)
   * @param role user role
   * @param deviceFp device fingerprint
   * @param expiresAt token expiry
   * @return signed JWT string
   */
  public String sign(Long userId, String role, String deviceFp, Instant expiresAt) {
    try {
      RSAPrivateKey key = getPrivateKey();
      RSASSASigner signer = new RSASSASigner(key);

      JWTClaimsSet claims = new JWTClaimsSet.Builder()
          .subject(userId.toString())
          .claim("role", role)
          .claim("device_fp", deviceFp)
          .claim("tier", "NORMAL")
          .issueTime(new Date())
          .expirationTime(Date.from(expiresAt))
          .build();

      JWSHeader header = new JWSHeader(JWSAlgorithm.RS256);
      SignedJWT jwt = new SignedJWT(header, claims);
      jwt.sign(signer);
      return jwt.serialize();
    } catch (Exception e) {
      throw new IllegalStateException("JWT signing failed: " + e.getMessage(), e);
    }
  }

  private RSAPrivateKey getPrivateKey() throws Exception {
    if (privateKey == null) {
      synchronized (this) {
        if (privateKey == null) {
          privateKey = loadPrivateKey();
        }
      }
    }
    return privateKey;
  }

  private RSAPrivateKey loadPrivateKey() throws Exception {
    String pem = readPem();
    String base64 = pem
        .replace("-----BEGIN PRIVATE KEY-----", "")
        .replace("-----END PRIVATE KEY-----", "")
        .replace("-----BEGIN RSA PRIVATE KEY-----", "")
        .replace("-----END RSA PRIVATE KEY-----", "")
        .replaceAll("\\s+", "");
    byte[] der = Base64.getDecoder().decode(base64);
    KeyFactory kf = KeyFactory.getInstance("RSA");
    return (RSAPrivateKey) kf.generatePrivate(new PKCS8EncodedKeySpec(der));
  }

  private String readPem() throws IOException {
    // 1. Try filesystem path as-is (absolute or relative to cwd)
    Path path = Path.of(privateKeyPath);
    if (Files.exists(path)) {
      log.info("loading JWT private key from filesystem: {}", path.toAbsolutePath());
      return Files.readString(path, StandardCharsets.UTF_8);
    }
    // 2. Try classpath with full path
    Resource classpath = new ClassPathResource(privateKeyPath);
    if (classpath.exists()) {
      log.info("loading JWT private key from classpath: {}", privateKeyPath);
      return new String(classpath.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
    // 3. Try classpath with just the filename (e.g. jwt-dev-privkey.pem)
    String filename = path.getFileName().toString();
    Resource classpathFallback = new ClassPathResource(filename);
    if (classpathFallback.exists()) {
      log.info("loading JWT private key from classpath fallback: {}", filename);
      return new String(classpathFallback.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
    throw new IOException("JWT private key not found at " + privateKeyPath
        + " (also tried classpath:" + filename + ")");
  }
}
