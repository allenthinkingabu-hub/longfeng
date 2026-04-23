package com.longfeng.aianalysis.event;

import java.time.Instant;

/** Mirror of S3's WrongItemChangedEvent (thin payload · Q5-R2). */
public record ItemChangedEvent(Long itemId, String action, Long version, Instant occurredAt) {}
