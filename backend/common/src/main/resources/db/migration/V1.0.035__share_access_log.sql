-- refs 业务与技术解决方案 §4.11 share_token_audit · 落地计划 §5.6 组 C
-- A6: 访问日志 事件表 无 FK (jti 不做 FK 以便 share_card 归档后仍保留日志)
CREATE TABLE share_access_log (
  id                     BIGINT       PRIMARY KEY,
  jti                    VARCHAR(64)  NOT NULL,
  viewer_device_fp_hash  CHAR(64),
  viewer_ip_hash         CHAR(64),
  upgraded_user_id       BIGINT,
  viewed_at              TIMESTAMPTZ  NOT NULL,
  created_at             TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_sal_jti_viewed ON share_access_log(jti, viewed_at DESC);
