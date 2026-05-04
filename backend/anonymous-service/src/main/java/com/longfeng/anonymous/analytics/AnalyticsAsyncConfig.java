package com.longfeng.anonymous.analytics;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Async executor for {@link AnalyticsEventService}.
 *
 * <p>Small bounded pool — analytics traffic is sparse and never request-critical.
 * Bounded queue + caller-runs rejection guarantees we never silently drop events.
 */
@Configuration
@EnableAsync
public class AnalyticsAsyncConfig {

  @Bean(name = "analyticsExecutor")
  public Executor analyticsExecutor() {
    ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
    exec.setCorePoolSize(2);
    exec.setMaxPoolSize(4);
    exec.setQueueCapacity(256);
    exec.setThreadNamePrefix("analytics-");
    exec.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
    exec.initialize();
    return exec;
  }
}
