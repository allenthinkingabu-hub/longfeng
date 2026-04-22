-- refs 业务与技术解决方案 §4.10 guest_session · §10.6 匿名态入口 · 落地计划 §5.6 组 C · device_fp_hash
-- A6: 匿名业务主表 · 对 user_account / wrong_item 走 FK RESTRICT (仅 claim 后才非空)
CREATE TABLE guest_session (
  id                    BIGINT       PRIMARY KEY,
  device_fp_hash        CHAR(64)     NOT NULL,
  ip_hash               CHAR(64),
  ua_sha                CHAR(64),
  entry_source          VARCHAR(32),
  experiment_bucket     VARCHAR(32),
  analysis_snapshot     JSONB,
  status                SMALLINT     NOT NULL DEFAULT 0,
  claimed_user_id       BIGINT,
  claimed_wrong_item_id BIGINT,
  claimed_at            TIMESTAMPTZ,
  created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
  expires_at            TIMESTAMPTZ  NOT NULL,
  deleted_at            TIMESTAMPTZ,
  CONSTRAINT fk_guest_claim_user FOREIGN KEY (claimed_user_id)       REFERENCES user_account(id) ON DELETE RESTRICT,
  CONSTRAINT fk_guest_claim_item FOREIGN KEY (claimed_wrong_item_id) REFERENCES wrong_item(id)   ON DELETE RESTRICT,
  CONSTRAINT ck_guest_status   CHECK (status IN (0,1,2,3,4,9)),
  CONSTRAINT ck_guest_fp_len   CHECK (char_length(device_fp_hash) = 64),
  CONSTRAINT ck_guest_expires  CHECK (expires_at > created_at)
);

CREATE INDEX idx_guest_fp_created    ON guest_session(device_fp_hash, created_at);
CREATE INDEX idx_guest_expires_live  ON guest_session(expires_at) WHERE status IN (0,1,2);
CREATE UNIQUE INDEX uq_guest_claim_item ON guest_session(claimed_wrong_item_id) WHERE claimed_wrong_item_id IS NOT NULL;
CREATE INDEX idx_guest_snapshot_gin  ON guest_session USING GIN (analysis_snapshot);
