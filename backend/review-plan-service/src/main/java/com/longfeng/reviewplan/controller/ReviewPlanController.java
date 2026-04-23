package com.longfeng.reviewplan.controller;

import com.longfeng.common.dto.ApiResult;
import com.longfeng.reviewplan.dto.CompleteReviewReq;
import com.longfeng.reviewplan.dto.CompleteReviewResp;
import com.longfeng.reviewplan.service.ReviewPlanService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 复习计划 REST 入口 · SC-08.AC-1 POST complete. */
@RestController
@RequestMapping("/review-plans")
public class ReviewPlanController {

  private final ReviewPlanService service;

  @Autowired
  public ReviewPlanController(ReviewPlanService service) {
    this.service = service;
  }

  /** POST /review-plans/{id}/complete · SC-08.AC-1. */
  @PostMapping("/{id}/complete")
  public ApiResult<CompleteReviewResp> complete(
      @PathVariable Long id, @Valid @RequestBody CompleteReviewReq req) {
    ReviewPlanService.CompleteResult r = service.complete(id, req.quality());
    return ApiResult.ok(
        new CompleteReviewResp(r.planId(), r.nextReviewAt(), r.easeFactorAfter(), r.mastered()));
  }
}
