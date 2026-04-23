-- refs S4 §8.7 Step 2 · ai_usage_log · V1.0.020→V1.0.023 renumber (DA drift · V1.0.020 占用于 S3)
-- 每次 LLM 调用 append 1 行 · 无 UPDATE/DELETE · 滚动清理走 S10
CREATE TABLE ai_usage_log (
  id            BIGSERIAL   PRIMARY KEY,
  user_id       BIGINT,
  item_id       BIGINT,
  provider      VARCHAR(16) NOT NULL,
  model         VARCHAR(64) NOT NULL,
  api_type      VARCHAR(16) NOT NULL,
  tokens_in     INTEGER,
  tokens_out    INTEGER,
  cost_cents    INTEGER,
  latency_ms    INTEGER,
  status        SMALLINT    NOT NULL DEFAULT 0,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_usage_provider  CHECK (provider IN ('dashscope','openai','stub')),
  CONSTRAINT ck_usage_api_type  CHECK (api_type IN ('chat','embed')),
  CONSTRAINT ck_usage_status    CHECK (status IN (0,1,9)),
  CONSTRAINT ck_usage_tokens_in CHECK (tokens_in IS NULL OR tokens_in >= 0),
  CONSTRAINT ck_usage_tokens_out CHECK (tokens_out IS NULL OR tokens_out >= 0)
);

CREATE INDEX idx_usage_created        ON ai_usage_log(created_at DESC);
CREATE INDEX idx_usage_user_created   ON ai_usage_log(user_id, created_at DESC);
CREATE INDEX idx_usage_provider_day   ON ai_usage_log(provider, created_at);
