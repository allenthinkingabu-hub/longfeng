package com.longfeng.reviewplan.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** POST /review-plans/{id}/complete response · SC-08.AC-1. */
public record CompleteReviewResp(
    Long planId, Instant nextReviewAt, BigDecimal easeFactorAfter, boolean mastered) {}
