-- refs 业务与技术解决方案 §4.10 consent_at/consent_type · 落地计划 §5.6 组 C
-- 未成年人保护合规 · consent_version 显式 NOT NULL (方案版本化)
CREATE TABLE guest_consent (
  id                 BIGINT       PRIMARY KEY,
  guest_session_id   BIGINT       NOT NULL,
  consent_type       SMALLINT     NOT NULL,
  consent_version    VARCHAR(16)  NOT NULL,
  guardian_name      VARCHAR(128),
  guardian_id_hash   CHAR(64),
  ip_hash            CHAR(64),
  user_agent_sha     CHAR(64),
  agreed_at          TIMESTAMPTZ  NOT NULL,
  created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT fk_consent_session FOREIGN KEY (guest_session_id) REFERENCES guest_session(id) ON DELETE RESTRICT,
  CONSTRAINT ck_consent_type CHECK (consent_type IN (1,2,3))
);

CREATE UNIQUE INDEX uq_consent_session_ver ON guest_consent(guest_session_id, consent_version);
