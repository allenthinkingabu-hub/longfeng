package com.longfeng.anonymous.guest;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body of {@code POST /api/guest/analyze}.
 *
 * <p>FE sends snake_case keys ({@code device_fp}, {@code image_url}). Java fields stay
 * camelCase via {@link JsonProperty} mapping.
 *
 * @param deviceFp 5-source composite fingerprint (must not be blank)
 * @param subject  selected subject (math / physics / chemistry / english) — currently informational
 * @param imageUrl presigned/stored URL of the uploaded photo
 */
public record GuestAnalyzeRequest(
    @JsonProperty("device_fp") @NotBlank String deviceFp,
    @NotBlank String subject,
    @JsonProperty("image_url") @NotBlank String imageUrl) {}
