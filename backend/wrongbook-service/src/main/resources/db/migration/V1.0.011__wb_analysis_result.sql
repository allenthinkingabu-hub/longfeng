-- refs TDD §4.3 wb_analysis_result · plan §5.S1
-- Schema: wrongbook · AI 分析结果（版本化 · 多次分析/换模型）
-- C9: all timestamps TIMESTAMPTZ
-- Note: version here is application-level (same question, different analysis runs), NOT @Version lock

CREATE TABLE wrongbook.wb_analysis_result (
  id               BIGINT       PRIMARY KEY,                  -- Snowflake
  question_id      BIGINT       NOT NULL REFERENCES wrongbook.wb_question(id),
  version          INT          NOT NULL,                     -- 同 question 的多次分析（重跑/换模型）
  model_provider   VARCHAR(32)  NOT NULL,                     -- openai / qianwen / zhipu / local
  model_name       VARCHAR(64)  NOT NULL,                     -- gpt-4o-mini / qwen-vl-max / glm-4v
  input_tokens     INT,
  output_tokens    INT,
  cost_cents       INT,
  stem_text        TEXT,
  student_answer   TEXT,
  correct_answer   TEXT,
  error_type       VARCHAR(32),                              -- CONCEPT/CARELESS/METHOD/CALC/UNKNOWN
  error_reason     TEXT,
  solution_steps   JSONB,                                    -- [{step,explain,formula}]
  knowledge_points JSONB,                                    -- [{code,name,bloom_level}]
  difficulty       SMALLINT,                                 -- 1..5
  raw_json         JSONB        NOT NULL,                    -- 模型原始输出（保真）
  status           SMALLINT     NOT NULL,                    -- 0 RUNNING 1 OK 2 LOW_CONFIDENCE 9 FAILED
  finished_at      TIMESTAMPTZ,
  tenant_id        BIGINT       NOT NULL,                    -- 多租户隔离 (plan §5.S1 出口门禁)
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),      -- C9
  CONSTRAINT uq_analysis_qid_version UNIQUE (question_id, version)
);

-- 按题目 + 状态查最新分析 · SSE 推送结果时用
CREATE INDEX idx_analysis_qid_status
  ON wrongbook.wb_analysis_result (question_id, status);

COMMENT ON TABLE  wrongbook.wb_analysis_result IS 'AI 分析结果表 · 一题可多次分析 · version 区分不同模型/重跑';
COMMENT ON COLUMN wrongbook.wb_analysis_result.version IS '应用级版本号（重跑序号）· 非乐观锁';
COMMENT ON COLUMN wrongbook.wb_analysis_result.raw_json IS '模型原始输出保真字段 · 不做结构假设 · 供 debug/重解析';
