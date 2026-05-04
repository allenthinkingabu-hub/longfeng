-- refs S7 BUG-LF-09 · auth-service · WeChat login · JWT token store
-- A2 override: BIGSERIAL PK · A3 timestamptz · issued/expires/revoked_at
CREATE TABLE IF NOT EXISTS user_token (
  id             BIGSERIAL    PRIMARY KEY,
  user_id        BIGINT       NOT NULL,
  token_hash     VARCHAR(128) NOT NULL UNIQUE,
  device_fp_hash VARCHAR(128),
  issued_at      TIMESTAMPTZ  DEFAULT NOW(),
  expires_at     TIMESTAMPTZ  NOT NULL,
  revoked_at     TIMESTAMPTZ,
  CONSTRAINT fk_user_token_user FOREIGN KEY (user_id) REFERENCES user_account(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_user_token_user_id ON user_token(user_id);
CREATE INDEX IF NOT EXISTS idx_user_token_expires ON user_token(expires_at) WHERE revoked_at IS NULL;
