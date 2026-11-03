#!/usr/bin/env bash
# [nexus-deploy] Escribe deploy/.env con los parametros de SSM /nexus/prod/* (un parametro por variable;
# el ultimo segmento del nombre es el nombre de la variable). Corre en la EC2 con el rol de la instancia.
# No imprime ningun valor.
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
prefix="${NEXUS_SSM_PREFIX:-/nexus/prod}"
region="${AWS_REGION:-us-east-1}"
tmp="$(mktemp "$here/.env.XXXXXX")"
cleanup() { [ -f "$tmp" ] && shred -u "$tmp" 2>/dev/null || true; }
trap cleanup EXIT
chmod 600 "$tmp"

aws ssm get-parameters-by-path --region "$region" --path "$prefix" --with-decryption --recursive \
  --query 'Parameters[].[Name,Value]' --output json \
  | python3 -c '
import json, sys
rows = json.load(sys.stdin)
for name, value in sorted(rows):
    key = name.rsplit("/", 1)[-1]
    if "\n" in value:
        sys.exit(f"{key}: el valor tiene saltos de linea")
    print(f"{key}={value}")
' > "$tmp"

required=(DB_HOST DB_MASTER_USER DB_MASTER_PASSWORD DB_USER DB_PASSWORD JWT_SECRET AES_SECRET MAIL_USERNAME MAIL_PASSWORD BACKEND_IMAGE SITE_ADDRESS PUBLIC_URL BACKUP_BUCKET)
missing=()
for k in "${required[@]}"; do
  grep -q "^${k}=" "$tmp" || missing+=("$k")
done
if [ "${#missing[@]}" -gt 0 ]; then
  echo "Faltan en SSM ${prefix}: ${missing[*]}" >&2
  exit 1
fi
# En AWS la base es RDS y el servicio mysql no arranca (perfil local-db), pero docker-compose.yml exige la
# variable al interpolar el archivo completo.
grep -q '^MYSQL_ROOT_PASSWORD=' "$tmp" || echo 'MYSQL_ROOT_PASSWORD=sin-uso-en-aws' >> "$tmp"

mv "$tmp" "$here/.env"
echo "deploy/.env escrito con $(wc -l < "$here/.env") variables"
