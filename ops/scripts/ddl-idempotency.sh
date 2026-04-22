#!/usr/bin/env bash
# 落地计划 §5.7 step 8 / §5.8 V-S1-05 · Flyway 双跑幂等
# 1) 起 pgvector/pgvector:pg16 容器
# 2) 首次 flyway:migrate
# 3) 再次 flyway:info 断言无 Pending/Failed
# 4) 拆容器
set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$REPO_ROOT"

IMAGE="pgvector/pgvector:pg16"
NAME="pg-wb-s1-idem"
PORT="${PG_PORT:-54321}"      # 非默认 5432 · 避免与本机 postgres 冲突

command -v docker >/dev/null 2>&1 || { echo "FAIL: docker not in PATH"; exit 1; }

cleanup() { docker rm -f "$NAME" >/dev/null 2>&1 || true; }
trap cleanup EXIT

docker rm -f "$NAME" >/dev/null 2>&1 || true
docker run -d --rm --name "$NAME" \
  -e POSTGRES_PASSWORD=wb \
  -e POSTGRES_DB=wrongbook \
  -p "${PORT}:5432" \
  "$IMAGE" >/dev/null

# Wait for ready
for i in $(seq 1 30); do
  if docker exec "$NAME" pg_isready -U postgres -d wrongbook >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

FLYWAY_URL="jdbc:postgresql://localhost:${PORT}/wrongbook"

# First migrate
(cd backend && mvn -pl common -q flyway:migrate \
  -Dflyway.url="$FLYWAY_URL" \
  -Dflyway.user=postgres \
  -Dflyway.password=wb \
  -Dflyway.locations=classpath:db/migration)

# Second info: must not show Pending/Failed
OUT=$(cd backend && mvn -pl common -q flyway:info \
  -Dflyway.url="$FLYWAY_URL" \
  -Dflyway.user=postgres \
  -Dflyway.password=wb \
  -Dflyway.locations=classpath:db/migration)

if echo "$OUT" | grep -qE "Pending|Failed"; then
  echo "FAIL: flyway not idempotent · info shows Pending/Failed"
  echo "$OUT" | head -40
  exit 1
fi

# Second migrate (must be no-op)
OUT2=$(cd backend && mvn -pl common -q flyway:migrate \
  -Dflyway.url="$FLYWAY_URL" \
  -Dflyway.user=postgres \
  -Dflyway.password=wb \
  -Dflyway.locations=classpath:db/migration)

if echo "$OUT2" | grep -qE "Migrating|Successfully applied"; then
  echo "FAIL: second migrate applied extra migrations"
  echo "$OUT2" | head -20
  exit 1
fi

echo "OK · flyway idempotent"
