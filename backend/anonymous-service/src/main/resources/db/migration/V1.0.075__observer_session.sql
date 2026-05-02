-- refs TDD §4.12 observer_session · plan §5.S1
-- Schema: anon · 观察者会话（家长/教师只读 token）
-- C9: all timestamps TIMESTAMPTZ
-- jti: JWT ID · UNIQUE · scope=READ · ObserverFilter 校验 + Bloom Filter revoke (D-Observer-Revoke)
-- D-Observer-TTL: PARENT 30d / TEACHER 90d · expires_at 由 invite.role 决定

CREATE TABLE anon.observer_session (
  id                    BIGINT       PRIMARY KEY,          -- Snowflake
  jti                   VARCHAR(64)  NOT NULL UNIQUE,      -- JWT ID · ObserverFilter 校验
  student_id            BIGINT       NOT NULL,             -- 被观察学生
  role                  VARCHAR(16)  NOT NULL,             -- PARENT / TEACHER
  device_fp             VARCHAR(128),                      -- 观察方设备指纹（optional）
  status                SMALLINT     NOT NULL DEFAULT 1,   -- 1 ACTIVE 2 EXPIRED 3 REVOKED_BY_STUDENT
  issued_at             TIMESTAMPTZ  NOT NULL DEFAULT now(), -- C9
  last_seen_at          TIMESTAMPTZ  NOT NULL DEFAULT now(), -- C9 · 每次请求更新
  expires_at            TIMESTAMPTZ  NOT NULL,             -- C9 · PARENT: +30d / TEACHER: +90d
  revoked_by_student_at TIMESTAMPTZ                        -- C9 · 学生主动撤销时间
);

-- 按 student 查活跃观察者（学生管理观察者列表）
CREATE INDEX idx_obs_student
  ON anon.observer_session (student_id, status);

-- jti 快速查 · ObserverFilter + Bloom revoke 热点
CREATE INDEX idx_obs_jti
  ON anon.observer_session (jti);

COMMENT ON TABLE  anon.observer_session IS '观察者会话 · scope=READ · PARENT 30d / TEACHER 90d TTL · 学生可主动撤销';
COMMENT ON COLUMN anon.observer_session.jti IS 'JWT ID · UNIQUE · D-Observer-Revoke 撤销查询热点';
COMMENT ON COLUMN anon.observer_session.status IS 'ACTIVE(1)→EXPIRED(2)|REVOKED_BY_STUDENT(3)';
COMMENT ON COLUMN anon.observer_session.expires_at IS 'D-Observer-TTL: PARENT=30d / TEACHER=90d · 由兑换时 invite.role 决定';
