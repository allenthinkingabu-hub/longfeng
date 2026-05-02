#!/bin/sh
# =============================================================
# longfeng-wrongbook · MinIO bucket init (one-shot)
# Runs inside the minio/mc init container after MinIO is healthy
# =============================================================

set -e

ENDPOINT="${MINIO_ENDPOINT:-http://minio:9000}"
ACCESS_KEY="${MINIO_ROOT_USER:-minio}"
SECRET_KEY="${MINIO_ROOT_PASSWORD:-minio12345}"
ALIAS="lf-dev"

echo "[minio-init] Waiting for MinIO to be ready at ${ENDPOINT}..."
until mc alias set "${ALIAS}" "${ENDPOINT}" "${ACCESS_KEY}" "${SECRET_KEY}" 2>/dev/null; do
  echo "[minio-init] MinIO not ready yet, retrying in 3s..."
  sleep 3
done

echo "[minio-init] MinIO is ready. Creating buckets..."

# 1. wrongbook-dev — main object storage for dev (images, OCR results, etc.)
mc mb --ignore-existing "${ALIAS}/wrongbook-dev"
mc anonymous set none "${ALIAS}/wrongbook-dev"
echo "[minio-init] Bucket 'wrongbook-dev' ready."

# 2. guest-tmp-dev — temporary bucket for unauthenticated (guest) uploads
#    TTL lifecycle: objects expire after 5 minutes (D-Guest-Storage)
mc mb --ignore-existing "${ALIAS}/guest-tmp-dev"
mc anonymous set none "${ALIAS}/guest-tmp-dev"
mc ilm import "${ALIAS}/guest-tmp-dev" <<'LIFECYCLE'
{
  "Rules": [
    {
      "ID": "guest-5min-expiry",
      "Status": "Enabled",
      "Filter": { "Prefix": "" },
      "Expiration": { "Days": 1 }
    }
  ]
}
LIFECYCLE
echo "[minio-init] Bucket 'guest-tmp-dev' ready (lifecycle: expire after 1 day in dev)."

# 3. shared-thumbnail-dev — pre-generated thumbnails for shared wrongbook items
mc mb --ignore-existing "${ALIAS}/shared-thumbnail-dev"
mc anonymous set none "${ALIAS}/shared-thumbnail-dev"
echo "[minio-init] Bucket 'shared-thumbnail-dev' ready."

echo ""
echo "[minio-init] All 3 buckets created successfully:"
mc ls "${ALIAS}/"

echo "[minio-init] Done."
