-- ═══════════════════════════════════════════════════════════════════
-- L4 落地穿透 · Postgres 真实数据查询
-- 测试: SC-11 + SC-12 + SC-01 happy path 后跑这套 query
-- 用法: PGPASSWORD=wb psql -h localhost -p 15432 -U postgres -d longfeng_dev -f qa/sc-11-12-hybrid/sql-checks.sql
-- ═══════════════════════════════════════════════════════════════════

\timing on
\pset pager off

-- ── Q0 · DB 健康检查 ──────────────────────────────────────────────
SELECT current_database() AS db, current_user AS usr, now() AS server_now;
SELECT table_schema, table_name FROM information_schema.tables
  WHERE table_schema = 'public' ORDER BY table_name;

-- ── Q1 · guest_session 新增 (验 GuestController.analyze 真写库) ──
\echo '=== Q1 · guest_session 最近 1 小时新增 ==='
SELECT id, device_fp, quota_used, quota_reset_at, created_at
  FROM guest_session
  WHERE created_at > now() - interval '1 hour'
  ORDER BY id DESC LIMIT 5;
SELECT count(*) AS guest_session_count_last_hour FROM guest_session
  WHERE created_at > now() - interval '1 hour';

-- ── Q2 · wrong_item_analysis (验 ai-analysis 真触发任务) ──────────
\echo '=== Q2 · wrong_item_analysis (含 task_id + status + model) ==='
SELECT id, task_id, status, model, latency_ms, created_at
  FROM wrong_item_analysis
  WHERE created_at > now() - interval '1 hour'
  ORDER BY id DESC LIMIT 5;
SELECT count(*) AS analysis_count_last_hour FROM wrong_item_analysis
  WHERE created_at > now() - interval '1 hour';

-- ── Q3 · ai_usage_log 真 token (核心铁证 · 监督第 7 项) ─────────────
\echo '=== Q3 · ai_usage_log 真实 LLM token 消费 (监督铁证) ==='
SELECT id, provider, model, tokens_in, tokens_out, cost_cents, latency_ms, status, created_at
  FROM ai_usage_log
  WHERE created_at > now() - interval '1 hour'
  ORDER BY id DESC LIMIT 5;
\echo '期望: provider=qianwen · model=qwen-vl-max · tokens_in > 0 · tokens_out > 0'
SELECT count(*) FILTER (WHERE tokens_out > 0) AS rows_with_real_tokens,
       sum(tokens_in) AS total_in,
       sum(tokens_out) AS total_out,
       sum(cost_cents) AS total_cost_cents
  FROM ai_usage_log
  WHERE created_at > now() - interval '1 hour';

-- ── Q4 · analytics_event (验 AnalyticsController 写库) ────────────
\echo '=== Q4 · analytics_event 漏斗事件 ==='
SELECT event_name, count(*) AS event_count, max(created_at) AS last_seen
  FROM analytics_event
  WHERE created_at > now() - interval '1 hour'
  GROUP BY event_name
  ORDER BY event_count DESC;
\echo '期望事件 (至少): anon_landing_view · anon_landing_cta_try · anon_guest_capture_view · anon_guest_capture_shoot · anon_guest_analyze_done'
SELECT event_name, device_fp, payload, created_at
  FROM analytics_event
  WHERE created_at > now() - interval '1 hour'
  ORDER BY id DESC LIMIT 15;

-- ── Q5 · wb_file (验 PresignController 真注册 + OSS callback) ────
\echo '=== Q5 · wb_file 上传记录 ==='
SELECT id, object_key, mime_type, bytes, status, created_at
  FROM wb_file
  WHERE created_at > now() - interval '1 hour'
  ORDER BY id DESC LIMIT 5;

-- ── Q6 · wb_question 详情 + planned_nodes (验 SC-01 P04 save) ────
\echo '=== Q6 · wb_question + wb_planned_node ==='
SELECT id, subject, stem, confidence, created_at FROM wb_question ORDER BY id DESC LIMIT 5;
SELECT question_id, t_level, due_at, status, created_at FROM wb_planned_node
  WHERE created_at > now() - interval '1 hour' ORDER BY id DESC LIMIT 6;
-- 注: 本次 SC-01 因无 auth-service · save 流程跳过 · wb_planned_node 可能为 0 · 这是 expected

-- ── Q7 · wrongbook_item (兼容老 schema · 看新旧字段) ─────────────
\echo '=== Q7 · wrongbook_item 主表 (老 schema · 留作兼容验证) ==='
SELECT count(*) AS wb_item_total FROM wrongbook_item;

-- ── 汇总 sweep ──────────────────────────────────────────────────────
\echo '=== 汇总 4 张核心表的 1 小时 row 数 ==='
SELECT 'guest_session'         AS tbl, count(*) AS rows_last_hour FROM guest_session         WHERE created_at > now() - interval '1 hour'
UNION ALL SELECT 'wrong_item_analysis', count(*) FROM wrong_item_analysis WHERE created_at > now() - interval '1 hour'
UNION ALL SELECT 'ai_usage_log',        count(*) FROM ai_usage_log        WHERE created_at > now() - interval '1 hour'
UNION ALL SELECT 'analytics_event',     count(*) FROM analytics_event     WHERE created_at > now() - interval '1 hour'
UNION ALL SELECT 'wb_file',             count(*) FROM wb_file             WHERE created_at > now() - interval '1 hour';

-- 期望最低 (按本次 SC-11 + SC-12 happy path):
--   guest_session: ≥ 1 (拍题创建)
--   wrong_item_analysis: ≥ 1 (analyze 触发)
--   ai_usage_log: ≥ 1 with tokens_out > 0 (真 LLM)
--   analytics_event: ≥ 5 (5 个核心漏斗事件)
--   wb_file: ≥ 1 (presign 创建 PENDING)
