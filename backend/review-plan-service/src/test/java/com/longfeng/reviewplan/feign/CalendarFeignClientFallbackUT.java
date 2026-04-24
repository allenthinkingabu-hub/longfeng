package com.longfeng.reviewplan.feign;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.longfeng.common.test.CoversAC;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * V-S5-09 · SC-10.AC-1 Feign 熔断 fallback 单测.
 *
 * <p>IT 里 Sentinel + Nacos + 真 core-service stub 的完整熔断链路 成本过高 · 本 UT 只断言 fallback
 * class 本身的行为（Caffeine cache hit/miss）· 配合 arch §2.4 sequence diagram 描述.
 *
 * <p>complete Sentinel circuit IT 留 S10 / staging 环境补（见 TODO）.
 */
class CalendarFeignClientFallbackUT {

  @Test
  @DisplayName("SC-10.AC-1 happy_path.0 · cache hit · fallback 返回 cached nodes")
  @CoversAC("SC-10.AC-1#happy_path.0")
  void scenario_sc10_ac1_happy_path_0_cache_hit_returns_cached() {
    Cache<String, List<Map<String, Object>>> cache =
        Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(10)).build();
    LocalDate d = LocalDate.parse("2026-04-24");
    List<Map<String, Object>> cached = List.of(Map.of("node_id", 1L, "title", "复习 T0"));
    cache.put(d.toString(), cached);

    CalendarFeignClientFallback fallback = new CalendarFeignClientFallback(cache);
    List<Map<String, Object>> out = fallback.getNodes(d);

    assertThat(out).hasSize(1).containsExactly(Map.of("node_id", 1L, "title", "复习 T0"));
  }

  @Test
  @DisplayName("SC-10.AC-1 error_paths.1 · cache miss · fallback 返 empty（上层 503）")
  @CoversAC("SC-10.AC-1#error_paths.1")
  void scenario_sc10_ac1_error_paths_1_cache_miss_empty() {
    Cache<String, List<Map<String, Object>>> cache =
        Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(10)).build();
    CalendarFeignClientFallback fallback = new CalendarFeignClientFallback(cache);

    List<Map<String, Object>> out = fallback.getNodes(LocalDate.parse("2026-04-24"));

    assertThat(out).isEmpty();
  }

  @Test
  @DisplayName("cache expired 等同 cache miss · 返 empty")
  void cache_expired_returns_empty() {
    Cache<String, List<Map<String, Object>>> cache =
        Caffeine.newBuilder().expireAfterWrite(Duration.ofMillis(1)).build();
    cache.put("2026-04-24", List.of(Map.of("x", 1)));
    // wait expire · Caffeine policy lazy · invalidateAll 保证测试确定性
    cache.invalidateAll();

    CalendarFeignClientFallback fallback = new CalendarFeignClientFallback(cache);
    List<Map<String, Object>> out = fallback.getNodes(LocalDate.parse("2026-04-24"));

    assertThat(out).isEmpty();
  }
}
