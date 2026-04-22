-- refs 落地计划 §5.1 A8 审计域 append-only · §5.6 组 D · actor_type
-- 无 deleted_at · 无 updated_at · 无 FK · 冷存储归档延后到 S10+
CREATE TABLE audit_log (
  id             BIGINT       PRIMARY KEY,
  actor_type     VARCHAR(16)  NOT NULL,
  actor_id       BIGINT,
  action         VARCHAR(64)  NOT NULL,
  target_type    VARCHAR(32),
  target_id      BIGINT,
  ip_hash        CHAR(64),
  ua_sha         CHAR(64),
  request_id     VARCHAR(64),
  payload        JSONB,
  created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT ck_audit_actor_type CHECK (actor_type IN ('USER','SYSTEM','ADMIN','ANON'))
);

CREATE INDEX idx_audit_created      ON audit_log(created_at DESC);
CREATE INDEX idx_audit_actor        ON audit_log(actor_type, actor_id, created_at DESC);
CREATE INDEX idx_audit_target       ON audit_log(target_type, target_id, created_at DESC);
CREATE INDEX idx_audit_payload_gin  ON audit_log USING GIN (payload);
