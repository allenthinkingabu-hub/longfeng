package com.longfeng.anonymous.analytics;

import com.longfeng.anonymous.entity.AnalyticsEvent;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link AnalyticsEvent}.
 *
 * <p>Inserts only — analytics events are append-only telemetry. Reads are out-of-band
 * (BI / data warehouse). C3: only touches {@code anon.analytics_event}.
 */
public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, Long> {
}
