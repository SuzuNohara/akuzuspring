#!/usr/bin/env bash
# [nexus-deploy] Respaldo de la base de Nexus que corre en Docker (EC2 / Linux). Uso: ./respaldar.sh
# Deja deploy/respaldos/nexus-AAAAMMDD-HHMM.sql (fuera de git). Equivalente de respaldar.bat.
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p respaldos
fecha="$(date +%Y%m%d-%H%M)"
destino="respaldos/nexus-${fecha}.sql"
if ! docker compose exec -T mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers --no-tablespaces --set-gtid-purged=OFF --databases nexus' > "${destino}.tmp"; then
  echo "No se pudo respaldar." >&2
  exit 1
fi
mv "${destino}.tmp" "$destino"
chmod 600 "$destino"
echo "Respaldo listo: deploy/${destino}"
