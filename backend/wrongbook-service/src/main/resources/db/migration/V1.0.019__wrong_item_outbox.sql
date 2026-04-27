-- S3 · wrong_item_outbox · ADR 0002 Outbox over Seata
-- Local message table for RocketMQ tx-message fallback (§4.3).
-- BIGSERIAL PK is intentional for the outbox (single-consumer sequential scan, non-business id).
CREATE TABLE IF NOT EXISTS wrong_item_outbox (
  id            BIGSERIAL    PRIMARY KEY,
  aggregate_id  BIGINT       NOT NULL,
  event_type    VARCHAR(32)  NOT NULL CHECK (event_type IN ('created','updated','deleted')),
  payload       JSONB        NOT NULL,
  status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','PUBLISHED','FAILED')),
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  published_at  TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_wrong_item_outbox_status ON wrong_item_outbox(status, created_at);
