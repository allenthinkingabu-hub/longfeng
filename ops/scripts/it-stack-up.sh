#!/usr/bin/env bash
# S3 IT stack · brings up pgvector/pg16 + redis 7 on fixed ports 15432 / 16379.
# Kept in ops/scripts so CI (GitHub Actions) can reuse. Rationale documented in
# WrongbookIntegrationTestBase.java (Testcontainers × Docker Desktop 4.64+ compat gap).
set -euo pipefail

PG_NAME="${PG_NAME:-s3-it-pg}"
REDIS_NAME="${REDIS_NAME:-s3-it-redis}"
PG_PORT="${PG_PORT:-15432}"
REDIS_PORT="${REDIS_PORT:-16379}"

start_pg() {
  if docker ps --filter "name=${PG_NAME}" --format '{{.Names}}' | grep -qx "$PG_NAME"; then
    echo "[it-stack] $PG_NAME already running"; return 0
  fi
  docker rm -f "$PG_NAME" >/dev/null 2>&1 || true
  docker run -d --name "$PG_NAME" -p "${PG_PORT}:5432" \
    -e POSTGRES_DB=wrongbook -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=wb \
    pgvector/pgvector:pg16 >/dev/null
  echo "[it-stack] started $PG_NAME on :$PG_PORT"
}

start_redis() {
  if docker ps --filter "name=${REDIS_NAME}" --format '{{.Names}}' | grep -qx "$REDIS_NAME"; then
    echo "[it-stack] $REDIS_NAME already running"; return 0
  fi
  docker rm -f "$REDIS_NAME" >/dev/null 2>&1 || true
  docker run -d --name "$REDIS_NAME" -p "${REDIS_PORT}:6379" redis:7.2-alpine >/dev/null
  echo "[it-stack] started $REDIS_NAME on :$REDIS_PORT"
}

wait_pg() {
  for _ in $(seq 1 30); do
    if docker exec "$PG_NAME" pg_isready -U postgres >/dev/null 2>&1; then
      docker exec "$PG_NAME" psql -U postgres -d wrongbook -c "CREATE EXTENSION IF NOT EXISTS vector;" >/dev/null
      echo "[it-stack] $PG_NAME ready + vector extension"
      return 0
    fi
    sleep 1
  done
  echo "[it-stack] $PG_NAME failed to become ready" >&2
  exit 1
}

start_pg
start_redis
wait_pg
