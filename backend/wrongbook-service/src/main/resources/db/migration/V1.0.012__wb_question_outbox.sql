-- refs TDD §4.15 · plan §5.S1 · ADR-0005 RocketMQ 事务消息兜底 (Outbox Pattern)
-- Schema: wrongbook · Outbox 暂存 → Relay job 扫表发 MQ
-- C9: all timestamps TIMESTAMPTZ
-- event_type: wrongbook-service 发出的域事件类型

CREATE TABLE wrongbook.wb_question_outbox (
  id            BIGINT       PRIMARY KEY,                    -- Snowflake
  aggregate_id  BIGINT       NOT NULL,                       -- wb_question.id
  event_type    VARCHAR(64)  NOT NULL,                       -- question.created / question.analyzed / question.archived / question.deleted
  payload       JSONB        NOT NULL,                       -- 完整事件体（含 question_id / student_id / subject_code / status）
  status        SMALLINT     NOT NULL DEFAULT 0,             -- 0 PENDING 1 SENT 9 DEAD
  retry_count   INT          NOT NULL DEFAULT 0,
  next_retry_at TIMESTAMPTZ,
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),         -- C9
  sent_at       TIMESTAMPTZ,
  dead_reason   TEXT
);

-- Relay job 扫 pending 行 · 按 next_retry_at 排序
CREATE INDEX idx_wb_q_outbox_pending
  ON wrongbook.wb_question_outbox (status, next_retry_at)
  WHERE status = 0;

COMMENT ON TABLE  wrongbook.wb_question_outbox IS 'ADR-0005 Outbox · wrongbook 域事件暂存 · Relay job 30s 重试';
COMMENT ON COLUMN wrongbook.wb_question_outbox.status IS 'PENDING(0)→SENT(1)|DEAD(9)';
COMMENT ON COLUMN wrongbook.wb_question_outbox.payload IS '事件完整快照 · 含 question_id/student_id/status/subject_code';
