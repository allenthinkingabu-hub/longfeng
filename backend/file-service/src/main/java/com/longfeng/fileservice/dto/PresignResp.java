package com.longfeng.fileservice.dto;

/** POST /files/presign response · SC-11.AC-1. */
public record PresignResp(String uploadUrl, String fileKey, String expiresAt, long ttlSeconds) {}
