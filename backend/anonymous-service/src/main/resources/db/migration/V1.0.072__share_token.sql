-- refs TDD §4.11 share_token · plan §5.S1
-- Schema: anon · 分享令牌
-- C9: all timestamps TIMESTAMPTZ
-- jti: JWT ID · UNIQUE · ShareFilter 校验 + Bloom Filter revoke

CREATE TABLE anon.share_token (
  id                BIGINT       PRIMARY KEY,              -- Snowflake
  jti               VARCHAR(64)  NOT NULL UNIQUE,          -- JWT ID · HS256 · ShareFilter 查
  sharer_student_id BIGINT       NOT NULL,                 -- 分享发起方 student_id
  share_type        VARCHAR(16)  NOT NULL,                 -- EXAM_DAY / QUESTION / REVIEW_NODE
  relation_id       VARCHAR(128) NOT NULL,                 -- 'question:{qid}' / 'node:{nid}'
  allow_claim       BOOLEAN      NOT NULL DEFAULT FALSE,   -- 是否允许接收方认领
  usage_limit       INT          NOT NULL DEFAULT 1000,
  usage_count       INT          NOT NULL DEFAULT 0,
  status            SMALLINT     NOT NULL DEFAULT 1,       -- 1 ACTIVE 2 EXPIRED 3 REVOKED 4 EXHAUSTED
  created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),   -- C9
  expires_at        TIMESTAMPTZ  NOT NULL                  -- C9 · ≤ created_at + 7d
);

-- 按分享方查有效 token · 分享历史列表
CREATE INDEX idx_share_sharer
  ON anon.share_token (sharer_student_id, created_at);

COMMENT ON TABLE  anon.share_token IS '分享令牌 · JWT HS256 · ≤7d 有效期 · ShareFilter 校验';
COMMENT ON COLUMN anon.share_token.jti IS 'JWT ID · UNIQUE · Bloom Filter 撤销 (D-Observer-Revoke)';
COMMENT ON COLUMN anon.share_token.status IS 'ACTIVE(1)→EXPIRED(2)|REVOKED(3)|EXHAUSTED(4)';
COMMENT ON COLUMN anon.share_token.relation_id IS '格式 question:{qid} / review_node:{nid} / exam:{eid}';
