package com.longfeng.wrongbook.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SetDifficultyReq(@NotNull @Min(1) @Max(5) Short level) {}
