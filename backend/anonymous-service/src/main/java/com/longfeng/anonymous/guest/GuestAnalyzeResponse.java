package com.longfeng.anonymous.guest;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Success response body of {@code POST /api/guest/analyze}.
 *
 * <p>FE expects snake_case keys ({@code guest_session_id}, {@code task_id}, {@code status}).
 *
 * @param guestSessionId snowflake id of the created guest session row
 * @param taskId         id of the downstream AI analysis task (mock UUID until WT4 wires the real Feign call)
 * @param status         lifecycle hint — currently always {@code ANALYZING}
 */
public record GuestAnalyzeResponse(
    @JsonProperty("guest_session_id") String guestSessionId,
    @JsonProperty("task_id") String taskId,
    String status) {}
