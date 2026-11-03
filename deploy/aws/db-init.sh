#!/usr/bin/env bash
# [nexus-deploy] Carga unica de deploy/db/init.sql en la RDS (stack nexus-db). Corre en la EC2 (Run Command).
# - Usa un contenedor mysql:8.0 como cliente, con TLS obligatorio.
# - Quita los DEFINER=`root`@`localhost` de init.sql: en RDS no existe ese usuario ni el privilegio SUPER.
# - Crea o actualiza el usuario de la aplicacion (DB_USER) con permisos solo sobre la base nexus.
# - Si la base nexus ya tiene tablas, no carga nada (usar --force para recargar, y solo sobre una base vacia).
# Variables de .env: DB_HOST, DB_MASTER_USER, DB_MASTER_PASSWORD, DB_USER, DB_PASSWORD. No imprime secretos.
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
cd "$here"
force=false
[ "${1:-}" = "--force" ] && force=true

val() { grep -E "^$1=" .env | head -1 | cut -d= -f2-; }
host="$(val DB_HOST)"; muser="$(val DB_MASTER_USER)"; app_user="$(val DB_USER)"
[ -n "$host" ] && [ -n "$muser" ] && [ -n "$app_user" ] || { echo "Faltan DB_HOST, DB_MASTER_USER o DB_USER en .env" >&2; exit 1; }

# Las contrasenas viajan en un archivo de opciones del cliente (600), no en la linea de comandos.
work="$(mktemp -d)"
cleanup() { find "$work" -type f -exec shred -u {} + 2>/dev/null || true; rmdir "$work" 2>/dev/null || true; }
trap cleanup EXIT
chmod 700 "$work"
printf '[client]\nuser=%s\npassword=%s\nhost=%s\nssl-mode=REQUIRED\n' "$muser" "$(val DB_MASTER_PASSWORD)" "$host" > "$work/my.cnf"
chmod 600 "$work/my.cnf"   # el cliente del contenedor corre como root

sql() { docker run --rm -i -v "$work/my.cnf:/etc/nexus.cnf:ro" mysql:8.0 mysql --defaults-extra-file=/etc/nexus.cnf "$@"; }

tables="$(sql -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='nexus'")"
if [ "$tables" -gt 0 ] && ! $force; then
  echo "La base nexus ya tiene ${tables} tablas: no se carga init.sql (usa --force solo sobre una base vacia)."
else
  echo "Cargando init.sql (sin DEFINER)..."
  sed -E 's#/\*!50017 DEFINER=`[^`]+`@`[^`]+`\*/ ##g; s#DEFINER=`[^`]+`@`[^`]+` ##g' db/init.sql | sql
fi

app_pw="$(val DB_PASSWORD)"
case "$app_pw" in *"'"*|*"\\"*) echo "DB_PASSWORD no puede tener comillas simples ni barras invertidas" >&2; exit 1;; esac
sql <<SQL
CREATE USER IF NOT EXISTS '${app_user}'@'%' IDENTIFIED BY '${app_pw}' REQUIRE SSL;
ALTER USER '${app_user}'@'%' IDENTIFIED BY '${app_pw}' REQUIRE SSL;
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES, TRIGGER, CREATE VIEW, SHOW VIEW, LOCK TABLES, EXECUTE ON nexus.* TO '${app_user}'@'%';
FLUSH PRIVILEGES;
SQL

echo "Conteos:"
sql -t nexus -e "SELECT
  (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='nexus') AS tablas,
  (SELECT COUNT(*) FROM activities)  AS actividades,
  (SELECT COUNT(*) FROM places)      AS lugares,
  (SELECT COUNT(*) FROM preferences) AS preferencias,
  (SELECT COUNT(*) FROM users)       AS usuarios"
