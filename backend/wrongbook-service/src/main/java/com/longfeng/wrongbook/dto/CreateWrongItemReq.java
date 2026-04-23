package com.longfeng.wrongbook.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateWrongItemReq(
    @NotNull Long studentId,
    @NotBlank @Size(max = 16) String subject,
    @Size(max = 16) String gradeCode,
    @NotNull @Min(1) @Max(5) Short sourceType,
    @Size(max = 512) String originImageKey,
    @Size(max = 512) String processedImageKey,
    String ocrText,
    String stemText,
    @Min(1) @Max(5) Short difficulty) {}
