-- refs TDD §4.2 (pg_trgm GIN idx_wb_q_trgm) · plan §5.S1 V1.0.001__bootstrap_extensions
-- C9: pg_trgm + btree_gin extensions · idempotent
-- pg_trgm: 模糊文本检索 (ocr_text % '二次函数') · GIN gin_trgm_ops 索引
-- btree_gin: 支持 GIN 索引做 btree-type 查询（复合 GIN 索引常用）
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS btree_gin;
