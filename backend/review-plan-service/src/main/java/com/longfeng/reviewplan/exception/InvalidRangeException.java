package com.longfeng.reviewplan.exception;

/** SC-09.AC-1 error_paths.0 · 400 INVALID_RANGE · range 非 week/month/quarter. */
public class InvalidRangeException extends RuntimeException {
  private final String range;

  public InvalidRangeException(String range) {
    super("invalid range: " + range + " · expected one of [week, month, quarter]");
    this.range = range;
  }

  public String range() {
    return range;
  }
}
