#!/bin/sh
# Creates a dedicated database for Keycloak on first cluster init.
#
# Keycloak manages its own schema. Letting it share the application database
# means its ~90 tables sit alongside the app's, where they confuse dumps,
# restores and anyone reading the schema. A separate database costs nothing
# and keeps the two migration histories from ever meeting.
#
# Runs only when the data volume is empty (Postgres entrypoint convention),
# so it is a no-op on every subsequent boot.
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<EOSQL
    SELECT 'CREATE DATABASE ${KEYCLOAK_DB:-keycloak}'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '${KEYCLOAK_DB:-keycloak}')\gexec
    GRANT ALL PRIVILEGES ON DATABASE ${KEYCLOAK_DB:-keycloak} TO ${POSTGRES_USER};
EOSQL

echo "[erezer] keycloak database ready"
