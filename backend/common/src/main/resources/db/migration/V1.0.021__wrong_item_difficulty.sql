-- refs G-Biz Q2-R1 · D2 drift resolution · design/arch/s3-wrongbook.md §4.4
-- 为 wrong_item 增加难度列 · 1..5 用户主动标注或 S4 AI 推断 · 本 Phase 仅 CRUD
-- NULL 表示未设定难度 · CHECK 与方案 §2A.3 对齐
ALTER TABLE wrong_item
  ADD COLUMN difficulty SMALLINT;

ALTER TABLE wrong_item
  ADD CONSTRAINT ck_wrong_item_difficulty
  CHECK (difficulty IS NULL OR difficulty BETWEEN 1 AND 5);
