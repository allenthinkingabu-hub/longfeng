package com.longfeng.fileservice.controller;

import com.longfeng.common.dto.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** K8s-style probes · 落地计划 §6.7 Step 9. */
@RestController
public class HealthController {

  @GetMapping("/ready")
  public ApiResult<String> ready() {
    return ApiResult.ok("READY");
  }

  @GetMapping("/live")
  public ApiResult<String> live() {
    return ApiResult.ok("LIVE");
  }
}
