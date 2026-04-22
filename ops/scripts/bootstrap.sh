#!/usr/bin/env bash
# S0 Phase bootstrap · idempotent replay of §4.7 steps
# 用法：bash ops/scripts/bootstrap.sh
# 本脚本不创建文件（那部分由 S0 手工一次性落地），仅重新跑构建与自检
set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$REPO_ROOT"

echo "=== S0 bootstrap · idempotent replay ==="

echo "[1/5] backend skeleton build"
(cd backend && mvn -q -T 1C -DskipTests package) || {
  echo "FAIL: backend build"; exit 1; }

echo "[2/5] frontend workspace install + build"
(cd frontend && pnpm install --frozen-lockfile 2>/dev/null || pnpm install) && \
(cd frontend && pnpm -r run build) || {
  echo "FAIL: frontend build"; exit 1; }

echo "[3/5] allowlist check (phase=s0)"
bash ops/scripts/check-allowlist.sh s0 || {
  echo "FAIL: allowlist"; exit 1; }

echo "[4/5] arch-consistency (phase=s0 · exempt)"
bash ops/scripts/check-arch-consistency.sh s0 || {
  echo "FAIL: arch-consistency"; exit 1; }

echo "[5/5] continuity check (phase=s0)"
bash ops/scripts/check-continuity.sh s0 || {
  echo "FAIL: continuity"; exit 1; }

echo ""
echo "=== S0 bootstrap OK ==="
