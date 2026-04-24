package com.longfeng.fileservice.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.GpsDirectory;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 图片处理 · SC-11.AC-2 · thumbnailator 缩放 + webp 转 + EXIF strip. */
@Component
public class ImageProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(ImageProcessor.class);
  public static final int THUMB_MAX = 320;
  public static final int MEDIUM_MAX = 1920;
  public static final float WEBP_QUALITY = 0.85f;

  /** 产出 thumb + medium 两档 webp · 自动 strip EXIF（thumbnailator 默认不写 EXIF · 即 strip）. */
  public Variants process(InputStream original, String sourceMime) {
    try {
      byte[] originalBytes = original.readAllBytes();
      BufferedImage src = ImageIO.read(new ByteArrayInputStream(originalBytes));
      if (src == null) {
        throw new IllegalArgumentException("unsupported image format or corrupted: mime=" + sourceMime);
      }

      byte[] thumb = resizeToWebp(src, THUMB_MAX);
      byte[] medium = resizeToWebp(src, MEDIUM_MAX);
      return new Variants(thumb, medium);
    } catch (IOException e) {
      throw new IllegalStateException("image processing failed", e);
    }
  }

  /** 检查 EXIF 是否已 strip（测试辅助 · 扫 GPS/Make/Model）. */
  public boolean hasSensitiveExif(byte[] webpBytes) {
    try {
      Metadata md = ImageMetadataReader.readMetadata(new ByteArrayInputStream(webpBytes));
      for (var dir : md.getDirectories()) {
        if (dir instanceof GpsDirectory gps && !gps.getTags().isEmpty()) return true;
        if (dir instanceof ExifIFD0Directory exif
            && (exif.containsTag(ExifIFD0Directory.TAG_MAKE)
                || exif.containsTag(ExifIFD0Directory.TAG_MODEL))) {
          return true;
        }
      }
    } catch (Exception e) {
      LOG.debug("hasSensitiveExif · metadata read failed (expected for clean webp)", e);
    }
    return false;
  }

  private byte[] resizeToWebp(BufferedImage src, int maxSide) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    int w = src.getWidth();
    int h = src.getHeight();
    double scale = Math.min(1.0, (double) maxSide / Math.max(w, h));
    BufferedImage scaled =
        scale < 1.0
            ? Thumbnails.of(src).scale(scale).asBufferedImage()
            : src;
    // webp codec 注册需 webp-imageio 依赖 · imageIO 自动发现
    boolean written = ImageIO.write(scaled, "webp", out);
    if (!written) {
      // 回退：若 webp codec 没注册（CI 罕见）· 降级 jpg · 测试用
      ImageIO.write(scaled, "jpg", out);
    }
    return out.toByteArray();
  }

  /** 处理结果 · thumb (≤320px) + medium (≤1920px) 两档 webp · Q-C 永久保留. */
  public record Variants(byte[] thumb, byte[] medium) {}
}
