package com.longfeng.fileservice.service;

import com.longfeng.fileservice.config.StorageProperties;
import com.longfeng.fileservice.entity.FileAsset;
import com.longfeng.fileservice.exception.FileNotFoundException;
import com.longfeng.fileservice.exception.MimeNotAllowedException;
import com.longfeng.fileservice.exception.OversizeException;
import com.longfeng.fileservice.provider.StorageProvider;
import com.longfeng.fileservice.provider.StorageProvider.PresignResult;
import com.longfeng.fileservice.repo.FileAssetRepository;
import com.longfeng.fileservice.support.SnowflakeIdGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 预签名 URL 生成 + MIME 白名单 + 大小校验（SC-11.AC-1 / SC-11.AC-3 · §10.4 禁止清单）. */
@Service
public class SignatureService {

  /** MIME 白名单（A4 决策 · §10.4 禁止之外） */
  public static final Set<String> ALLOWED_MIME =
      Set.of("image/jpeg", "image/png", "image/heic", "image/webp");

  private final StorageProvider storage;
  private final StorageProperties props;
  private final FileAssetRepository repo;
  private final SnowflakeIdGenerator idGen;

  public SignatureService(
      StorageProvider storage,
      StorageProperties props,
      FileAssetRepository repo,
      SnowflakeIdGenerator idGen) {
    this.storage = storage;
    this.props = props;
    this.repo = repo;
    this.idGen = idGen;
  }

  /**
   * SC-11.AC-1 · POST /files/presign · 返回 uploadUrl + fileKey.
   *
   * @throws MimeNotAllowedException if mime ∉ 白名单
   * @throws OversizeException if size > 10MB
   */
  @Transactional
  public PresignResponse presignUpload(Long ownerId, String filename, String mime, long size) {
    if (!ALLOWED_MIME.contains(mime)) {
      throw new MimeNotAllowedException(mime);
    }
    if (size < 0 || size > props.maxUploadSize()) {
      throw new OversizeException(size, props.maxUploadSize());
    }

    String ext = extractExt(filename);
    String objectKey = "raw/" + UUID.randomUUID() + ext;
    PresignResult pr =
        storage.presignUpload(
            props.bucket(), objectKey, mime, Duration.ofSeconds(props.presignTtlSeconds()));

    // 记录 PENDING 占位 · complete 时 UPDATE READY
    FileAsset asset = new FileAsset();
    asset.setId(idGen.nextId());
    asset.setOwnerId(ownerId);
    asset.setBucket(props.bucket());
    asset.setObjectKey(objectKey);
    asset.setMime(mime);
    asset.setSizeBytes(size);
    asset.setStatus(FileAsset.STATUS_PENDING);
    repo.save(asset);

    return new PresignResponse(
        pr.uploadUrl(),
        objectKey,
        Instant.now().plusSeconds(pr.expiresInSeconds()).toString(),
        pr.expiresInSeconds());
  }

  /** SC-11.AC-3 · GET /files/download/{fileKey}?variant=. */
  public String presignDownload(String fileKey, String variant) {
    FileAsset asset = repo.findByObjectKey(fileKey).orElseThrow(() -> new FileNotFoundException(fileKey));
    String actualKey =
        switch (variant == null ? "medium" : variant) {
          case "thumb" -> asset.getVariantThumbKey() != null ? asset.getVariantThumbKey() : fileKey;
          case "original" -> fileKey;
          case "medium" -> asset.getVariantMediumKey() != null ? asset.getVariantMediumKey() : fileKey;
          default -> throw new IllegalArgumentException("invalid variant: " + variant);
        };
    return storage.presignDownload(
        asset.getBucket(), actualKey, Duration.ofSeconds(props.presignTtlSeconds()));
  }

  private String extractExt(String filename) {
    if (filename == null) return "";
    int dot = filename.lastIndexOf('.');
    return dot < 0 ? "" : filename.substring(dot).toLowerCase();
  }

  /** presign response DTO. */
  public record PresignResponse(String uploadUrl, String fileKey, String expiresAt, long ttlSeconds) {}
}
