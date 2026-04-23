package com.longfeng.wrongbook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ConfirmImageReq(
    @NotBlank @Size(max = 512) String objectKey,
    @NotBlank @Pattern(regexp = "ORIGIN|PROCESSED|CROP|WATERMARK") String role,
    Integer widthPx,
    Integer heightPx,
    Long byteSize,
    @Size(max = 64) String contentType) {}
