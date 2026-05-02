-- refs TDD §4.2 wb_question · plan §5.S1
-- Schema: wrongbook (service-private · no cross-schema JOIN allowed)
-- C9: all timestamps TIMESTAMPTZ · D-DB: write-amplification index strategy
-- pgvector: embedding vector(1024) + ivfflat lists=100 (O-10)
-- pg_trgm: gin_trgm_ops on ocr_text (full-text fuzzy search)
-- C2: version BIGINT for optimistic lock (@Version)

CREATE SCHEMA IF NOT EXISTS wrongbook;

CREATE TABLE wrongbook.wb_question (
  id              BIGINT       PRIMARY KEY,                    -- Snowflake
  tenant_id       BIGINT       NOT NULL DEFAULT 0,            -- C2 CAS 前提 · 多租户隔离
  student_id      BIGINT       NOT NULL,
  subject_code    VARCHAR(16)  NOT NULL,                      -- MATH/CHINESE/ENGLISH/PHYSICS/CHEMISTRY/...
  grade_code      VARCHAR(16),                                -- G1..G12, COLLEGE, LANG_CEFR_B1
  source_type     SMALLINT     NOT NULL,                      -- 1 拍照 2 相册 3 H5文件 4 语音 5 手输 6 GUEST_CLAIMED
  origin_image    VARCHAR(512),                               -- OSS object key (D-OSS-Key)
  processed_image VARCHAR(512),                              -- 预处理 (去噪/矫正)
  thumbnail       VARCHAR(512),                              -- 240px 脱敏缩略 (观察者/分享用)
  ocr_text        TEXT,
  status          SMALLINT     NOT NULL DEFAULT 0,            -- 0 PENDING 1 ANALYZING 2 READY 3 ACTIVE 8 ARCHIVED 9 FAILED
  mastery         SMALLINT     NOT NULL DEFAULT 0,            -- 0 未掌握 1 部分 2 已掌握 (从 review_plan 同步)
  knowledge_tags  JSONB        NOT NULL DEFAULT '[]',         -- [{code,name,weight}]
  embedding       vector(1024),                               -- pgvector · OpenAI text-embedding-3-small / BGE-M3
  confidence      NUMERIC(4,3),                              -- AI 置信度 0..1
  version         BIGINT       NOT NULL DEFAULT 0,            -- @Version 乐观锁 (C2)
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),        -- C9: 全 UTC
  updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  archived_at     TIMESTAMPTZ,
  deleted_at      TIMESTAMPTZ                                 -- 软删 (SC-04)
);

-- 错题本列表按 status 过滤 · P05 列表分页 · 写放大 0.5×
CREATE INDEX idx_wb_q_student_status
  ON wrongbook.wb_question (student_id, status, created_at DESC)
  WHERE deleted_at IS NULL;

-- 学科切片 · 学科 chips 计数 · 写放大 0.3×
CREATE INDEX idx_wb_q_subject
  ON wrongbook.wb_question (student_id, subject_code)
  WHERE deleted_at IS NULL;

-- 知识点 JSONB 查询 · WHERE knowledge_tags @> '[{"code":"MATH_01"}]' · 写放大 1.2×
CREATE INDEX idx_wb_q_tags_gin
  ON wrongbook.wb_question USING GIN (knowledge_tags jsonb_path_ops);

-- 模糊文本检索 · WHERE ocr_text % '二次函数' · 写放大 1.5× · 需 pg_trgm extension
CREATE INDEX idx_wb_q_trgm
  ON wrongbook.wb_question USING GIN (ocr_text gin_trgm_ops);

-- 语义向量检索 · ORDER BY embedding <=> :query_vec LIMIT 10 · 写放大 0.7× · 需 pgvector
-- O-10: lists=100 为 TDD §4.2 + plan §5.S1 出口门禁要求
CREATE INDEX idx_wb_q_embedding_ivfflat
  ON wrongbook.wb_question USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- 归档扫描用 · FileTtlSweepJob / archive job
CREATE INDEX idx_wb_q_archived
  ON wrongbook.wb_question (archived_at)
  WHERE archived_at IS NOT NULL;

COMMENT ON TABLE  wrongbook.wb_question IS 'AI 错题本主表 · 一条 question = 一次拍题';
COMMENT ON COLUMN wrongbook.wb_question.status    IS 'PENDING(0)→ANALYZING(1)→READY(2)→ACTIVE(3)→ARCHIVED(8)|FAILED(9)';
COMMENT ON COLUMN wrongbook.wb_question.embedding IS 'OpenAI text-embedding-3-small (1536维降至1024) 或本地 BGE-M3';
COMMENT ON COLUMN wrongbook.wb_question.version   IS 'C2 乐观锁 · JPA @Version · CAS before update';
COMMENT ON COLUMN wrongbook.wb_question.tenant_id IS '多租户隔离 · MVP 默认 0 · SaaS 扩展用';
