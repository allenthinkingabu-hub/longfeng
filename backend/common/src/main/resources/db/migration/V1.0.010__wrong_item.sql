-- refs 业务与技术解决方案 §4.2 wb_question (语义) · 落地计划 §5.6 组 B (命名权威)
-- ivfflat WITH (lists=100) · embedding vector(1024) · A7 subject CHECK · A5 deleted_at
CREATE TABLE wrong_item (
  id                  BIGINT       PRIMARY KEY,
  student_id          BIGINT       NOT NULL,
  subject             VARCHAR(16)  NOT NULL,
  grade_code          VARCHAR(16),
  source_type         SMALLINT     NOT NULL,
  origin_image_key    VARCHAR(512),
  processed_image_key VARCHAR(512),
  ocr_text            TEXT,
  stem_text           TEXT,
  status              SMALLINT     NOT NULL DEFAULT 0,
  mastery             SMALLINT     NOT NULL DEFAULT 0,
  embedding           vector(1024),
  mastered_at         TIMESTAMPTZ,
  created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
  deleted_at          TIMESTAMPTZ,
  CONSTRAINT fk_wrong_item_student FOREIGN KEY (student_id) REFERENCES user_account(id) ON DELETE RESTRICT,
  CONSTRAINT ck_wrong_subject     CHECK (subject IN ('math','physics','chinese','english','biology','chemistry','history','geography','politics')),
  CONSTRAINT ck_wrong_source      CHECK (source_type IN (1,2,3,4,5)),
  CONSTRAINT ck_wrong_status      CHECK (status IN (0,1,2,3,8,9)),
  CONSTRAINT ck_wrong_mastery     CHECK (mastery BETWEEN 0 AND 2)
);

CREATE INDEX idx_wrong_student_status ON wrong_item(student_id, status, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_wrong_subject        ON wrong_item(student_id, subject)                 WHERE deleted_at IS NULL;
-- ivfflat index · required by V-S1-03
CREATE INDEX idx_wrong_item_embedding ON wrong_item USING ivfflat (embedding vector_cosine_ops) WITH (lists=100);
