package com.longfeng.fileservice.dto;

/** POST /files/complete/{fileKey} response · SC-11.AC-2. */
public record CompleteResp(
    String fileKey,
    String status,
    String variantThumbKey,
    String variantMediumKey) {}
