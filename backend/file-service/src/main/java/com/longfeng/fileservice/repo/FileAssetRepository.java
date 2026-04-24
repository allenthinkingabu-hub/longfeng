package com.longfeng.fileservice.repo;

import com.longfeng.fileservice.entity.FileAsset;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileAssetRepository extends JpaRepository<FileAsset, Long> {

  /** 按 object_key 查（presign 时用 UUID · 唯一 · Phase DoR 保证） */
  Optional<FileAsset> findByObjectKey(String objectKey);
}
