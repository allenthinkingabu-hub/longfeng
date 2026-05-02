package com.longfeng.fileservice.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 激活 JPA + ConfigurationProperties · file-service.
 *
 * <p>Entity scan covers both the legacy FileAsset entity and the new WbFile / WbFileLifecycle
 * entities added in BE-04-file (TDD §4.14 / V1.0.080 / V1.0.081).
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
@EnableJpaRepositories(basePackages = "com.longfeng.fileservice.repo")
@EntityScan(basePackages = "com.longfeng.fileservice.entity")
@EnableJpaAuditing
public class StorageConfigRegistration {}
