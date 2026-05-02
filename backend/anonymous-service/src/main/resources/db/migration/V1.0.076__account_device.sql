-- refs TDD §4.13 account_device · plan §5.S1
-- Schema: anon · 设备指纹软绑定（5 来源）
-- C9: all timestamps TIMESTAMPTZ
-- D4-1: 设备指纹漂移风险 · 软绑定而非硬绑 · first_seen/last_seen + login_count 追踪

CREATE TABLE anon.account_device (
  id            BIGINT       PRIMARY KEY,                  -- Snowflake
  student_id    BIGINT       NOT NULL,
  device_fp     VARCHAR(128) NOT NULL,                     -- 5来源组合指纹: Canvas+WebGL+AudioContext+UA+Accept-Language
  platform      VARCHAR(16),                               -- H5 / MINIP / IOS / ANDROID
  first_seen_at TIMESTAMPTZ  NOT NULL DEFAULT now(),       -- C9
  last_seen_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),       -- C9 · 每次登录更新
  login_count   INT          NOT NULL DEFAULT 1,
  CONSTRAINT uq_account_device UNIQUE (student_id, device_fp)
);

-- 按指纹反查 student · DeviceFingerprintService.matchPolicy 用
CREATE INDEX idx_account_device_fp
  ON anon.account_device (device_fp);

COMMENT ON TABLE  anon.account_device IS '设备指纹软绑定 · 5来源组合 · 漂移风险见 D4-1 · 非硬约束';
COMMENT ON COLUMN anon.account_device.device_fp IS '5来源: Canvas/WebGL/AudioContext/UA/Accept-Language 组合 hash';
COMMENT ON COLUMN anon.account_device.platform IS 'H5/MINIP/IOS/ANDROID · FingerprintMatchPolicy 根据 platform 调整权重';
