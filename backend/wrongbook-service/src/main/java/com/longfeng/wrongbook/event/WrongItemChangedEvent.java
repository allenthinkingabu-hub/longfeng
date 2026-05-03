package com.longfeng.wrongbook.event;

import java.time.Instant;

/**
 * Thin payload (Q5-R2 resolution) for topic {@code wrongbook_item_changed}.
 * Only id/action/version/occurredAt — downstream fetches details via GET /wrongbook/items/{id}.
 *
 * <p>S7 Issue 6: topic renamed from {@code wrongbook.item.changed} (contains dots, violates
 * RocketMQ naming rule {@code ^[%|a-zA-Z0-9_-]+$}) to {@code wrongbook_item_changed}.
 */
public record WrongItemChangedEvent(Long itemId, String action, Long version, Instant occurredAt) {
  public static final String TOPIC = "wrongbook_item_changed";
  public static final String ACTION_CREATED = "created";
  public static final String ACTION_UPDATED = "updated";
  public static final String ACTION_DELETED = "deleted";
}
