-- S3 · Q4-R1 decision · ADR 0014 WrongAttempt as independent aggregate root
-- append-only; no UPDATE or DELETE endpoints (INV-06).
CREATE TABLE IF NOT EXISTS wrong_attempt (
  id            BIGINT       NOT NULL,
  wrong_item_id BIGINT       NOT NULL REFERENCES wrong_item(id) ON DELETE RESTRICT,
  student_id    BIGINT       NOT NULL,
  answer_text   TEXT,
  is_correct    BOOLEAN      NOT NULL,
  duration_sec  SMALLINT,
  client_source VARCHAR(16),
  submitted_at  TIMESTAMPTZ  NOT NULL,
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT pk_wrong_attempt PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_wrong_attempt_item ON wrong_attempt(wrong_item_id, submitted_at DESC);
