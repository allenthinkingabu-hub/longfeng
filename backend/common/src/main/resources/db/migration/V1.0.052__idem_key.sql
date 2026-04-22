-- refs 落地计划 §5.6 组 D · 幂等键 · 网关/业务入口统一去重
-- scope 为 API path 或 job name · request_hash 识别重复请求
CREATE TABLE idem_key (
  id                 BIGINT       PRIMARY KEY,
  scope              VARCHAR(32)  NOT NULL,
  idem_key           VARCHAR(128) NOT NULL,
  request_hash       CHAR(64)     NOT NULL,
  response_snapshot  JSONB,
  status             SMALLINT     NOT NULL DEFAULT 0,
  created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
  expires_at         TIMESTAMPTZ  NOT NULL,
  CONSTRAINT ck_idem_status CHECK (status IN (0,1,9))
);

CREATE UNIQUE INDEX uq_idem_key     ON idem_key(scope, idem_key);
CREATE INDEX idx_idem_expires       ON idem_key(expires_at);
