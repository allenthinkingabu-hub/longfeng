#!/usr/bin/env bash
set -e
{
  echo "=== JDK ===";        java -version 2>&1
  echo "=== Maven ===";      mvn -v
  echo "=== Docker ===";     docker --version
  echo "=== Compose ===";    docker compose version
  echo "=== kubectl ===";    kubectl version --client=true --output=yaml || true
  echo "=== Git ===";        git --version
} | tee /Users/allenwang/build/longfeng/design/calendar-platform/env-check.log
