#!/usr/bin/env bash
# [nexus-deploy] Respaldo de la base de Nexus que corre en Docker (EC2 / Linux). Equivalente de respaldar.bat.
# Uso: ./respaldar.sh          deja deploy/respaldos/nexus-AAAAMMDD-HHMM.sql (fuera de git)
#      ./respaldar.sh --s3     ademas lo sube a s3://$BACKUP_BUCKET/backups/ (BACKUP_BUCKET sale de .env)
set -euo pipefail
cd "$(dirname "$0")"
to_s3=false
[ "${1:-}" = "--s3" ] && to_s3=true

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

if $to_s3; then
  bucket="$(grep -E '^BACKUP_BUCKET=' .env | cut -d= -f2- || true)"
  [ -n "$bucket" ] || { echo "Falta BACKUP_BUCKET en .env" >&2; exit 1; }
  gzip -k -f "$destino"
  aws s3 cp --only-show-errors "${destino}.gz" "s3://${bucket}/backups/nexus-${fecha}.sql.gz"
  echo "Subido: s3://${bucket}/backups/nexus-${fecha}.sql.gz"
fi
