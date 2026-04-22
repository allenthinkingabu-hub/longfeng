-- refs 业务与技术解决方案 §4.1 user_setting · 落地计划 §5.6 组 A
-- A4 blanket GIN on JSONB
CREATE TABLE user_settings (
  id               BIGINT       PRIMARY KEY,
  user_id          BIGINT       NOT NULL UNIQUE,
  push_channels    JSONB        NOT NULL DEFAULT '[]'::jsonb,
  learning_stage   VARCHAR(32),
  quiet_hours_from SMALLINT,
  quiet_hours_to   SMALLINT,
  preferences      JSONB        NOT NULL DEFAULT '{}'::jsonb,
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
  deleted_at       TIMESTAMPTZ,
  CONSTRAINT fk_settings_user FOREIGN KEY (user_id) REFERENCES user_account(id) ON DELETE RESTRICT,
  CONSTRAINT ck_settings_quiet_from CHECK (quiet_hours_from IS NULL OR quiet_hours_from BETWEEN 0 AND 23),
  CONSTRAINT ck_settings_quiet_to   CHECK (quiet_hours_to   IS NULL OR quiet_hours_to   BETWEEN 0 AND 23)
);

CREATE INDEX idx_user_settings_prefs_gin  ON user_settings USING GIN (preferences);
CREATE INDEX idx_user_settings_push_gin   ON user_settings USING GIN (push_channels);
