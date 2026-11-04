# [nexus-deploy] Funciones comunes de nexus-ops.sh y nexus-monitor.sh. Se carga con `source`, no se ejecuta.
# Corre en el equipo del dev (o en sunsetcli) con un perfil del AWS CLI con permisos de admin.

export AWS_REGION="${AWS_REGION:-us-east-1}"
EC2_STACK="${NEXUS_EC2_STACK:-nexus-ec2}"
DB_STACK="${NEXUS_DB_STACK:-nexus-db}"
SSM_PREFIX="${NEXUS_SSM_PREFIX:-/nexus/prod}"
# Estado de las rutinas (modo, snapshots de F2). Fuera de /nexus/prod para que render-env.sh no lo meta al .env.
STATE_PREFIX="${NEXUS_STATE_PREFIX:-/nexus/state}"
LOG_PREFIX="${NEXUS_LOG_PREFIX:-/nexus/prod}"
DB_ID="${NEXUS_DB_ID:-nexus-prod-mysql}"
YES="${YES:-0}"

log() { printf '[nexus %s] %s\n' "$(date +%H:%M:%S)" "$*" >&2; }
die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

# El AWS CLI de snap no escribe a stdout si no es una tuberia; con pipefail se conserva su codigo de salida.
awsc() { aws "$@" | cat; }

# Texto de salida sin "None".
clean() { local v; v="$(cat)"; [ "$v" = "None" ] && v=""; printf '%s' "$v"; }

stack_status() {
  awsc cloudformation describe-stacks --stack-name "$1" --query 'Stacks[0].StackStatus' --output text 2>/dev/null || echo NONE
}
stack_out() {
  awsc cloudformation describe-stacks --stack-name "$1" \
    --query "Stacks[0].Outputs[?OutputKey=='$2'].OutputValue | [0]" --output text 2>/dev/null | clean || true
}
stack_param() {
  awsc cloudformation describe-stacks --stack-name "$1" \
    --query "Stacks[0].Parameters[?ParameterKey=='$2'].ParameterValue | [0]" --output text 2>/dev/null | clean || true
}

instance_id() { stack_out "$EC2_STACK" InstanceId; }
instance_state() {
  awsc ec2 describe-instances --instance-ids "$1" --query 'Reservations[0].Instances[0].State.Name' --output text
}
root_volume() {
  awsc ec2 describe-instances --instance-ids "$1" \
    --query "Reservations[0].Instances[0].BlockDeviceMappings[?DeviceName=='/dev/sda1'].Ebs.VolumeId | [0]" --output text | clean || true
}
db_status() {
  awsc rds describe-db-instances --db-instance-identifier "$DB_ID" \
    --query 'DBInstances[0].DBInstanceStatus' --output text 2>/dev/null || echo none
}

ssm_get() {
  awsc ssm get-parameter --name "$1" --with-decryption --query Parameter.Value --output text 2>/dev/null | clean || true
}
ssm_put() { awsc ssm put-parameter --name "$1" --type String --value "$2" --overwrite >/dev/null; }
state_get() { ssm_get "$STATE_PREFIX/$1"; }
state_put() { ssm_put "$STATE_PREFIX/$1" "$2"; }

# confirm "<frase>": pide escribir la frase exacta, salvo con --yes.
confirm() {
  [ "$YES" = 1 ] && return 0
  local r
  read -rp "Escribe '$1' para continuar: " r
  [ "$r" = "$1" ] || die "Cancelado."
}

wait_db() {  # wait_db <estado> [intentos de 20 s]
  local want="$1" n="${2:-120}" s
  for _ in $(seq 1 "$n"); do
    s="$(db_status)"
    [ "$s" = "$want" ] && return 0
    sleep 20
  done
  die "La RDS no llego a '$want' (esta en '$s')."
}

wait_ssm_online() {
  local s
  for _ in $(seq 1 60); do
    s="$(awsc ssm describe-instance-information --filters "Key=InstanceIds,Values=$1" \
      --query 'InstanceInformationList[0].PingStatus' --output text | clean)"
    [ "$s" = "Online" ] && return 0
    sleep 10
  done
  die "La instancia $1 no aparece en SSM."
}

wait_snapshot() {  # el waiter del CLI se rinde a los 10 min; un disco de 100 GB puede tardar mas
  local s p
  for _ in $(seq 1 180); do
    read -r s p < <(awsc ec2 describe-snapshots --snapshot-ids "$1" --query 'Snapshots[0].[State,Progress]' --output text)
    [ "$s" = completed ] && return 0
    [ "$s" = error ] && die "El snapshot $1 fallo."
    log "    snapshot $1: $p"
    sleep 20
  done
  die "El snapshot $1 no termino en 1 h."
}

# remote <instancia> "<comando sh>" [timeout s]: lo corre en /opt/nexus/deploy por SSM Run Command.
# AWS-RunShellScript usa sh (sin pipefail). La salida completa queda en s3://<bucket>/backups/ssm-logs/<id>/.
remote() {
  local iid="$1" cmd="$2" timeout="${3:-1800}" bucket params id st
  bucket="$(stack_out "$EC2_STACK" BucketName)"
  params="$(python3 -I -c 'import json,sys; print(json.dumps({"commands":["set -eu","export AWS_REGION="+sys.argv[3],"cd /opt/nexus/deploy",sys.argv[1]],"executionTimeout":[sys.argv[2]]}))' "$cmd" "$timeout" "$AWS_REGION")"
  id="$(awsc ssm send-command --instance-ids "$iid" --document-name AWS-RunShellScript --comment "nexus-ops" \
    --timeout-seconds 600 --output-s3-bucket-name "$bucket" --output-s3-key-prefix backups/ssm-logs \
    --parameters "$params" --query Command.CommandId --output text)"
  log "    SSM $id: $cmd"
  for _ in $(seq 1 $(( timeout / 10 + 30 ))); do
    st="$(awsc ssm get-command-invocation --command-id "$id" --instance-id "$iid" --query Status --output text 2>/dev/null || echo Pending)"
    case "$st" in Success|Failed|Cancelled|TimedOut) break ;; esac
    sleep 10
  done
  awsc ssm get-command-invocation --command-id "$id" --instance-id "$iid" --query StandardOutputContent --output text \
    | grep -vE 'Downloading|Extracting|Waiting|Verifying|Pull complete|Download complete|Already exists' | tail -60 || true
  if [ "$st" != Success ]; then
    awsc ssm get-command-invocation --command-id "$id" --instance-id "$iid" --query StandardErrorContent --output text | tail -20 >&2 || true
    die "El comando remoto termino en '$st'. Log completo: s3://$bucket/backups/ssm-logs/$id/"
  fi
}

public_url() { ssm_get "$SSM_PREFIX/PUBLIC_URL"; }
health() {  # health [intentos de 10 s]
  local url n="${1:-30}"
  url="$(public_url)"
  for _ in $(seq 1 "$n"); do
    if curl -fsS -m 10 -o /dev/null "$url/api/auth/health"; then
      log "    salud OK: $url/api/auth/health"
      return 0
    fi
    sleep 10
  done
  die "El backend no responde en $url/api/auth/health"
}

# stack_update <stack> CLAVE=valor...: misma plantilla, el resto de parametros sin cambio.
# Valores sin comas (las listas como SubnetIds no se cambian por aqui).
stack_update() {
  local stack="$1" keys k kv over args=()
  shift
  over=" $* "
  keys="$(awsc cloudformation describe-stacks --stack-name "$stack" --query 'Stacks[0].Parameters[].ParameterKey' --output text)"
  for k in $keys; do
    [[ "$over" == *" $k="* ]] || args+=("ParameterKey=$k,UsePreviousValue=true")
  done
  for kv in "$@"; do args+=("ParameterKey=${kv%%=*},ParameterValue=${kv#*=}"); done
  awsc cloudformation update-stack --stack-name "$stack" --use-previous-template --capabilities CAPABILITY_IAM \
    --parameters "${args[@]}" >/dev/null
  awsc cloudformation wait stack-update-complete --stack-name "$stack" \
    || die "El update de $stack fallo: aws cloudformation describe-stack-events --stack-name $stack"
}

# --desde 15m|1h|6h|1d|7d -> segundos
to_seconds() {
  case "$1" in
    *m) echo $(( ${1%m} * 60 )) ;;
    *h) echo $(( ${1%h} * 3600 )) ;;
    *d) echo $(( ${1%d} * 86400 )) ;;
    *) die "Formato de --desde no valido: $1 (usa 15m, 1h, 1d...)" ;;
  esac
}
