-- refs TDD §4.10 guest_session · plan §5.S1
-- Schema: anon (anonymous-service private · DB: anonymous)
-- C9: all timestamps TIMESTAMPTZ · BRIN index for expires_at range scan
-- C3 红线: 匿名 session 永不写 wb_* 表 (跨 schema 禁止)
-- device_fp: 5来源组合指纹 hash (Canvas/WebGL/AudioContext/UA/Accept-Language)

CREATE SCHEMA IF NOT EXISTS anon;

CREATE TABLE anon.guest_session (
  id                    BIGINT       PRIMARY KEY,            -- Snowflake
  device_fp             VARCHAR(128) NOT NULL,               -- 5来源组合指纹 hash
  ip_hash               VARCHAR(64),                         -- HMAC-SHA256(ip) · 不存明文 IP
  ua                    VARCHAR(256),
  entry_source          VARCHAR(32),                         -- ad / qr / share / direct
  experiment_bucket     VARCHAR(32),                         -- A/B 桶
  image_tmp_url         VARCHAR(512),                        -- D-Guest-Storage 5min 短签
  analysis_result_json  JSONB,                               -- AI 结构化结果快照
  consent_at            TIMESTAMPTZ,                         -- C9
  consent_type          SMALLINT,                            -- 1 ADULT 2 MINOR_WITH_GUARDIAN 3 MINOR_NO_GUARDIAN
  status                SMALLINT     NOT NULL DEFAULT 0,     -- 0 CREATED 1 ANALYZING 2 RESULT_READY 3 FAILED 4 CLAIMED 9 EXPIRED
  version               INT          NOT NULL DEFAULT 0,     -- 状态机 CAS (D-State · 防 expire/claim 并发竞态)
  claimed_by_student_id BIGINT,                              -- 认领后填 · 软指针到 user_account（跨仓）
  claimed_question_id   BIGINT,                              -- 认领后填 · 软指针到 wb_question（跨 schema）
  created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(), -- C9
  expires_at            TIMESTAMPTZ  NOT NULL,               -- created_at + 24h
  claimed_at            TIMESTAMPTZ                          -- C9
);

-- 设备指纹 + 日期查 · 限流 + 去重
CREATE INDEX idx_guest_fp_day
  ON anon.guest_session (device_fp, created_at);

-- 过期扫描 · GuestSessionExpiryJob 用 · BRIN 效率高（时序单调递增）
CREATE INDEX idx_guest_expires_brin
  ON anon.guest_session USING BRIN (expires_at);

-- 认领唯一性 · 一道题只能被认领一次
CREATE UNIQUE INDEX uq_guest_claim
  ON anon.guest_session (claimed_question_id)
  WHERE claimed_question_id IS NOT NULL;

COMMENT ON TABLE  anon.guest_session IS '游客会话 · 24h TTL · C3:永不写 wb_* 表';
COMMENT ON COLUMN anon.guest_session.device_fp IS '5来源组合指纹 hash: Canvas+WebGL+AudioContext+UA+Accept-Language';
COMMENT ON COLUMN anon.guest_session.status IS 'CREATED(0)→ANALYZING(1)→RESULT_READY(2)|FAILED(3)→CLAIMED(4)|EXPIRED(9)';
COMMENT ON COLUMN anon.guest_session.expires_at IS 'created_at + 24h · GuestSessionExpiryJob BRIN 扫过期';
