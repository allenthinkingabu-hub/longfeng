-- refs TDD §4.10 guest_rate_bucket · plan §5.S1
-- Schema: anon · Redis 限速降级写库 (Bucket4j Redis fallback)
-- 当 Redis 不可用时 · 降级到此表做 DB 级计数
-- C9: bucket_date 为 DATE (Asia/Shanghai 自然日) · 不含时区字段

CREATE TABLE anon.guest_rate_bucket (
  device_fp   VARCHAR(128) NOT NULL,
  ip_hash     VARCHAR(64)  NOT NULL,
  bucket_date DATE         NOT NULL,               -- Asia/Shanghai 自然日 · GuestRateLimiter 转换
  count       INT          NOT NULL DEFAULT 0,
  CONSTRAINT pk_guest_rate PRIMARY KEY (device_fp, ip_hash, bucket_date)
);

COMMENT ON TABLE  anon.guest_rate_bucket IS 'Bucket4j Redis 失败降级写库 · 自然日计数 · 每天自动过期（job 清理 > 7d）';
COMMENT ON COLUMN anon.guest_rate_bucket.bucket_date IS 'Asia/Shanghai 自然日 · app 层转换后存入 · 非 UTC';
COMMENT ON COLUMN anon.guest_rate_bucket.count IS '当日该设备+IP 组合的分析请求计数 · 超限返 429';
