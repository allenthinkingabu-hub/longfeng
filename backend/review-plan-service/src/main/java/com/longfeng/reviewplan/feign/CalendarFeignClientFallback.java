package com.longfeng.reviewplan.feign;

import com.github.benmanes.caffeine.cache.Cache;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Feign fallback · 返回 Caffeine 10min cache · SC-10.AC-1 error_paths.0/1.
 *
 * <p>当 core-service 超时 / 5xx · Feign 自动转发本实例。cache 未命中则返空 list · 上层
 * Controller 视业务需要判 503 CALENDAR_DEPENDENCY_DOWN.
 */
@Component
public class CalendarFeignClientFallback implements CalendarFeignClient {

  private static final Logger LOG = LoggerFactory.getLogger(CalendarFeignClientFallback.class);

  private final Cache<String, List<Map<String, Object>>> cache;

  @Autowired
  public CalendarFeignClientFallback(Cache<String, List<Map<String, Object>>> calendarCache) {
    this.cache = calendarCache;
  }

  @Override
  public List<Map<String, Object>> getNodes(LocalDate date) {
    String key = date.toString();
    var cached = cache.getIfPresent(key);
    if (cached != null) {
      LOG.warn("calendar core-service fallback · source=cache · date={}", date);
      return cached;
    }
    LOG.error("calendar core-service fallback · cache MISS · date={} · return empty", date);
    return List.of();
  }
}
