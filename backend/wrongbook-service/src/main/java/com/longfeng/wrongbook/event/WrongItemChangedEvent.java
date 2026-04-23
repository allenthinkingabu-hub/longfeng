package com.longfeng.wrongbook.event;

import java.time.Instant;

/**
 * Thin payload (Q5-R2 resolution) for topic {@code wrongbook.item.changed}. Only id/action/version
 * /occurredAt — downstream fetches details via GET /wrongbook/items/{id}.
 */
public record WrongItemChangedEvent(Long itemId, String action, Long version, Instant occurredAt) {
  public static final String TOPIC = "wrongbook.item.changed";
  public static final String ACTION_CREATED = "created";
  public static final String ACTION_UPDATED = "updated";
  public static final String ACTION_DELETED = "deleted";
}
