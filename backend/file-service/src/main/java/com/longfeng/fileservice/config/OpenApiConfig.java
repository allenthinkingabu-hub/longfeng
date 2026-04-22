package com.longfeng.fileservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** springdoc grouping + OpenAPI metadata · 落地计划 §6.7 Step 10. */
@Configuration
public class OpenApiConfig {

  @Value("${spring.application.name:file-service}")
  private String appName;

  @Bean
  public GroupedOpenApi defaultApi() {
    return GroupedOpenApi.builder().group("default").pathsToMatch("/**").build();
  }

  @Bean
  public OpenAPI meta() {
    return new OpenAPI().info(new Info().title(appName).version("1.0.0"));
  }
}
