-- refs 业务与技术解决方案 §4.12 observer_session · 落地计划 §5.6 组 C · scope=READ 唯一值
CREATE TABLE observer_session (
  id                 BIGINT       PRIMARY KEY,
  jti                VARCHAR(64)  NOT NULL UNIQUE,
  student_user_id    BIGINT       NOT NULL,
  role               VARCHAR(16)  NOT NULL,
  scope              VARCHAR(16)  NOT NULL DEFAULT 'READ',
  device_fp_hash     CHAR(64),
  status             SMALLINT     NOT NULL DEFAULT 1,
  issued_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
  last_seen_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
  expires_at         TIMESTAMPTZ  NOT NULL,
  revoked_at         TIMESTAMPTZ,
  created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT fk_os_student FOREIGN KEY (student_user_id) REFERENCES user_account(id) ON DELETE RESTRICT,
  CONSTRAINT ck_os_role   CHECK (role IN ('PARENT','TEACHER')),
  CONSTRAINT ck_os_scope  CHECK (scope = 'READ'),
  CONSTRAINT ck_os_status CHECK (status IN (1,2,3))
);

CREATE INDEX idx_os_student_status  ON observer_session(student_user_id, status);
CREATE INDEX idx_os_expires_active  ON observer_session(expires_at) WHERE status = 1;
