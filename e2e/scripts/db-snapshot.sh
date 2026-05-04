#!/usr/bin/env bash
# DB 穿透验证 · 跑完 hybrid E2E 后立即跑 · 落证据到 export/login-trace/
#
# 用法：bash e2e/scripts/db-snapshot.sh [输出目录]
# 默认输出：export/login-trace/db-state.txt + redis-state.txt
set -euo pipefail

OUT_DIR="${1:-export/login-trace}"
mkdir -p "$OUT_DIR"

PG_HOST="${PG_HOST:-localhost}"
PG_PORT="${PG_PORT:-15432}"
PG_USER="${PG_USER:-postgres}"
PG_DB="${PG_DB:-longfeng_dev}"
# s3-it-pg container password (verified via docker inspect)
PG_PASSWORD="${PG_PASSWORD:-wb}"

REDIS_HOST="${REDIS_HOST:-localhost}"
REDIS_PORT="${REDIS_PORT:-16379}"

DB_SNAPSHOT="$OUT_DIR/05-db-state.txt"
REDIS_SNAPSHOT="$OUT_DIR/06-redis-state.txt"

echo "═══════════════════════════════════════════════"
echo "  DB 穿透验证 · $(date -u +'%Y-%m-%dT%H:%M:%SZ')"
echo "═══════════════════════════════════════════════"

# ── PG 查询 ──────────────────────────────────────
echo "[PG] $PG_HOST:$PG_PORT · DB=$PG_DB"
{
  echo "═══════════════════════════════════════════════"
  echo "  DB SNAPSHOT · $(date -u +'%Y-%m-%dT%H:%M:%SZ')"
  echo "  $PG_HOST:$PG_PORT/$PG_DB"
  echo "═══════════════════════════════════════════════"
  echo
  echo "─── user_account 最新 5 条 ─────────────────"
  PGPASSWORD="$PG_PASSWORD" psql -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -d "$PG_DB" -P pager=off -c "
    SELECT id, username, wechat_openid, role, status, created_at
      FROM lfwb.user_account
      ORDER BY created_at DESC NULLS LAST LIMIT 5;
  " 2>&1 || echo "[ERR] user_account 查询失败 (schema 可能是 public · 重试) " && \
  PGPASSWORD="$PG_PASSWORD" psql -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -d "$PG_DB" -P pager=off -c "
    SELECT id, username, wechat_openid, role, status, created_at
      FROM user_account
      ORDER BY created_at DESC NULLS LAST LIMIT 5;
  " 2>&1 || true

  echo
  echo "─── user_token 最新 5 条 ───────────────────"
  PGPASSWORD="$PG_PASSWORD" psql -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -d "$PG_DB" -P pager=off -c "
    SELECT user_id, LEFT(token_hash, 16) AS token_prefix, device_fp_hash, issued_at, expires_at
      FROM lfwb.user_token
      ORDER BY issued_at DESC LIMIT 5;
  " 2>&1 || \
  PGPASSWORD="$PG_PASSWORD" psql -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -d "$PG_DB" -P pager=off -c "
    SELECT user_id, LEFT(token_hash, 16) AS token_prefix, device_fp_hash, issued_at, expires_at
      FROM user_token
      ORDER BY issued_at DESC LIMIT 5;
  " 2>&1 || true

  echo
  echo "─── 表是否存在 ──────────────────────────────"
  PGPASSWORD="$PG_PASSWORD" psql -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -d "$PG_DB" -P pager=off -c "
    SELECT table_schema, table_name
      FROM information_schema.tables
      WHERE table_name IN ('user_account', 'user_token', 'guest_session')
      ORDER BY table_schema, table_name;
  " 2>&1 || true

  echo
  echo "─── flyway_schema_history 最近 5 条 ─────────"
  PGPASSWORD="$PG_PASSWORD" psql -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -d "$PG_DB" -P pager=off -c "
    SELECT version, description, success, installed_on
      FROM lfwb.flyway_schema_history
      ORDER BY installed_rank DESC LIMIT 5;
  " 2>&1 || \
  PGPASSWORD="$PG_PASSWORD" psql -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -d "$PG_DB" -P pager=off -c "
    SELECT version, description, success, installed_on
      FROM flyway_schema_history
      ORDER BY installed_rank DESC LIMIT 5;
  " 2>&1 || true
} > "$DB_SNAPSHOT" 2>&1

echo "[OK] PG snapshot → $DB_SNAPSHOT"

# ── Redis 查询 ───────────────────────────────────
echo "[Redis] $REDIS_HOST:$REDIS_PORT"
{
  echo "═══════════════════════════════════════════════"
  echo "  REDIS SNAPSHOT · $(date -u +'%Y-%m-%dT%H:%M:%SZ')"
  echo "  $REDIS_HOST:$REDIS_PORT"
  echo "═══════════════════════════════════════════════"
  echo
  echo "─── auth:* keys ─────────────────────────────"
  redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" --scan --pattern "auth:*" 2>&1 | head -20 || echo "[INFO] auth:* keys 不存在 (BE 可能不用 Redis 缓存 token)"
  echo
  echo "─── auth:token:* keys ───────────────────────"
  redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" --scan --pattern "auth:token:*" 2>&1 | head -10 || true
  echo
  echo "─── 全部 keys 总数 ──────────────────────────"
  redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" DBSIZE 2>&1 || true
} > "$REDIS_SNAPSHOT" 2>&1

echo "[OK] Redis snapshot → $REDIS_SNAPSHOT"
echo
echo "═══════════════════════════════════════════════"
echo "  DB 穿透完成 · 证据写入 $OUT_DIR/"
echo "═══════════════════════════════════════════════"
