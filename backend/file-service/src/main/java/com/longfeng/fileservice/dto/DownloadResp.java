package com.longfeng.fileservice.dto;

/** GET /files/download/{fileKey} response · SC-11.AC-3. */
public record DownloadResp(String downloadUrl, String variant, long ttlSeconds) {}
