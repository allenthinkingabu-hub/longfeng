package com.longfeng.reviewplan.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** 启用 JPA / Feign 扫描 · review-plan-service. */
@Configuration
@EnableFeignClients(basePackages = "com.longfeng.reviewplan.feign")
@EnableJpaRepositories(basePackages = "com.longfeng.reviewplan.repo")
@EntityScan(basePackages = "com.longfeng.reviewplan.entity")
@EnableJpaAuditing
public class FeignAndJpaConfig {}
