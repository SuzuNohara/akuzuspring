#!/usr/bin/env bash
# [nexus-deploy] Respaldo logico de la base de Nexus (Linux). Equivalente de respaldar.bat.
# - Si .env trae DB_HOST (AWS, RDS): mysqldump desde un contenedor mysql:8.0 con TLS y el usuario maestro.
# - Si no (laptop): mysqldump dentro del contenedor mysql del compose.
# Uso: ./respaldar.sh          deja deploy/respaldos/nexus-AAAAMMDD-HHMM.sql (fuera de git)
#      ./respaldar.sh --s3     ademas lo sube a s3://$BACKUP_BUCKET/backups/
set -euo pipefail
cd "$(dirname "$0")"
to_s3=false
[ "${1:-}" = "--s3" ] && to_s3=true
val() { grep -E "^$1=" .env | head -1 | cut -d= -f2- || true; }

mkdir -p respaldos
fecha="$(date +%Y%m%d-%H%M)"
destino="respaldos/nexus-${fecha}.sql"
opts=(--single-transaction --routines --triggers --no-tablespaces --set-gtid-purged=OFF --databases nexus)

host="$(val DB_HOST)"
if [ -n "$host" ]; then
  work="$(mktemp -d)"
  trap 'find "$work" -type f -exec shred -u {} + 2>/dev/null || true; rmdir "$work" 2>/dev/null || true' EXIT
  chmod 700 "$work"
  printf '[client]\nuser=%s\npassword=%s\nhost=%s\nssl-mode=REQUIRED\n' "$(val DB_MASTER_USER)" "$(val DB_MASTER_PASSWORD)" "$host" > "$work/my.cnf"
  chmod 600 "$work/my.cnf"
  dump() { docker run --rm -i -v "$work/my.cnf:/etc/nexus.cnf:ro" mysql:8.0 mysqldump --defaults-extra-file=/etc/nexus.cnf "${opts[@]}"; }
else
  dump() { docker compose exec -T mysql sh -c "mysqldump -uroot -p\"\$MYSQL_ROOT_PASSWORD\" ${opts[*]}"; }
fi

if ! dump > "${destino}.tmp"; then
  echo "No se pudo respaldar." >&2
  exit 1
fi
mv "${destino}.tmp" "$destino"
chmod 600 "$destino"
echo "Respaldo listo: deploy/${destino}"

if $to_s3; then
  bucket="$(val BACKUP_BUCKET)"
  [ -n "$bucket" ] || { echo "Falta BACKUP_BUCKET en .env" >&2; exit 1; }
  gzip -k -f "$destino"
  aws s3 cp --only-show-errors "${destino}.gz" "s3://${bucket}/backups/nexus-${fecha}.sql.gz"
  echo "Subido: s3://${bucket}/backups/nexus-${fecha}.sql.gz"
fi
