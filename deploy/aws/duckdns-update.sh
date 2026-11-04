#!/usr/bin/env bash
# [nexus-deploy] Apunta el subdominio de DuckDNS a la IP publica de esta EC2. Solo actua si .env trae
# DUCKDNS_DOMAIN (sin ".duckdns.org") y DUCKDNS_TOKEN; si no, no hace nada (dominio en tramite u otro DNS).
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
domain="$(grep -E '^DUCKDNS_DOMAIN=' "$here/.env" | cut -d= -f2- || true)"
token="$(grep -E '^DUCKDNS_TOKEN=' "$here/.env" | cut -d= -f2- || true)"
if [ -z "$domain" ] || [ -z "$token" ]; then
  echo "DuckDNS: sin DUCKDNS_DOMAIN o DUCKDNS_TOKEN, se omite"
  exit 0
fi
imds="http://169.254.169.254/latest"
t="$(curl -fsS -X PUT "$imds/api/token" -H 'X-aws-ec2-metadata-token-ttl-seconds: 60')"
ip="$(curl -fsS -H "X-aws-ec2-metadata-token: $t" "$imds/meta-data/public-ipv4")"
answer="$(curl -fsS "https://www.duckdns.org/update?domains=${domain}&token=${token}&ip=${ip}")"
if [ "$answer" != "OK" ]; then
  echo "DuckDNS respondio '$answer'" >&2
  exit 1
fi
echo "DuckDNS: ${domain}.duckdns.org -> ${ip}"
