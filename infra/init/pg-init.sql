-- =============================================================
-- longfeng-wrongbook · PostgreSQL dev init
-- Runs once at container first-start (docker-entrypoint-initdb.d)
-- =============================================================

-- pgvector: dense-vector cosine similarity search (hybrid search)
CREATE EXTENSION IF NOT EXISTS vector;

-- pg_trgm: trigram similarity (full-text + fuzzy search MVP, ES not used until P1)
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- btree_gin: composite GIN index support (multi-column filter + text search combos)
CREATE EXTENSION IF NOT EXISTS btree_gin;

-- pg_stat_statements: query performance analysis (referenced in TDD §21.4)
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

-- Verify
DO $$
BEGIN
  ASSERT (SELECT COUNT(*) FROM pg_extension WHERE extname IN ('vector','pg_trgm','btree_gin','pg_stat_statements')) = 4,
    'One or more required extensions failed to install';
  RAISE NOTICE 'All 4 extensions installed successfully: vector, pg_trgm, btree_gin, pg_stat_statements';
END $$;
