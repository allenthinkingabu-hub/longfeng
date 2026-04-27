package com.longfeng.aianalysis.controller;

import com.longfeng.aianalysis.llm.ProviderRouter;
import com.longfeng.common.dto.ApiResult;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * K8s-style probes (落地计划 §6.7 Step 9) plus the spec-mandated {@code GET /health}
 * domain probe that reflects the active LLM provider (be-build-spec key_rules ·
 * "GET /health 探针 · 返回 { status: 'UP', provider: <name> }").
 */
@RestController
public class HealthController {

  private final ProviderRouter router;

  public HealthController(ProviderRouter router) {
    this.router = router;
  }

  @GetMapping("/ready")
  public ApiResult<String> ready() {
    return ApiResult.ok("READY");
  }

  @GetMapping("/live")
  public ApiResult<String> live() {
    return ApiResult.ok("LIVE");
  }

  /** Spec-mandated domain probe · returns {@code { status: 'UP', provider: <name> }}. */
  @GetMapping("/health")
  public Map<String, String> health() {
    return Map.of("status", "UP", "provider", router.currentProviderName());
  }
}
