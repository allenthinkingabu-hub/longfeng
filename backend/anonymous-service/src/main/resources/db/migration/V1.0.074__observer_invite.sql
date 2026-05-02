-- refs TDD §4.12 observer_invite · plan §5.S1
-- Schema: anon · 观察者邀请码
-- C9: all timestamps TIMESTAMPTZ
-- invite_code: 6位大写字母+数字 · 'O-XXXX-XXX' 格式 · UNIQUE

CREATE TABLE anon.observer_invite (
  id           BIGINT       PRIMARY KEY,                   -- Snowflake
  invite_code  CHAR(6)      NOT NULL UNIQUE,               -- 6位大写字母+数字 (UUID.randomUUID shortcode)
  student_id   BIGINT       NOT NULL,                      -- 邀请发起方
  role         VARCHAR(16)  NOT NULL,                      -- PARENT / TEACHER
  status       SMALLINT     NOT NULL DEFAULT 1,            -- 1 PENDING 2 EXCHANGED 3 EXPIRED 4 REVOKED
  expires_at   TIMESTAMPTZ  NOT NULL,                      -- C9 · created_at + 24h
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()         -- C9
);

-- 按 student 查邀请历史
CREATE INDEX idx_observer_invite_student
  ON anon.observer_invite (student_id, created_at DESC);

COMMENT ON TABLE  anon.observer_invite IS '观察者邀请码 · 24h 有效 · 家长/教师用 · PENDING→EXCHANGED|EXPIRED|REVOKED';
COMMENT ON COLUMN anon.observer_invite.invite_code IS '6位大写字母+数字 · 学生告知家长/教师 · 兑换后生成 observer_session';
COMMENT ON COLUMN anon.observer_invite.role IS 'PARENT(30d session) / TEACHER(90d session)';
