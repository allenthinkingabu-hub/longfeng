package com.longfeng.wrongbook.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** Jackson JSR-310 for Instant / OffsetDateTime — §7.4 allowlist. */
@Configuration
public class JsonConfig {

  @Bean
  @Primary
  public ObjectMapper objectMapper() {
    ObjectMapper om = new ObjectMapper();
    om.registerModule(new JavaTimeModule());
    om.configure(
        com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    return om;
  }
}
