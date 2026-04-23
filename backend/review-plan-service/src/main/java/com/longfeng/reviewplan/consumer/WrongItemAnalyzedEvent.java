package com.longfeng.reviewplan.consumer;

import java.time.Instant;

/** S4 发出的 wrongbook.item.analyzed 事件 payload · SC-07.AC-1 入站. */
public record WrongItemAnalyzedEvent(Long itemId, Long userId, String subject, Instant analyzedAt) {}
