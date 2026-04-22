-- refs 业务与技术解决方案 §2A.2 学科 · §4.1 student 复用 user · 落地计划 §5.6 组 A
-- A2 override: BIGINT PK (Snowflake) · A3 timestamptz · A5 deleted_at soft-delete · A7 enum via CHECK
CREATE TABLE user_account (
  id               BIGINT       PRIMARY KEY,
  username         VARCHAR(64)  NOT NULL UNIQUE,
  phone_hash       CHAR(64),
  email_hash       CHAR(64),
  role             VARCHAR(16)  NOT NULL,
  grade_code       VARCHAR(16),
  status           SMALLINT     NOT NULL DEFAULT 1,
  timezone         VARCHAR(32)  NOT NULL DEFAULT 'Asia/Shanghai',
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
  deleted_at       TIMESTAMPTZ,
  CONSTRAINT ck_user_role   CHECK (role IN ('STUDENT','PARENT','TEACHER','ADMIN')),
  CONSTRAINT ck_user_status CHECK (status IN (1,2,9))
);

CREATE UNIQUE INDEX uq_user_phone_hash ON user_account(phone_hash) WHERE phone_hash IS NOT NULL;
CREATE UNIQUE INDEX uq_user_email_hash ON user_account(email_hash) WHERE email_hash IS NOT NULL;
CREATE INDEX idx_user_role_active   ON user_account(role) WHERE deleted_at IS NULL;
