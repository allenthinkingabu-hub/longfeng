-- refs 落地计划 §5.6 / §5.7 step 4 · 本地开发重置用 · 生产禁用
-- 匿名域整组撤销 · observer/share 一并清理
DROP TABLE IF EXISTS observer_grant     CASCADE;
DROP TABLE IF EXISTS observer_session   CASCADE;
DROP TABLE IF EXISTS share_access_log   CASCADE;
DROP TABLE IF EXISTS share_card         CASCADE;
DROP TABLE IF EXISTS guest_wrong_item   CASCADE;
DROP TABLE IF EXISTS guest_quota        CASCADE;
DROP TABLE IF EXISTS guest_consent      CASCADE;
DROP TABLE IF EXISTS guest_session      CASCADE;
