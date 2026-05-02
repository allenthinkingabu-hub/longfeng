-- refs TDD §4.7 wb_push_log · plan §5.S1
-- Schema: review · 推送日志（每通道一行）
-- C9: all timestamps TIMESTAMPTZ

CREATE TABLE review.wb_push_log (
  id           BIGINT       PRIMARY KEY,                    -- Snowflake
  task_id      BIGINT       NOT NULL,                       -- → wb_push_task.id
  channel      VARCHAR(16)  NOT NULL,                       -- WX_MP / APP / EMAIL / SMS
  request_id   VARCHAR(64),                                 -- 第三方平台请求 ID（微信 msgid 等）
  success      BOOLEAN      NOT NULL,
  error_code   VARCHAR(32),
  error_msg    TEXT,
  delivered_at TIMESTAMPTZ,                                 -- C9 · 实际投递确认时间
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()          -- C9
);

-- 按 task 查推送日志 · 按时间倒序（最新失败优先）
CREATE INDEX idx_push_log_task
  ON review.wb_push_log (task_id, created_at DESC);

COMMENT ON TABLE  review.wb_push_log IS '推送日志 · 每通道每次尝试一行 · 用于故障追查 + 成功率统计';
COMMENT ON COLUMN review.wb_push_log.channel IS 'WX_MP/APP/EMAIL/SMS · 与 wb_push_task.channels 枚举一致';
COMMENT ON COLUMN review.wb_push_log.request_id IS '第三方平台请求 ID · 微信 msgid / APNs uuid 等 · 用于投诉追查';
