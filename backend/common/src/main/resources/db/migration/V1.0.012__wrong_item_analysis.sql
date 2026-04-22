-- refs 业务与技术解决方案 §4.3 wb_analysis_result · 落地计划 §5.6 组 B
-- version per wrong_item · multiple analyses per item (rerun / switch model)
CREATE TABLE wrong_item_analysis (
  id               BIGINT       PRIMARY KEY,
  wrong_item_id    BIGINT       NOT NULL,
  version          INT          NOT NULL,
  model_provider   VARCHAR(32)  NOT NULL,
  model_name       VARCHAR(64)  NOT NULL,
  input_tokens     INT,
  output_tokens    INT,
  cost_cents       INT,
  stem_text        TEXT,
  student_answer   TEXT,
  correct_answer   TEXT,
  error_type       VARCHAR(32),
  error_reason     TEXT,
  solution_steps   JSONB,
  knowledge_points JSONB,
  difficulty       SMALLINT,
  raw_json         JSONB        NOT NULL,
  status           SMALLINT     NOT NULL DEFAULT 0,
  finished_at      TIMESTAMPTZ,
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT fk_analysis_item    FOREIGN KEY (wrong_item_id) REFERENCES wrong_item(id) ON DELETE RESTRICT,
  CONSTRAINT uq_analysis_version UNIQUE (wrong_item_id, version),
  CONSTRAINT ck_analysis_version CHECK (version >= 1),
  CONSTRAINT ck_analysis_status  CHECK (status IN (0,1,9)),
  CONSTRAINT ck_analysis_diff    CHECK (difficulty IS NULL OR difficulty BETWEEN 1 AND 5)
);

CREATE INDEX idx_analysis_item_ver       ON wrong_item_analysis(wrong_item_id, version DESC);
CREATE INDEX idx_analysis_solution_gin   ON wrong_item_analysis USING GIN (solution_steps);
CREATE INDEX idx_analysis_knowledge_gin  ON wrong_item_analysis USING GIN (knowledge_points);
CREATE INDEX idx_analysis_raw_gin        ON wrong_item_analysis USING GIN (raw_json);
