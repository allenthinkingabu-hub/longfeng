-- refs TDD §4.6 wb_review_record · plan §5.S1
-- Schema: review (service-private · review-plan-service)
-- C9: all timestamps TIMESTAMPTZ
-- 复习执行流水 · 每次 POST /complete 一行

CREATE SCHEMA IF NOT EXISTS review;

CREATE TABLE review.wb_review_record (
  id              BIGINT       PRIMARY KEY,                  -- Snowflake
  node_id         BIGINT       NOT NULL,                     -- 软外键 → wb_review_node.id (跨 schema · Feign only)
  plan_id         BIGINT       NOT NULL,                     -- 软外键 → wb_review_plan.id
  student_id      BIGINT       NOT NULL,
  start_at        TIMESTAMPTZ  NOT NULL,                     -- C9
  end_at          TIMESTAMPTZ,
  duration_ms     INT,
  self_rating     SMALLINT,                                  -- 1 掌握 2 部分 3 未掌握 (D-Q-Self-Rate)
  ai_rating       SMALLINT,                                  -- 可选 AI 评估
  notes           TEXT,
  tenant_id       BIGINT       NOT NULL,                     -- 多租户隔离 (plan §5.S1 出口门禁)
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()        -- C9
);

-- 按节点查复习记录（统计/重跑时用）
CREATE INDEX idx_rec_node
  ON review.wb_review_record (node_id);

-- 按 student + 时间范围查（日历复习历史）
CREATE INDEX idx_rec_student_time
  ON review.wb_review_record (student_id, created_at DESC);

COMMENT ON TABLE  review.wb_review_record IS '复习执行流水 · 每次复习行为一行 · 保留 180d';
COMMENT ON COLUMN review.wb_review_record.self_rating IS '1=掌握 2=部分 3=未掌握 · D-Q-Self-Rate 自评协议';
