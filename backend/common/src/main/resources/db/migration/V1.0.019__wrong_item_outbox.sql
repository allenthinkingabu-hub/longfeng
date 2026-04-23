-- refs 落地计划 §7 · S3 wrongbook-service 事务消息降级 · design/arch/s3-wrongbook.md §4.3
-- wrong_item_outbox · 本地消息表 · RocketMQ 事务消息失败时落库 · 补发 scheduler 留到 S10
CREATE TABLE wrong_item_outbox (
  id            BIGSERIAL   PRIMARY KEY,
  aggregate_id  BIGINT      NOT NULL,
  event_type    VARCHAR(32) NOT NULL CHECK (event_type IN ('created','updated','deleted')),
  payload       JSONB       NOT NULL,
  status        VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','PUBLISHED','FAILED')),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_at  TIMESTAMPTZ
);

CREATE INDEX idx_wrong_item_outbox_status ON wrong_item_outbox(status, created_at);
