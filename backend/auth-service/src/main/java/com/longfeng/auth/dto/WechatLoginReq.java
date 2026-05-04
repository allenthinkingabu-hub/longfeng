package com.longfeng.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** WeChat login request DTO · S7 BUG-LF-09. */
public record WechatLoginReq(
    @NotBlank @JsonProperty("wx_code") String wxCode,
    @NotBlank @JsonProperty("device_fp") String deviceFp,
    @NotNull @JsonProperty("consent_accepted") Boolean consentAccepted) {}
