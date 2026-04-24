package com.longfeng.fileservice.service;

import com.longfeng.fileservice.config.StorageProperties;
import com.longfeng.fileservice.entity.FileAsset;
import com.longfeng.fileservice.exception.FileNotFoundException;
import com.longfeng.fileservice.exception.VirusDetectedException;
import com.longfeng.fileservice.provider.StorageProvider;
import com.longfeng.fileservice.repo.FileAssetRepository;
import com.longfeng.fileservice.scan.AntivirusClient;
import com.longfeng.fileservice.scan.AntivirusClient.ScanResult;
import com.longfeng.fileservice.scan.AntivirusClient.Verdict;
import com.longfeng.fileservice.service.ImageProcessor.Variants;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** SC-11.AC-2 · POST /files/complete/{fileKey} · 后处理 webp + EXIF strip + scan + UPDATE READY. */
@Service
public class UploadService {

  private static final Logger LOG = LoggerFactory.getLogger(UploadService.class);

  private final StorageProvider storage;
  private final StorageProperties props;
  private final FileAssetRepository repo;
  private final ImageProcessor imageProcessor;
  private final AntivirusClient av;

  public UploadService(
      StorageProvider storage,
      StorageProperties props,
      FileAssetRepository repo,
      ImageProcessor imageProcessor,
      AntivirusClient av) {
    this.storage = storage;
    this.props = props;
    this.repo = repo;
    this.imageProcessor = imageProcessor;
    this.av = av;
  }

  /**
   * SC-11.AC-2 · complete 链路 · 同步返回（≤ 3s 目标）.
   *
   * <ol>
   *   <li>查 file_asset 必须 PENDING · 否则 404
   *   <li>读 OSS/MinIO 原图
   *   <li>AntivirusClient scan · INFECTED → QUARANTINED · 422
   *   <li>thumbnailator 缩放 + webp + EXIF strip
   *   <li>回写 thumb + medium variant
   *   <li>UPDATE file_asset.status=READY + variant_*_key + uploaded_at
   * </ol>
   */
  @Transactional
  public FileAsset complete(String fileKey) {
    FileAsset asset = repo.findByObjectKey(fileKey).orElseThrow(() -> new FileNotFoundException(fileKey));
    if (FileAsset.STATUS_READY.equals(asset.getStatus())) {
      // idempotent（boundary.0 · 已处理再调 complete · 直接返回）
      return asset;
    }

    // 读原图
    byte[] originalBytes;
    try (var is = storage.readObject(asset.getBucket(), asset.getObjectKey())) {
      originalBytes = is.readAllBytes();
    } catch (Exception e) {
      throw new IllegalStateException("read object failed: " + fileKey, e);
    }

    // 病毒扫描
    ScanResult scan = av.scan(new ByteArrayInputStream(originalBytes));
    if (scan.verdict() != Verdict.CLEAN) {
      asset.setStatus(FileAsset.STATUS_QUARANTINED);
      repo.save(asset);
      throw new VirusDetectedException(fileKey, scan.reason());
    }

    // thumbnailator + webp + EXIF strip
    Variants variants =
        imageProcessor.process(new ByteArrayInputStream(originalBytes), asset.getMime());

    // 回写 thumb + medium
    String thumbKey = derivedKey(asset.getObjectKey(), "thumb");
    String mediumKey = derivedKey(asset.getObjectKey(), "medium");
    storage.putObject(
        asset.getBucket(),
        thumbKey,
        new ByteArrayInputStream(variants.thumb()),
        variants.thumb().length,
        "image/webp");
    storage.putObject(
        asset.getBucket(),
        mediumKey,
        new ByteArrayInputStream(variants.medium()),
        variants.medium().length,
        "image/webp");

    asset.setVariantThumbKey(thumbKey);
    asset.setVariantMediumKey(mediumKey);
    asset.setStatus(FileAsset.STATUS_READY);
    asset.setUploadedAt(Instant.now());
    FileAsset saved = repo.save(asset);
    LOG.info(
        "complete · fileKey={} status=READY thumbSize={} mediumSize={}",
        fileKey,
        variants.thumb().length,
        variants.medium().length);
    return saved;
  }

  /** 从 raw/{uuid}.jpg → variants/thumb/{uuid}.webp · raw → variants/medium/{uuid}.webp. */
  private String derivedKey(String rawKey, String variant) {
    String base =
        rawKey.startsWith("raw/")
            ? rawKey.substring(4)
            : rawKey;
    int dot = base.lastIndexOf('.');
    String stem = dot < 0 ? base : base.substring(0, dot);
    return "variants/" + variant + "/" + stem + ".webp";
  }
}
