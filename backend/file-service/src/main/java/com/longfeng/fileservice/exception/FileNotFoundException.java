package com.longfeng.fileservice.exception;

/** SC-11.AC-2 error_paths.0 / SC-11.AC-3 · 404 FILE_NOT_FOUND. */
public class FileNotFoundException extends RuntimeException {
  private final String fileKey;

  public FileNotFoundException(String fileKey) {
    super("file not found: " + fileKey);
    this.fileKey = fileKey;
  }

  public String fileKey() {
    return fileKey;
  }
}
