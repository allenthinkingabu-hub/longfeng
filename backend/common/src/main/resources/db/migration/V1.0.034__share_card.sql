-- refs 业务与技术解决方案 §4.11 share_token (重命名 share_card + 水印资源) · 落地计划 §5.6 组 C
-- 水印 + TTL 7d · expires_at <= created_at + 7d 硬约束
CREATE TABLE share_card (
  id                 BIGINT       PRIMARY KEY,
  jti                VARCHAR(64)  NOT NULL UNIQUE,
  sharer_user_id     BIGINT       NOT NULL,
  share_type         VARCHAR(16)  NOT NULL,
  relation_id        BIGINT       NOT NULL,
  image_object_key   VARCHAR(512),
  watermark_payload  JSONB        NOT NULL,
  allow_claim        BOOLEAN      NOT NULL DEFAULT false,
  usage_limit        INT          NOT NULL DEFAULT 1000,
  usage_count        INT          NOT NULL DEFAULT 0,
  status             SMALLINT     NOT NULL DEFAULT 1,
  created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
  expires_at         TIMESTAMPTZ  NOT NULL,
  revoked_at         TIMESTAMPTZ,
  deleted_at         TIMESTAMPTZ,
  CONSTRAINT fk_sc_sharer     FOREIGN KEY (sharer_user_id) REFERENCES user_account(id) ON DELETE RESTRICT,
  CONSTRAINT ck_sc_share_type CHECK (share_type IN ('WRONG_ITEM','REVIEW_NODE','EXAM_DAY')),
  CONSTRAINT ck_sc_status     CHECK (status IN (1,2,3,4)),
  CONSTRAINT ck_sc_usage      CHECK (usage_count >= 0 AND usage_count <= usage_limit),
  CONSTRAINT ck_sc_ttl_7d     CHECK (expires_at <= created_at + INTERVAL '7 days')
);

CREATE INDEX idx_sc_sharer_created   ON share_card(sharer_user_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_sc_expires_active   ON share_card(expires_at) WHERE status = 1;
CREATE INDEX idx_sc_watermark_gin    ON share_card USING GIN (watermark_payload);
