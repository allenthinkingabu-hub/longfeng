package com.longfeng.anonymous.landing;

import com.longfeng.anonymous.landing.SampleCardDto.AiAnalysisMockDto;
import com.longfeng.anonymous.ratelimit.LandingRateLimiter;
import com.longfeng.anonymous.support.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * LandingController · {@code /api/landing/*} · plan §S7 BUG-LF-09 fix.
 *
 * <p>Two endpoints serve the public landing page:
 * <ul>
 *   <li>{@code GET /samples?bucket=default} — sample wrong-question cards for the carousel
 *   <li>{@code GET /kpi} — top-line KPI banner numbers
 * </ul>
 *
 * <p>Response shape is camelCase bare JSON (no ApiResult wrapper) to match the FE schema in
 * {@code frontend/apps/h5/src/__mocks__/handlers/guest.ts}.
 *
 * <p>Both endpoints are rate-limited to 30 req/min/IP via {@link LandingRateLimiter}
 * (Cloudflare is the first tier; this is the application-level second tier).
 *
 * <p>TODO(post-S7): replace hard-coded sample data with a {@code landing_sample} admin-managed
 * table + Redis cache. Tracking ticket: BUG-LF-09 follow-up.
 */
@RestController
@RequestMapping("/api/landing")
public class LandingController {

  private static final Logger log = LoggerFactory.getLogger(LandingController.class);

  private final LandingRateLimiter rateLimiter;

  // Hard-coded sample data — kept 1:1 with FE MSW mock handler so visual stays unchanged.
  // See: frontend/apps/h5/src/__mocks__/handlers/guest.ts (Round 4 enrichment).
  private static final List<SampleCardDto> DEFAULT_SAMPLES = List.of(
      new SampleCardDto(
          "sample-1",
          "math",
          "已知函数 f(x)=x²-4x+3，求顶点坐标",
          "/mock/math.png",
          "f(x)=x²-4x+3",
          "错因 · 配方法符号错",
          "知识点 · 二次函数顶点式",
          "T1 · 1h 后复习",
          new AiAnalysisMockDto(
              "配方法时常数项符号易丢 · 应配 (x-2)²-1 · 顶点 (2,-1)",
              3,
              "记忆口诀: 一移二配三还原")),
      new SampleCardDto(
          "sample-2",
          "physics",
          "斜面 θ=30° 滑块沿斜面下滑，求加速度",
          "/mock/physics.png",
          "a = g(sinθ - μcosθ)",
          "错因 · 分解方向选错",
          "知识点 · 共点力 / 斜面受力分解",
          "T2 · 1d 后复习",
          new AiAnalysisMockDto(
              "应沿斜面方向分解重力 · 分量 mg·sinθ 才是下滑力",
              4,
              "画力图先分解后列方程")),
      new SampleCardDto(
          "sample-3",
          "english",
          "If I ___ you, I would take the chance.",
          "/mock/english.png",
          "If I ___ you, I would ...",
          "错因 · 虚拟语气时态",
          "知识点 · 虚拟语气 / 与现在事实相反",
          "T3 · 3d 后复习",
          new AiAnalysisMockDto(
              "与现在事实相反 · be 动词统一用 were · 答案是 were",
              2,
              "虚拟语气主从呼应: were / would / could")));

  // Hard-coded KPI snapshot — TODO replace with a metrics-rollup query (cron-warmed cache).
  private static final LandingKpiResponse DEFAULT_KPI = new LandingKpiResponse(
      1_204_312L,
      0.47,
      "已分析 120w+ 错题");

  public LandingController(LandingRateLimiter rateLimiter) {
    this.rateLimiter = rateLimiter;
  }

  @GetMapping("/samples")
  public LandingSamplesResponse samples(
      @RequestParam(name = "bucket", defaultValue = "default") String bucket,
      HttpServletRequest req) {
    rateLimiter.checkAndConsume(ClientIpResolver.resolve(req));
    log.debug("landing-samples requested bucket={}", bucket);
    // Currently bucket is ignored — single sample set. TODO: A/B-bucket lookup table.
    return new LandingSamplesResponse(bucket, DEFAULT_SAMPLES);
  }

  @GetMapping("/kpi")
  public LandingKpiResponse kpi(HttpServletRequest req) {
    rateLimiter.checkAndConsume(ClientIpResolver.resolve(req));
    return DEFAULT_KPI;
  }
}
