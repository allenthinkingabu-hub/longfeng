package com.longfeng.reviewplan.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Caffeine cache · calendar/nodes · TTL 10min · size 1000 · SC-10.AC-1. */
@Configuration
public class CalendarCacheConfig {

  @Bean
  public Cache<String, List<Map<String, Object>>> calendarCache() {
    return Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(10))
        .maximumSize(1000)
        .recordStats()
        .build();
  }
}
