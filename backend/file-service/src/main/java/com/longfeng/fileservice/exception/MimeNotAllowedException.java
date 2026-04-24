package com.longfeng.fileservice.exception;

/** SC-11.AC-1 error_paths.0 · 400 MIME_NOT_ALLOWED. */
public class MimeNotAllowedException extends RuntimeException {
  private final String mime;

  public MimeNotAllowedException(String mime) {
    super("mime not allowed: " + mime);
    this.mime = mime;
  }

  public String mime() {
    return mime;
  }
}
