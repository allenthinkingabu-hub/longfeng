package com.longfeng.gateway.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

/**
 * Loads the RSA public key used by {@code JwtAuthFilter} · 落地计划 §6.6.
 *
 * <p>Dev: resolves {@code docs/dev/jwt-dev-pubkey.pem} relative to cwd. Prod (S10): replaced by
 * External Secrets mounted at the same path via env {@code JWT_PUBLIC_KEY_PATH}.
 */
@Configuration
public class JwtKeyConfig {

  private static final Logger log = LoggerFactory.getLogger(JwtKeyConfig.class);

  @Value("${jwt.public-key-path:docs/dev/jwt-dev-pubkey.pem}")
  private String publicKeyPath;

  @Bean
  public RSAPublicKey jwtPublicKey() throws Exception {
    String pem = readPem();
    String base64 =
        pem.replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s+", "");
    byte[] der = Base64.getDecoder().decode(base64);
    KeyFactory kf = KeyFactory.getInstance("RSA");
    return (RSAPublicKey) kf.generatePublic(new X509EncodedKeySpec(der));
  }

  private String readPem() throws IOException {
    Path path = Path.of(publicKeyPath);
    if (Files.exists(path)) {
      log.info("loading JWT public key from filesystem: {}", path.toAbsolutePath());
      return Files.readString(path, StandardCharsets.UTF_8);
    }
    Resource classpath = new ClassPathResource(publicKeyPath);
    if (classpath.exists()) {
      log.info("loading JWT public key from classpath: {}", publicKeyPath);
      return new String(classpath.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
    throw new IOException("JWT public key not found at " + publicKeyPath);
  }
}
