-- refs G-Biz Q4-R1 · D-Attempt drift resolution · design/arch/s3-wrongbook.md §2.1 §4.1
-- wrong_attempt · 新聚合根 · 一次作答不可变 (append-only · 无 updated_at · 无 deleted_at)
-- 每道错题可多次作答 · client_source 记录来源端 · is_correct 用户自评或 AI 评判
CREATE TABLE wrong_attempt (
  id             BIGINT       PRIMARY KEY,        -- Snowflake 应用生成
  wrong_item_id  BIGINT       NOT NULL,
  student_id     BIGINT       NOT NULL,
  answer_text    TEXT,
  is_correct     BOOLEAN      NOT NULL,
  duration_sec   SMALLINT,
  client_source  VARCHAR(16),
  submitted_at   TIMESTAMPTZ  NOT NULL,
  created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT fk_wa_item    FOREIGN KEY (wrong_item_id) REFERENCES wrong_item(id) ON DELETE RESTRICT,
  CONSTRAINT fk_wa_student FOREIGN KEY (student_id)    REFERENCES user_account(id) ON DELETE RESTRICT,
  CONSTRAINT ck_wa_client  CHECK (client_source IS NULL OR client_source IN ('app','web','mp','admin')),
  CONSTRAINT ck_wa_duration CHECK (duration_sec IS NULL OR duration_sec >= 0)
);

CREATE INDEX idx_wa_item_submitted    ON wrong_attempt(wrong_item_id, submitted_at DESC);
CREATE INDEX idx_wa_student_submitted ON wrong_attempt(student_id, submitted_at DESC);
