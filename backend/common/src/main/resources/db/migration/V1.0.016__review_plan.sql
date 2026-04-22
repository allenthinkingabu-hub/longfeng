-- refs 业务与技术解决方案 §4.4 wb_review_plan + §4.8 ebbinghaus_node_config · 艾宾浩斯.md
-- 落地计划 §5.6 组 B · 艾宾浩斯 · ease_factor + interval_index (SM-2 融合)
-- A6: FK RESTRICT · 业务主表
CREATE TABLE review_plan (
  id               BIGINT        PRIMARY KEY,
  wrong_item_id    BIGINT        NOT NULL UNIQUE,
  student_id       BIGINT        NOT NULL,
  strategy_code    VARCHAR(32)   NOT NULL DEFAULT 'EBBINGHAUS_STD',
  start_at         TIMESTAMPTZ   NOT NULL,
  current_level    SMALLINT      NOT NULL DEFAULT 0,
  interval_index   SMALLINT      NOT NULL DEFAULT 0,
  ease_factor      NUMERIC(5,3)  NOT NULL DEFAULT 2.500,
  total_review     INT           NOT NULL DEFAULT 0,
  total_forget     INT           NOT NULL DEFAULT 0,
  mastery_score    NUMERIC(5,2)  NOT NULL DEFAULT 0.00,
  next_due_at      TIMESTAMPTZ,
  status           SMALLINT      NOT NULL DEFAULT 0,
  completed_at     TIMESTAMPTZ,
  created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
  deleted_at       TIMESTAMPTZ,
  CONSTRAINT fk_rp_item     FOREIGN KEY (wrong_item_id) REFERENCES wrong_item(id)    ON DELETE RESTRICT,
  CONSTRAINT fk_rp_student  FOREIGN KEY (student_id)    REFERENCES user_account(id)  ON DELETE RESTRICT,
  CONSTRAINT ck_rp_level    CHECK (current_level  BETWEEN 0 AND 6),
  CONSTRAINT ck_rp_interval CHECK (interval_index BETWEEN 0 AND 6),
  CONSTRAINT ck_rp_ease     CHECK (ease_factor    BETWEEN 1.300 AND 5.000),
  CONSTRAINT ck_rp_mastery  CHECK (mastery_score  BETWEEN 0.00 AND 100.00),
  CONSTRAINT ck_rp_status   CHECK (status IN (0,1,9))
);

CREATE INDEX idx_rp_next_due  ON review_plan(status, next_due_at)  WHERE deleted_at IS NULL;
CREATE INDEX idx_rp_student   ON review_plan(student_id, status)    WHERE deleted_at IS NULL;
