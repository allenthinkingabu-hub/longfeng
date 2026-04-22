-- refs 业务与技术解决方案 §4.12 observer_invite (重命名 observer_grant · 更符合业务语义) · 落地计划 §5.6 组 C
-- A6: 业务主表 FK RESTRICT · invite_code 6 位长度硬约束
CREATE TABLE observer_grant (
  id                     BIGINT       PRIMARY KEY,
  invite_code            CHAR(6)      NOT NULL UNIQUE,
  student_user_id        BIGINT       NOT NULL,
  role                   VARCHAR(16)  NOT NULL,
  status                 SMALLINT     NOT NULL DEFAULT 1,
  exchanged_session_id   BIGINT,
  expires_at             TIMESTAMPTZ  NOT NULL,
  exchanged_at           TIMESTAMPTZ,
  created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT fk_og_student FOREIGN KEY (student_user_id)      REFERENCES user_account(id)      ON DELETE RESTRICT,
  CONSTRAINT fk_og_session FOREIGN KEY (exchanged_session_id) REFERENCES observer_session(id)  ON DELETE RESTRICT,
  CONSTRAINT ck_og_role   CHECK (role IN ('PARENT','TEACHER')),
  CONSTRAINT ck_og_status CHECK (status IN (1,2,3,4)),
  CONSTRAINT ck_og_code   CHECK (char_length(invite_code) = 6)
);

CREATE INDEX idx_og_student  ON observer_grant(student_user_id, status);
