-- refs 业务与技术解决方案 §4.10 guest_rate_bucket · 落地计划 §5.6 组 C · Token Bucket 容量 3/日
CREATE TABLE guest_quota (
  id                 BIGINT       PRIMARY KEY,
  device_fp_hash     CHAR(64)     NOT NULL,
  bucket_day         DATE         NOT NULL,
  tokens_remaining   SMALLINT     NOT NULL DEFAULT 3,
  tokens_capacity    SMALLINT     NOT NULL DEFAULT 3,
  last_refill_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT ck_guest_quota_remaining CHECK (tokens_remaining BETWEEN 0 AND tokens_capacity),
  CONSTRAINT ck_guest_quota_capacity  CHECK (tokens_capacity > 0),
  CONSTRAINT ck_guest_quota_fp_len    CHECK (char_length(device_fp_hash) = 64)
);

CREATE UNIQUE INDEX uq_guest_quota_fp_day ON guest_quota(device_fp_hash, bucket_day);
