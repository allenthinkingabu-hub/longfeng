-- refs 落地计划 §5.6 组 D · 通用配额桶 (区别于 guest_quota · 不限匿名域)
-- scope=USER/IP/DEVICE · bucket_name=AI_CALL / UPLOAD / EXPORT 等
CREATE TABLE quota_bucket (
  id                 BIGINT       PRIMARY KEY,
  scope              VARCHAR(32)  NOT NULL,
  scope_key          VARCHAR(128) NOT NULL,
  bucket_name        VARCHAR(64)  NOT NULL,
  bucket_day         DATE         NOT NULL,
  tokens_remaining   INT          NOT NULL,
  tokens_capacity    INT          NOT NULL,
  last_refill_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT ck_qb_scope     CHECK (scope IN ('USER','IP','DEVICE')),
  CONSTRAINT ck_qb_remaining CHECK (tokens_remaining BETWEEN 0 AND tokens_capacity),
  CONSTRAINT ck_qb_capacity  CHECK (tokens_capacity > 0)
);

CREATE UNIQUE INDEX uq_quota_bucket_key ON quota_bucket(scope, scope_key, bucket_name, bucket_day);
