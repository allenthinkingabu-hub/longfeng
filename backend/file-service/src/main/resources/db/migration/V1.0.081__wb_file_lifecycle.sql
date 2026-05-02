-- refs TDD §4.14 wb_file_lifecycle · plan §5.S1
-- Schema: file · 文件生命周期管理（TTL 分层）
-- C9: all timestamps TIMESTAMPTZ
-- D-OSS-TTL: 30d→IA / 180d→ARCHIVE · FileTtlSweepJob 驱动
-- file_id 1:1 → wb_file.id (PRIMARY KEY 即外键)

CREATE TABLE file.wb_file_lifecycle (
  file_id    BIGINT       PRIMARY KEY REFERENCES file.wb_file(id) ON DELETE CASCADE,
  promote_at TIMESTAMPTZ,               -- C9 · 30d 后转 IA · FileTtlSweepJob 触发
  archive_at TIMESTAMPTZ,               -- C9 · 180d 后转 ARCHIVE
  delete_at  TIMESTAMPTZ                -- C9 · 学生主动续期可推迟 · NULL = 不自动删除
);

-- 转 IA 扫描 · FileTtlSweepJob promote 路径
CREATE INDEX idx_file_lifecycle_promote
  ON file.wb_file_lifecycle (promote_at)
  WHERE promote_at IS NOT NULL;

-- 转 ARCHIVE 扫描
CREATE INDEX idx_file_lifecycle_archive
  ON file.wb_file_lifecycle (archive_at)
  WHERE archive_at IS NOT NULL;

-- 硬删除扫描（学生注销 / 超期）
CREATE INDEX idx_file_lifecycle_delete
  ON file.wb_file_lifecycle (delete_at)
  WHERE delete_at IS NOT NULL;

COMMENT ON TABLE  file.wb_file_lifecycle IS 'D-OSS-TTL 文件生命周期 · 1:1 → wb_file · FileTtlSweepJob 驱动';
COMMENT ON COLUMN file.wb_file_lifecycle.promote_at IS '30d→IA · 默认 file.created_at + 30d · 续期可延后';
COMMENT ON COLUMN file.wb_file_lifecycle.archive_at IS '180d→ARCHIVE · 默认 file.created_at + 180d · 续期可延后';
COMMENT ON COLUMN file.wb_file_lifecycle.delete_at IS 'NULL=永久保留 · 学生注销时填入 now() + 7d 宽限';
