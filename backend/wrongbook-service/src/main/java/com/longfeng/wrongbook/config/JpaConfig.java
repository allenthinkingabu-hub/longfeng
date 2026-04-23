package com.longfeng.wrongbook.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** QueryDSL JPAQueryFactory · see design/arch/s3-wrongbook.md §6 dependency row. */
@Configuration
public class JpaConfig {

  @PersistenceContext private EntityManager em;

  @Bean
  public JPAQueryFactory jpaQueryFactory() {
    return new JPAQueryFactory(em);
  }
}
