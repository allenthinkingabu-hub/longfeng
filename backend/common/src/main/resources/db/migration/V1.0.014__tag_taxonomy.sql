-- refs 业务与技术解决方案 §2A.5 知识点标签 · 落地计划 §5.6 组 B · 静态引用数据
CREATE TABLE tag_taxonomy (
  id               BIGINT       PRIMARY KEY,
  code             VARCHAR(64)  NOT NULL UNIQUE,
  display_name     VARCHAR(128) NOT NULL,
  parent_code      VARCHAR(64),
  subject          VARCHAR(16),
  bloom_level      SMALLINT,
  status           SMALLINT     NOT NULL DEFAULT 1,
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT ck_tag_subject CHECK (subject IS NULL OR subject IN ('math','physics','chinese','english','biology','chemistry','history','geography','politics')),
  CONSTRAINT ck_tag_bloom   CHECK (bloom_level IS NULL OR bloom_level BETWEEN 1 AND 6),
  CONSTRAINT ck_tag_status  CHECK (status IN (1,9))
);

CREATE INDEX idx_tag_parent   ON tag_taxonomy(parent_code);
CREATE INDEX idx_tag_subject  ON tag_taxonomy(subject);
