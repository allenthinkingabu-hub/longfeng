package com.longfeng.wrongbook.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record AddTagReq(
    @NotBlank @Size(max = 64) String tagCode,
    @DecimalMin("0.000") @DecimalMax("1.000") BigDecimal weight) {}
