#!/bin/sh
# ============================================================================
# Erezer Postgres backup — runs inside the `pg-backup` compose service.
#
#   * Dumps the configured DB to /var/backups/erezer-YYYYMMDD-HHMM.sql.gz
#   * Optionally uploads to an S3-compatible bucket (set BACKUP_S3_BUCKET)
#   * Prunes local dumps older than BACKUP_RETENTION_DAYS
#
# Env (provided by docker-compose):
#   PGHOST PGUSER PGPASSWORD PGDATABASE     — connection
#   BACKUP_RETENTION_DAYS                   — local prune window (default 14)
#   S3_BUCKET S3_ENDPOINT                   — optional off-host upload
#   AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY — credentials for the above
# ============================================================================

set -eu

OUT_DIR="/var/backups"
mkdir -p "$OUT_DIR"

stamp=$(date -u +%Y%m%d-%H%M)
file="${OUT_DIR}/erezer-${stamp}.sql.gz"

echo "[pg-backup] dumping ${PGDATABASE} → ${file}"
pg_dump --no-owner --no-privileges \
        --format=plain "${PGDATABASE}" \
  | gzip -9 > "${file}"

echo "[pg-backup] wrote $(du -h "${file}" | cut -f1)"

# ── Optional: upload to S3 ──────────────────────────────────────────────────
if [ -n "${S3_BUCKET:-}" ] && command -v aws >/dev/null 2>&1; then
    s3_args=""
    if [ -n "${S3_ENDPOINT:-}" ]; then
        s3_args="--endpoint-url ${S3_ENDPOINT}"
    fi
    key="db/$(basename "${file}")"
    echo "[pg-backup] uploading to s3://${S3_BUCKET}/${key}"
    aws s3 cp ${s3_args} "${file}" "s3://${S3_BUCKET}/${key}" --only-show-errors \
      || echo "[pg-backup] upload failed (continuing) — check IAM / endpoint."
fi

# ── Local retention prune ───────────────────────────────────────────────────
retention="${BACKUP_RETENTION_DAYS:-14}"
echo "[pg-backup] pruning local dumps older than ${retention} day(s)"
find "${OUT_DIR}" -name 'erezer-*.sql.gz' -mtime "+${retention}" -delete

echo "[pg-backup] done"
