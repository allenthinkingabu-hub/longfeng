package com.longfeng.reviewplan.controller;

import com.longfeng.common.dto.ApiResult;
import com.longfeng.reviewplan.dto.CompleteReviewReq;
import com.longfeng.reviewplan.dto.CompleteReviewResp;
import com.longfeng.reviewplan.dto.ReviewStatsResp;
import com.longfeng.reviewplan.service.ReviewPlanService;
import com.longfeng.reviewplan.service.ReviewStatsService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 复习计划 REST 入口 · SC-08.AC-1 POST complete. */
@RestController
public class ReviewPlanController {

  /** 真实 S5 环境由 JWT 解析 userId · 本 Phase 暂从 header 取 · S10 接 JwtAuthFilter 时替换. */
  private static final String USER_ID_HEADER = "X-User-Id";
  private static final String TIMEZONE_HEADER = "X-User-Timezone";

  private final ReviewPlanService service;
  private final ReviewStatsService statsService;

  @Autowired
  public ReviewPlanController(ReviewPlanService service, ReviewStatsService statsService) {
    this.service = service;
    this.statsService = statsService;
  }

  /** POST /review-plans/{id}/complete · SC-08.AC-1. */
  @PostMapping("/review-plans/{id}/complete")
  public ApiResult<CompleteReviewResp> complete(
      @PathVariable Long id, @Valid @RequestBody CompleteReviewReq req) {
    ReviewPlanService.CompleteResult r = service.complete(id, req.quality());
    return ApiResult.ok(
        new CompleteReviewResp(r.planId(), r.nextReviewAt(), r.easeFactorAfter(), r.mastered()));
  }

  /** GET /review-stats · SC-09.AC-1 · 日视图 + Top N 薄弱项 · timezone 切日. */
  @GetMapping("/review-stats")
  public ApiResult<ReviewStatsResp> reviewStats(
      @RequestParam("range") String range,
      @RequestParam(value = "subject", required = false) String subject,
      @RequestHeader(value = USER_ID_HEADER, defaultValue = "0") Long userId,
      @RequestHeader(value = TIMEZONE_HEADER, required = false) String timezone) {
    return ApiResult.ok(statsService.aggregate(userId, range, subject, timezone));
  }
}
