package com.longfeng.fileservice.exception;

/** SC-11.AC-1 error_paths.1 · 400 FILE_TOO_LARGE. */
public class OversizeException extends RuntimeException {
  private final long size;
  private final long max;

  public OversizeException(long size, long max) {
    super("file size " + size + " exceeds max " + max);
    this.size = size;
    this.max = max;
  }

  public long size() { return size; }
  public long max() { return max; }
}
