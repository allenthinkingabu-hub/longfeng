-- refs S7 BUG-LF-09 · auth-service · add wechat_openid + wechat_unionid to user_account
ALTER TABLE user_account ADD COLUMN IF NOT EXISTS wechat_openid  VARCHAR(64) UNIQUE;
ALTER TABLE user_account ADD COLUMN IF NOT EXISTS wechat_unionid VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_user_account_wechat_openid ON user_account(wechat_openid) WHERE wechat_openid IS NOT NULL;
