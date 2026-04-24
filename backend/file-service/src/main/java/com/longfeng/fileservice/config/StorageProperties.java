package com.longfeng.fileservice.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** S6 存储 Feature Flag + 配置 · A1 决策 · Q-D bucket 策略 · Q-B OSS 留 S10. */
@Validated
@ConfigurationProperties(prefix = "file-service.storage")
public record StorageProperties(
    @NotBlank String provider,          // minio | oss · A1 决策
    @NotBlank String bucket,            // Q-D: wrongbook-staging 默认（分 bucket · 非前缀）
    @NotBlank String endpoint,
    @NotBlank String accessKey,
    @NotBlank String secretKey,
    @NotNull @Min(60) @Max(900) Integer presignTtlSeconds,   // A5: TTL ≤ 900s
    @NotNull @Min(1024) @Max(10485760) Long maxUploadSize) { // A4: 10MB
}
