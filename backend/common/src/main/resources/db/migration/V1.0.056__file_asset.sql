-- refs 落地计划 §10.1 A6 · S6 补 S1 DDL 漂移（S1 §5.6 未建 file_asset · S6 按 ADR 0014 模式新增）
-- file_asset · 图片/文件元数据表
CREATE TABLE IF NOT EXISTS file_asset (
  id                  BIGINT PRIMARY KEY,
  owner_id            BIGINT NOT NULL,
  bucket              VARCHAR(64) NOT NULL,
  object_key          VARCHAR(512) NOT NULL,
  mime                VARCHAR(64) NOT NULL CHECK (mime IN ('image/jpeg', 'image/png', 'image/heic', 'image/webp')),
  size_bytes          BIGINT NOT NULL CHECK (size_bytes >= 0 AND size_bytes <= 10485760),
  checksum_sha256     CHAR(64),
  status              VARCHAR(16) NOT NULL DEFAULT 'PENDING'
                      CHECK (status IN ('PENDING', 'READY', 'QUARANTINED', 'ARCHIVED')),
  uploaded_at         TIMESTAMPTZ,
  variant_thumb_key   VARCHAR(512),
  variant_medium_key  VARCHAR(512),
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at          TIMESTAMPTZ
);

-- 索引：按 owner 查询 + 按 status 归档 + 按 uploaded_at 归档 job
CREATE INDEX IF NOT EXISTS idx_file_asset_owner_created ON file_asset (owner_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_file_asset_status       ON file_asset (status)                    WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_file_asset_uploaded_at  ON file_asset (uploaded_at)               WHERE deleted_at IS NULL;
-- (bucket, object_key) 唯一（避免同 key 双写）
CREATE UNIQUE INDEX IF NOT EXISTS uk_file_asset_bucket_key ON file_asset (bucket, object_key) WHERE deleted_at IS NULL;

COMMENT ON TABLE  file_asset                  IS 'S6 ADR 0014 类推 · §10.1 A6 · 图片/文件元数据 · Q-C 90d 归档策略';
COMMENT ON COLUMN file_asset.status           IS 'PENDING=presign 后未 complete · READY=complete 处理完 · QUARANTINED=病毒扫描阳性 · ARCHIVED=90d 归档';
COMMENT ON COLUMN file_asset.variant_thumb_key  IS 'thumb ≤ 320px (Q-C 永久保留)';
COMMENT ON COLUMN file_asset.variant_medium_key IS 'medium ≤ 1920px (Q-C 永久保留)';
