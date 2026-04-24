package com.longfeng.fileservice.provider;

import java.io.InputStream;
import java.time.Duration;

/**
 * S6 存储抽象 SPI · ADR 0009 · 双实现切 minio / oss（Q-B · 本 Phase 仅 MinioProvider · OssProvider
 * stub · AK/SK vault 接入留 S10）.
 */
public interface StorageProvider {

  /** 预签名上传 URL · TTL ≤ 900s（A5）· 返回 {uploadUrl, fileKey}. */
  PresignResult presignUpload(String bucket, String objectKey, String mime, Duration ttl);

  /** 预签名下载 URL · TTL ≤ 900s. */
  String presignDownload(String bucket, String objectKey, Duration ttl);

  /** 读取对象（用于 complete 后处理 · 读 + webp 转 + EXIF strip + 回写）. */
  InputStream readObject(String bucket, String objectKey);

  /** 写对象（回写 webp/thumb/medium variant）. */
  void putObject(String bucket, String objectKey, InputStream content, long size, String mime);

  /** 软删 · 本 Phase 只操作 file_asset 元数据 · OSS object 实体由 S10 job 延迟 30d 硬删（A7）. */
  void deleteObject(String bucket, String objectKey);

  /** provider 名称（minio / oss）· 用于日志 & metric tag. */
  String name();

  /** 预签名结果. */
  record PresignResult(String uploadUrl, String objectKey, long expiresInSeconds) {}
}
