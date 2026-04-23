-- refs 落地计划 §9 · ADR 0014 · Q-B/F 决策
-- S5 调整 review_plan schema：S1 V1.0.016 建成"单行 plan · current_level 游走"· 与 S5 G-Biz Q-B "7 行 node_index 0..6 · 节点独立 SM-2" 冲突
-- 本迁移做最小侵入调整（backwards compatible · S1 已插数据自动 node_index=0）：
--   1. DROP UNIQUE (wrong_item_id)         · 允许一条错题 7 行
--   2. ADD node_index                      · 0..6 节点编号
--   3. ADD dispatch_version                · 乐观锁（S5 complete + XXL-Job 派发）
--   4. ADD consecutive_good_count          · Q-G mastered 触发（连续 3 次 ease≥2.8）
--   5. CREATE UNIQUE INDEX (wrong_item_id, node_index) WHERE deleted_at IS NULL · 7 行唯一兜底

ALTER TABLE review_plan
  DROP CONSTRAINT IF EXISTS review_plan_wrong_item_id_key;

ALTER TABLE review_plan
  ADD COLUMN IF NOT EXISTS node_index SMALLINT NOT NULL DEFAULT 0;

ALTER TABLE review_plan
  ADD CONSTRAINT ck_rp_node_index CHECK (node_index BETWEEN 0 AND 6);

ALTER TABLE review_plan
  ADD COLUMN IF NOT EXISTS dispatch_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE review_plan
  ADD COLUMN IF NOT EXISTS consecutive_good_count SMALLINT NOT NULL DEFAULT 0;

ALTER TABLE review_plan
  ADD CONSTRAINT ck_rp_cgood CHECK (consecutive_good_count BETWEEN 0 AND 3);

-- 兜底唯一索引 · 支撑 SC-07.AC-1 Consumer 幂等 ON CONFLICT DO NOTHING
CREATE UNIQUE INDEX IF NOT EXISTS uk_review_plan_item_node
  ON review_plan (wrong_item_id, node_index)
  WHERE deleted_at IS NULL;

COMMENT ON COLUMN review_plan.node_index IS 'S5 Q-B · 艾宾浩斯节点索引 0..6';
COMMENT ON COLUMN review_plan.dispatch_version IS 'S5 乐观锁 · complete + XXL-Job CAS 兜底';
COMMENT ON COLUMN review_plan.consecutive_good_count IS 'S5 Q-G · 连续 3 次 ease≥2.8 触发 mastered';
