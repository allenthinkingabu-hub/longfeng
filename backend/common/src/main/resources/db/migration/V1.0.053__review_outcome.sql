-- refs 落地计划 §9.2 · ADR 0014 · S5 补 S1 DDL 漂移
-- review_outcome · 复习结果审计表（每次 POST complete 一行）
CREATE TABLE IF NOT EXISTS review_outcome (
  id                       BIGINT PRIMARY KEY,
  plan_id                  BIGINT NOT NULL,
  wrong_item_id            BIGINT NOT NULL,
  user_id                  BIGINT NOT NULL,
  quality                  SMALLINT NOT NULL CHECK (quality >= 0 AND quality <= 5),
  ease_factor_before       NUMERIC(4, 2) NOT NULL CHECK (ease_factor_before >= 1.3 AND ease_factor_before <= 2.5),
  ease_factor_after        NUMERIC(4, 2) NOT NULL CHECK (ease_factor_after  >= 1.3 AND ease_factor_after  <= 2.5),
  interval_days_before     INTEGER NOT NULL CHECK (interval_days_before >= 0 AND interval_days_before <= 60),
  interval_days_after      INTEGER NOT NULL CHECK (interval_days_after  >= 0 AND interval_days_after  <= 60),
  completed_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT fk_review_outcome_plan FOREIGN KEY (plan_id) REFERENCES review_plan (id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_review_outcome_plan_completed     ON review_outcome (plan_id, completed_at DESC);
CREATE INDEX IF NOT EXISTS idx_review_outcome_user_completed     ON review_outcome (user_id, completed_at DESC);
CREATE INDEX IF NOT EXISTS idx_review_outcome_wrong_item         ON review_outcome (wrong_item_id, completed_at DESC);

COMMENT ON TABLE review_outcome IS 'S5 ADR 0014 · 复习结果审计 · 保留 180d (§9.1 A10) · 归档 job 在 S10';
