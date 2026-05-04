package com.longfeng.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/** WeChat login response DTO · S7 BUG-LF-09. */
public record WechatLoginResp(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("refresh_token") String refreshToken,
    @JsonProperty("student_id") String studentId,
    @JsonProperty("is_new_user") boolean isNewUser,
    @JsonProperty("expires_at") Instant expiresAt) {}
