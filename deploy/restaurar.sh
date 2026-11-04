#!/usr/bin/env bash
# [nexus-deploy] Restaura un respaldo en la base de Docker. Uso: ./restaurar.sh respaldos/nexus-AAAAMMDD-HHMM.sql
# Reemplaza los datos actuales de nexus por los del respaldo. Equivalente de restaurar.bat.
set -euo pipefail
cd "$(dirname "$0")"
archivo="${1:-}"
[ -n "$archivo" ] || { echo "Uso: ./restaurar.sh respaldos/archivo.sql" >&2; exit 1; }
[ -f "$archivo" ] || { echo "No existe $archivo" >&2; exit 1; }
read -r -p "Esto reemplaza los datos de Nexus por los de $archivo. Escribe SI para seguir: " ok
[ "${ok^^}" = "SI" ] || { echo "Cancelado."; exit 1; }
docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD"' < "$archivo"
echo "Restaurado: $archivo"
