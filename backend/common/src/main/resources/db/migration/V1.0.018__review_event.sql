-- refs 业务与技术解决方案 §4.5/§4.6 wb_review_node + wb_review_record (融合为事件流) · 落地计划 §5.6 组 B
-- A6: 事件表无 FK · append-leaning · 每次节点动作 (SCHEDULED/PUSHED/REVIEWED/FORGOTTEN/CANCELLED) 一行
CREATE TABLE review_event (
  id               BIGINT       PRIMARY KEY,
  plan_id          BIGINT       NOT NULL,
  wrong_item_id    BIGINT       NOT NULL,
  student_id       BIGINT       NOT NULL,
  level            SMALLINT     NOT NULL,
  action           VARCHAR(16)  NOT NULL,
  effect           SMALLINT,
  duration_ms      INT,
  payload          JSONB,
  occurred_at      TIMESTAMPTZ  NOT NULL,
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT ck_re_level  CHECK (level BETWEEN 0 AND 6),
  CONSTRAINT ck_re_action CHECK (action IN ('SCHEDULED','PUSHED','REVIEWED','FORGOTTEN','CANCELLED')),
  CONSTRAINT ck_re_effect CHECK (effect IS NULL OR effect BETWEEN 1 AND 3)
);

CREATE INDEX idx_re_plan_occ     ON review_event(plan_id, occurred_at DESC);
CREATE INDEX idx_re_student_occ  ON review_event(student_id, occurred_at DESC);
CREATE INDEX idx_re_payload_gin  ON review_event USING GIN (payload);
