-- refs TDD §4.8 ebbinghaus_node_config · plan §5.S1
-- Schema: review · 艾宾浩斯节点配置（热更新 · admin 后台可动态增配）
-- 7 行默认节点 · T0/H1/D1/D3/D7/D15/D30 对应 offset_seconds
-- C9: 本表无 timestamp 列（静态配置表 · 无时序需求）

CREATE TABLE IF NOT EXISTS review.ebbinghaus_node_config (
  id                 SERIAL      PRIMARY KEY,
  strategy_code      VARCHAR(32) NOT NULL,                   -- EBBINGHAUS_STD / INTENSIVE / SLOW
  level              SMALLINT    NOT NULL,                   -- 0..6
  level_code         VARCHAR(8)  NOT NULL,                   -- INITIAL/H1/D1/D3/D7/D15/D30
  offset_seconds     BIGINT      NOT NULL,                   -- 相对 T0 的秒数
  pre_notice_seconds INT         NOT NULL DEFAULT 1800,      -- 提前通知窗口 (default 30min)
  window_seconds     INT         NOT NULL DEFAULT 86400,     -- 复习窗口宽度 (default 24h)
  enabled            BOOLEAN     NOT NULL DEFAULT TRUE,
  CONSTRAINT uq_strat_level UNIQUE (strategy_code, level)
);

-- EBBINGHAUS_STD 标准策略 7 节点初始化
-- T0=0s · H1=1h · D1=1d · D3=3d · D7=7d · D15=15d · D30=30d
INSERT INTO review.ebbinghaus_node_config
  (strategy_code, level, level_code, offset_seconds)
VALUES
  ('EBBINGHAUS_STD', 0, 'INITIAL',   0),
  ('EBBINGHAUS_STD', 1, 'H1',        3600),
  ('EBBINGHAUS_STD', 2, 'D1',        86400),
  ('EBBINGHAUS_STD', 3, 'D3',        259200),
  ('EBBINGHAUS_STD', 4, 'D7',        604800),
  ('EBBINGHAUS_STD', 5, 'D15',       1296000),
  ('EBBINGHAUS_STD', 6, 'D30',       2592000);

COMMENT ON TABLE  review.ebbinghaus_node_config IS '艾宾浩斯节点配置 · 热更新 · admin 后台可动态增配 INTENSIVE/SLOW 策略';
COMMENT ON COLUMN review.ebbinghaus_node_config.offset_seconds IS '相对 T0(拍题时刻)的秒数 · review_node.due_at = question.created_at + offset_seconds';
COMMENT ON COLUMN review.ebbinghaus_node_config.pre_notice_seconds IS '提前推送窗口 · review_node.ready_at = due_at - pre_notice_seconds';
COMMENT ON COLUMN review.ebbinghaus_node_config.window_seconds IS '复习窗口宽度 · review_node.window_end_at = due_at + window_seconds';
