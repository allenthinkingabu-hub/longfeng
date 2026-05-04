package com.longfeng.anonymous.guest;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 429 response body for {@code POST /api/guest/analyze} when the daily quota is exhausted.
 *
 * <p>FE checks {@code analyzeRes.status === 429} and renders a "今日额度已用完" banner. The body is
 * informational; FE only needs the status code.
 *
 * @param error    machine-readable error tag (always {@code QUOTA_EXHAUSTED})
 * @param resetAt  ISO-8601 timestamp when the next free quota becomes available
 */
public record GuestQuotaExhaustedResponse(
    String error,
    @JsonProperty("reset_at") String resetAt) {}
