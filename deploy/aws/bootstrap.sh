#!/usr/bin/env bash
# [nexus-deploy] Levanta o actualiza NEXUS en la EC2. Idempotente. Lo lanza SSM Run Command como root
# despues de desempacar el bundle en /opt/nexus (ver deploy/aws/README.md).
# Pasos: GPU visible en Docker -> .env desde SSM -> login ECR -> DNS -> compose up -> salud -> precalentado.
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
cd "$here"
export AWS_REGION="${AWS_REGION:-us-east-1}"
compose=(docker compose -f docker-compose.yml -f docker-compose.gpu.yml -f docker-compose.aws.yml)

log() { printf '[bootstrap %s] %s\n' "$(date -u +%H:%M:%S)" "$*"; }

# Las llamadas internas van por la red de compose con un contenedor de curl: la imagen del backend no
# trae curl, y con un dominio en SITE_ADDRESS Caddy no responde a Host: localhost.
net=""
incurl() { docker run --rm --network "$net" curlimages/curl:8.10.1 -fsS -o /dev/null "$@"; }
health() { incurl --max-time 5 http://backend:8080/api/auth/health; }

log "1/7 GPU y Docker"
command -v docker >/dev/null || { echo "Docker no esta instalado (la AMI deberia traerlo)" >&2; exit 1; }
command -v aws >/dev/null || { echo "AWS CLI no esta instalado" >&2; exit 1; }
nvidia-smi --query-gpu=name,memory.total --format=csv,noheader
if ! docker info --format '{{json .Runtimes}}' | grep -q nvidia; then
  nvidia-ctk runtime configure --runtime=docker
  systemctl restart docker
fi
systemctl enable --now docker >/dev/null

log "2/7 .env desde SSM"
"$here/aws/render-env.sh"

log "3/7 login en ECR"
image="$(grep -E '^BACKEND_IMAGE=' .env | cut -d= -f2-)"
registry="${image%%/*}"
aws ecr get-login-password | docker login --username AWS --password-stdin "$registry" >/dev/null

log "4/7 DNS"
"$here/aws/duckdns-update.sh"

log "5/7 compose up"
"${compose[@]}" pull --quiet backend
"${compose[@]}" up -d --remove-orphans
net="$(docker inspect --format '{{range $k, $v := .NetworkSettings.Networks}}{{$k}}{{end}}' "$("${compose[@]}" ps -q ollama)")"

log "6/7 esperando la salud del backend (la primera vez bajan ~5.6 GB de modelos)"
for _ in $(seq 1 90); do
  health && break
  sleep 10
done
health || { echo "El backend no respondio" >&2; "${compose[@]}" ps; exit 1; }

log "7/7 precalentado de los modelos en la GPU"
chat="$(grep -E '^NEXUS_AGENT_CHAT_MODEL=' .env | cut -d= -f2- || true)"
chat="${chat:-mistral:7b-instruct-v0.3-q4_K_M}"
embed="$(grep -E '^NEXUS_AGENT_EMBED_MODEL=' .env | cut -d= -f2- || true)"
embed="${embed:-bge-m3}"
# num_ctx igual al del backend (nexus.agent.num-ctx=8192): Ollama recarga el modelo si cambia.
incurl http://ollama:11434/api/generate \
  -d "{\"model\":\"$chat\",\"prompt\":\"ok\",\"stream\":false,\"keep_alive\":-1,\"options\":{\"num_ctx\":8192,\"num_predict\":1}}"
incurl http://ollama:11434/api/embed -d "{\"model\":\"$embed\",\"input\":\"ok\",\"keep_alive\":-1}"
"${compose[@]}" exec -T ollama ollama ps
log "listo"
