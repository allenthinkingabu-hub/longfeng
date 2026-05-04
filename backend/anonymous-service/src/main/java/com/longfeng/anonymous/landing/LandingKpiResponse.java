package com.longfeng.anonymous.landing;

/**
 * Response body of {@code GET /api/landing/kpi}.
 *
 * <p>Field names are camelCase to match the FE schema. {@code retention7d} is a fraction
 * (0..1), not a percentage — the FE multiplies by 100 for display.
 *
 * @param totalQuestionsAnalyzed all-time count of analyzed questions across all users
 * @param retention7d            7-day retention fraction (0..1)
 * @param headline               pre-rendered headline string for the hero KPI banner
 */
public record LandingKpiResponse(
    long totalQuestionsAnalyzed,
    double retention7d,
    String headline) {}
