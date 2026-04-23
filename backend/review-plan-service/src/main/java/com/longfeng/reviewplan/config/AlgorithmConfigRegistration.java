package com.longfeng.reviewplan.config;

import com.longfeng.reviewplan.algo.AlgorithmConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 激活 {@link AlgorithmConfig} @ConfigurationProperties bean · review.sm2.* 自动绑定. */
@Configuration
@EnableConfigurationProperties(AlgorithmConfig.class)
public class AlgorithmConfigRegistration {}
