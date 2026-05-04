package com.longfeng.anonymous.guest;

/**
 * Response body of {@code GET /api/guest/quota}.
 *
 * <p>FE schema (camelCase) — see
 * {@code frontend/apps/h5/src/__mocks__/handlers/guest.ts}.
 *
 * @param quotaRemaining 0 or 1 — how many free analyses the device still has today
 * @param quotaResetAt   ISO-8601 timestamp when the daily window resets (00:00 Asia/Shanghai)
 */
public record GuestQuotaResponse(int quotaRemaining, String quotaResetAt) {}
