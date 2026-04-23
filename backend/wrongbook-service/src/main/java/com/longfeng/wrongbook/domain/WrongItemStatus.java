package com.longfeng.wrongbook.domain;

import java.util.Set;

/**
 * wrong_item.status numeric codes — D3 resolution (Q3-R1). DDL CHECK IN (0,1,2,3,8,9).
 * State machine edges documented in design/arch/s3-wrongbook.md §2.2.
 */
public enum WrongItemStatus {
  DRAFT((short) 0),
  ANALYZED((short) 1),
  SCHEDULED((short) 2),
  REVIEWED((short) 3),
  MASTERED((short) 8),
  ARCHIVED((short) 9);

  private final short code;

  WrongItemStatus(short code) {
    this.code = code;
  }

  public short code() {
    return code;
  }

  public static WrongItemStatus fromCode(short code) {
    for (var s : values()) {
      if (s.code == code) {
        return s;
      }
    }
    throw new IllegalArgumentException("unknown wrong_item.status code: " + code);
  }

  /** Allowed outbound transitions from the current state. Invariant INV-01. */
  public Set<WrongItemStatus> allowedNext() {
    return switch (this) {
      case DRAFT -> Set.of(ANALYZED, ARCHIVED);
      case ANALYZED -> Set.of(SCHEDULED, ARCHIVED);
      case SCHEDULED -> Set.of(REVIEWED, ARCHIVED);
      case REVIEWED -> Set.of(SCHEDULED, MASTERED, ARCHIVED);
      case MASTERED -> Set.of(SCHEDULED, ARCHIVED);
      case ARCHIVED -> Set.of();
    };
  }
}
