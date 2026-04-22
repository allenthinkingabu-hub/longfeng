-- refs 业务与技术解决方案 §4.2（vector(1024) + ivfflat）· 落地计划 §5.6 组 A · Q2 pg_trgm 延后 M4
-- S1 G-Biz: Q2 → 本 Phase 不加 pg_trgm
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
