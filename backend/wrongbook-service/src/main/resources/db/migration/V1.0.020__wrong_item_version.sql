-- S3 · D1 drift resolution (Q1-R1) · ADR 0011 optimistic lock
-- wrong_item.version BIGINT — managed by JPA @Version, business code must never increment manually.
ALTER TABLE wrong_item ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
