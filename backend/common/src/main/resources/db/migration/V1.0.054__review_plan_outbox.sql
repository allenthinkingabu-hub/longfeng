-- refs 落地计划 §9.2 · ADR 0005 RocketMQ 事务消息兜底 · ADR 0014
-- review_plan_outbox · S5 Outbox 事件暂存 · Relay 扫表发 MQ
CREATE TABLE IF NOT EXISTS review_plan_outbox (
  id             BIGINT PRIMARY KEY,
  plan_id        BIGINT NOT NULL,
  event_type     VARCHAR(32) NOT NULL CHECK (event_type IN ('due', 'completed', 'mastered')),
  payload        JSONB NOT NULL,
  status         VARCHAR(16) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'dispatched', 'failed')),
  retry_count    SMALLINT NOT NULL DEFAULT 0,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  dispatched_at  TIMESTAMPTZ
);

-- 未发 + 按 created_at 序扫（Relay 用）
CREATE INDEX IF NOT EXISTS idx_review_plan_outbox_status_created ON review_plan_outbox (status, created_at) WHERE status = 'pending';
CREATE INDEX IF NOT EXISTS idx_review_plan_outbox_plan           ON review_plan_outbox (plan_id);

COMMENT ON TABLE review_plan_outbox IS 'S5 ADR 0014 · Outbox 事件暂存 · Relay (S10) 扫 pending → MQ';
