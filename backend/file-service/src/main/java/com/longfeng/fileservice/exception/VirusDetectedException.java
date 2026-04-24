package com.longfeng.fileservice.exception;

/** SC-11.AC-2 error_paths.1 · 422 VIRUS_DETECTED. */
public class VirusDetectedException extends RuntimeException {
  private final String fileKey;
  private final String reason;

  public VirusDetectedException(String fileKey, String reason) {
    super("virus detected: " + fileKey + " reason=" + reason);
    this.fileKey = fileKey;
    this.reason = reason;
  }

  public String fileKey() { return fileKey; }
  public String reason() { return reason; }
}
