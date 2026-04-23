package com.longfeng.wrongbook.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateAttemptReq(
    @NotNull Long studentId,
    String answerText,
    @NotNull Boolean isCorrect,
    @Min(0) Short durationSec,
    @Pattern(regexp = "app|web|mp|admin") String clientSource) {}
