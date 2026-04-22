#!/usr/bin/env bash
# 落地计划 §5.7 step 7 / §5.8 V-S1-02 · DDL 静态计数自检
# 用法：
#   PG_URL="postgresql://postgres:wb@localhost:5432/wrongbook" bash ops/scripts/ddl-count.sh
#
# 阈值（plan §5.3 最低下限 · 实际产出更多 · exit 0 = PASS）:
#   表   ≥ 18   (§5.6 实际 21 张)
#   索引 ≥ 23
#   CHECK ≥ 12
#   FK   ≥ 14
set -euo pipefail

PG_URL="${PG_URL:-postgresql://postgres:wb@localhost:5432/wrongbook}"

command -v psql >/dev/null 2>&1 || { echo "FAIL: psql not in PATH"; exit 1; }

TBL=$(psql "$PG_URL" -tAc "SELECT count(*) FROM pg_tables WHERE schemaname='public' AND tablename <> 'flyway_schema_history'")
IDX=$(psql "$PG_URL" -tAc "SELECT count(*) FROM pg_indexes WHERE schemaname='public'")
CHK=$(psql "$PG_URL" -tAc "SELECT count(*) FROM pg_constraint c JOIN pg_class cl ON c.conrelid=cl.oid JOIN pg_namespace n ON cl.relnamespace=n.oid WHERE c.contype='c' AND n.nspname='public'")
FK=$(psql "$PG_URL" -tAc "SELECT count(*) FROM pg_constraint c JOIN pg_class cl ON c.conrelid=cl.oid JOIN pg_namespace n ON cl.relnamespace=n.oid WHERE c.contype='f' AND n.nspname='public'")

echo "tables=$TBL indexes=$IDX checks=$CHK fks=$FK"

FAIL=0
[[ "$TBL" -ge 18 ]] || { echo "FAIL: tables $TBL < 18"; FAIL=1; }
[[ "$IDX" -ge 23 ]] || { echo "FAIL: indexes $IDX < 23"; FAIL=1; }
[[ "$CHK" -ge 12 ]] || { echo "FAIL: checks $CHK < 12"; FAIL=1; }
[[ "$FK"  -ge 14 ]] || { echo "FAIL: fks $FK < 14"; FAIL=1; }

exit "$FAIL"
