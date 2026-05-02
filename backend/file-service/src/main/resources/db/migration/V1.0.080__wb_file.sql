-- refs TDD §4.14 wb_file · plan §5.S1
-- Schema: file (file-service private)
-- C9: all timestamps TIMESTAMPTZ
-- D-OSS-Key: object_key 格式规范 · ObjectKeyBuilder 生成
-- sha256_hash: 内容寻址 · 防重复上传

CREATE SCHEMA IF NOT EXISTS file;

CREATE TABLE file.wb_file (
  id            BIGINT       PRIMARY KEY,                  -- Snowflake
  tenant_id     BIGINT       NOT NULL DEFAULT 0,           -- C2 多租户隔离
  student_id    BIGINT       NOT NULL,
  object_key    VARCHAR(512) NOT NULL UNIQUE,              -- D-OSS-Key · ObjectKeyBuilder 生成
  original_name VARCHAR(255),
  mime_type     VARCHAR(64),
  bytes         BIGINT,
  sha256_hash   CHAR(64),                                  -- 内容寻址 (SHA-256 hex) · 防重复上传
  status        SMALLINT     NOT NULL DEFAULT 0,           -- 0 PENDING 1 UPLOADED 2 SCANNED_OK 3 QUARANTINED 9 DELETED
  storage_class VARCHAR(16),                               -- STANDARD / IA / ARCHIVE · D-OSS-TTL 冷热分层
  uploaded_at   TIMESTAMPTZ,                               -- C9 · presign callback 后填
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()        -- C9
);

-- 按 student 查文件列表 · 按状态过滤 + 时间倒序
CREATE INDEX idx_file_student
  ON file.wb_file (student_id, status, created_at DESC);

COMMENT ON TABLE  file.wb_file IS '文件元数据 · OSS 对象指针 · presign→callback→scan 流程';
COMMENT ON COLUMN file.wb_file.object_key IS 'D-OSS-Key 格式: {env}/{tenant_id}/{student_id}/{yyyy}/{mm}/{uuid}.{ext}';
COMMENT ON COLUMN file.wb_file.status IS 'PENDING(0)→UPLOADED(1)→SCANNED_OK(2)|QUARANTINED(3)|DELETED(9)';
COMMENT ON COLUMN file.wb_file.storage_class IS 'STANDARD→IA(30d)→ARCHIVE(180d) · FileTtlSweepJob 驱动 · D-OSS-TTL';
COMMENT ON COLUMN file.wb_file.sha256_hash IS 'SHA-256 hex · 64字符 · 内容寻址防重复上传';
