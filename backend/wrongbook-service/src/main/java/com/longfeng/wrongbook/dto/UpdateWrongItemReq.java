package com.longfeng.wrongbook.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateWrongItemReq(
    @NotNull Long version, String stemText, String ocrText, @Min(0) @Max(9) Short status,
    @Min(0) @Max(2) Short mastery) {}
