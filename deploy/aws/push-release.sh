#!/usr/bin/env bash
# [nexus-deploy] Corre en el equipo de desarrollo. Publica una version del backend:
#   1. construye la imagen desde la raiz del backend y la etiqueta con el commit corto,
#   2. la sube a ECR (repo inmutable: un tag ya subido no se vuelve a subir),
#   3. empaqueta deploy/ (sin .env, respaldos ni .bat) y lo sube a s3://<bucket>/bundle/,
#   4. fija /nexus/prod/BACKEND_IMAGE con el tag nuevo.
# No toca la EC2: el despliegue lo hace bootstrap.sh por SSM Run Command.
# Uso: AWS_PROFILE=nexus-admin deploy/aws/push-release.sh
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$root"
export AWS_REGION="${AWS_REGION:-us-east-1}"
stack="${NEXUS_STACK:-nexus-ec2}"
prefix="${NEXUS_SSM_PREFIX:-/nexus/prod}"

if [ -n "$(git status --porcelain -- src pom.xml deploy)" ]; then
  echo "Hay cambios sin commitear en src/, pom.xml o deploy/: el tag no representaria lo que se sube." >&2
  exit 1
fi
tag="$(git rev-parse --short=12 HEAD)"

out() { aws cloudformation describe-stacks --stack-name "$stack" --query "Stacks[0].Outputs[?OutputKey=='$1'].OutputValue" --output text; }
repo="$(out BackendRepositoryUri)"
bucket="$(out BucketName)"
[ -n "$repo" ] && [ "$repo" != "None" ] || { echo "El stack $stack no tiene BackendRepositoryUri" >&2; exit 1; }

image="${repo}:${tag}"
echo "[1/4] build ${image}"
docker build -f deploy/Dockerfile -t "$image" .

echo "[2/4] push"
aws ecr get-login-password | docker login --username AWS --password-stdin "${repo%%/*}" >/dev/null
if aws ecr describe-images --repository-name "${repo##*/}" --image-ids imageTag="$tag" >/dev/null 2>&1; then
  echo "      ${tag} ya estaba en ECR"
else
  docker push --quiet "$image"
fi

echo "[3/4] bundle de deploy/"
bundle="$(mktemp -d)/deploy-${tag}.tgz"
tar -czf "$bundle" --exclude='deploy/.env' --exclude='deploy/.env.*' --exclude='deploy/respaldos' \
  --exclude='*.bat' deploy
aws s3 cp --only-show-errors "$bundle" "s3://${bucket}/bundle/deploy-${tag}.tgz"
aws s3 cp --only-show-errors "$bundle" "s3://${bucket}/bundle/deploy-latest.tgz"

echo "[4/4] ${prefix}/BACKEND_IMAGE"
aws ssm put-parameter --name "${prefix}/BACKEND_IMAGE" --type String --value "$image" --overwrite >/dev/null
echo "Version lista: ${tag} (s3://${bucket}/bundle/deploy-${tag}.tgz)"
