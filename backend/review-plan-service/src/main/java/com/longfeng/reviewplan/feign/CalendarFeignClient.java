package com.longfeng.reviewplan.feign;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * calendar-platform core-service Feign · SC-10.AC-1.
 *
 * <p>熔断走 Sentinel（{@code @SentinelResource}）· fallback {@link CalendarFeignClientFallback}。
 * url 默认指向 localhost · 实战由 Nacos 服务发现替换。
 */
@FeignClient(
    name = "core-service",
    url = "${calendar.core-service.url:http://localhost:18080}",
    fallback = CalendarFeignClientFallback.class)
public interface CalendarFeignClient {

  @GetMapping("/calendar/nodes")
  List<Map<String, Object>> getNodes(
      @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date);
}
