-- refs TDD §4.11 share_token_audit · plan §5.S1
-- Schema: anon · 分享访问审计日志
-- C9: all timestamps TIMESTAMPTZ
-- 每次 token 被访问一行 · 接收方注册成功后 upgraded_student_id 回填

CREATE TABLE anon.share_token_audit (
  id                  BIGINT       PRIMARY KEY,            -- Snowflake
  jti                 VARCHAR(64)  NOT NULL,               -- → share_token.jti (软外键)
  viewer_device_fp    VARCHAR(128),                        -- 访问方设备指纹
  viewer_ip_hash      VARCHAR(64),                         -- HMAC-SHA256(viewer_ip)
  upgraded_student_id BIGINT,                              -- 接收方注册成功后回填
  viewed_at           TIMESTAMPTZ  NOT NULL DEFAULT now()  -- C9
);

-- 按 jti 查访问记录 · 分享统计 / 滥用检测
CREATE INDEX idx_share_audit_jti
  ON anon.share_token_audit (jti, viewed_at);

COMMENT ON TABLE  anon.share_token_audit IS '分享访问审计 · 每次 token 访问一行 · 注册成功后 upgraded_student_id 回填';
COMMENT ON COLUMN anon.share_token_audit.upgraded_student_id IS '访问方注册成功后回填 · 便于统计分享→注册转化漏斗';
