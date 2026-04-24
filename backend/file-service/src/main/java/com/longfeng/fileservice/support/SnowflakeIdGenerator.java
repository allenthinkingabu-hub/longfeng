package com.longfeng.fileservice.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Same Snowflake layout as wrongbook / ai-analysis / review-plan · S6 worker-id=6. */
@Component
public class SnowflakeIdGenerator {

  private static final long EPOCH_MILLIS = 1_700_000_000_000L;
  private static final long WORKER_BITS = 10L;
  private static final long SEQ_BITS = 12L;
  private static final long WORKER_MASK = (1L << WORKER_BITS) - 1;
  private static final long SEQ_MASK = (1L << SEQ_BITS) - 1;
  private static final long TIMESTAMP_SHIFT = WORKER_BITS + SEQ_BITS;

  private final long workerId;
  private long lastTimestamp = -1L;
  private long sequence = 0L;

  public SnowflakeIdGenerator(@Value("${snowflake.worker-id:6}") long workerId) {
    this.workerId = workerId & WORKER_MASK;
  }

  public synchronized long nextId() {
    long ts = System.currentTimeMillis();
    if (ts < lastTimestamp) ts = lastTimestamp;
    if (ts == lastTimestamp) {
      sequence = (sequence + 1) & SEQ_MASK;
      if (sequence == 0L) {
        while (ts <= lastTimestamp) ts = System.currentTimeMillis();
      }
    } else {
      sequence = 0L;
    }
    lastTimestamp = ts;
    return ((ts - EPOCH_MILLIS) << TIMESTAMP_SHIFT) | (workerId << SEQ_BITS) | sequence;
  }
}
