package com.longfeng.reviewplan.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** POST /review-plans/{id}/complete request body · SC-08.AC-1. */
public record CompleteReviewReq(
    @NotNull @Min(0) @Max(5) Integer quality) {}
