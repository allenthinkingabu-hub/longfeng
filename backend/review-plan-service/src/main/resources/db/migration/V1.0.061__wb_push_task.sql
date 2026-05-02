-- refs TDD §4.7 wb_push_task · plan §5.S1
-- Schema: review · 推送任务调度
-- C6: idempotency_key VARCHAR(64) NOT NULL UNIQUE · md5(node_id + scheduled_at) 防重复推送
-- C9: all timestamps TIMESTAMPTZ
-- version: 乐观锁 · XXL-Job CAS 抢占

CREATE TABLE review.wb_push_task (
  id              BIGINT       PRIMARY KEY,                  -- Snowflake
  node_id         BIGINT       NOT NULL,                     -- 软外键 → wb_review_node.id
  student_id      BIGINT       NOT NULL,
  channels        VARCHAR(64)  NOT NULL,                     -- 'WX_MP,APP,EMAIL'
  scheduled_at    TIMESTAMPTZ  NOT NULL,                     -- C9 · 预定推送时间
  status          SMALLINT     NOT NULL DEFAULT 0,           -- 0 等待 1 处理中 2 成功 3 部分成功 9 失败
  tried_times     SMALLINT     NOT NULL DEFAULT 0,
  last_error      TEXT,
  idempotency_key VARCHAR(64)  NOT NULL,                     -- C6: md5(node_id + ':' + scheduled_at::text)
  version         INT          NOT NULL DEFAULT 0,           -- 乐观锁 · XXL-Job CAS 抢占
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),       -- C9 审计字段
  CONSTRAINT uq_push_idem UNIQUE (idempotency_key)
);

-- 等待/处理中任务扫描 · XXL-Job 轮询 · 热点查询
CREATE INDEX idx_push_sched
  ON review.wb_push_task (status, scheduled_at)
  WHERE status IN (0, 1);

COMMENT ON TABLE  review.wb_push_task IS '推送任务调度表 · XXL-Job 扫 pending → 多通道推送';
COMMENT ON COLUMN review.wb_push_task.idempotency_key IS 'C6 幂等键 · md5(node_id:scheduled_at) · UNIQUE 约束防重复投递';
COMMENT ON COLUMN review.wb_push_task.channels IS '推送通道逗号分隔 · WX_MP/APP/EMAIL/SMS';
COMMENT ON COLUMN review.wb_push_task.version IS '乐观锁 · XXL-Job CAS 抢占 · 防并发双推';
