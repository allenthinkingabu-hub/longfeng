-- refs G-Biz Q1-R1 · D1 drift resolution · design/arch/s3-wrongbook.md §4.4
-- 为 wrong_item 增加乐观锁 version 列 · JPA @Version Long · PATCH 必带 version · 冲突 409
-- 已有行回填 version=0 · BIGINT 与 Snowflake id 对齐类型
ALTER TABLE wrong_item
  ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
