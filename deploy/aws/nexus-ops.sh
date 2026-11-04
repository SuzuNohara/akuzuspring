#!/usr/bin/env bash
# [nexus-deploy] Rutinas de operacion de NEXUS en AWS. Encendido y apagado manuales.
#
# Uso: AWS_PROFILE=nexus-admin deploy/aws/nexus-ops.sh <comando> [--yes]
#
#   estado     modo, EC2, RDS, salud, respaldos, snapshots y costo aproximado
#   apagar     F1: respaldo a S3 -> stop EC2 -> stop RDS                         (~19 USD/mes)
#              AWS vuelve a encender la RDS sola a los 7 dias: para pausas largas usa congelar.
#   encender   sale de F1: start RDS -> start EC2 -> bootstrap -> salud          (5-10 min)
#   congelar   F2: respaldo -> snapshot del disco -> stack sin instancia ni IP -> borra el disco suelto
#              -> borra nexus-db con snapshot final                                (~7 USD/mes)
#   restaurar  sale de F2: RDS desde su snapshot -> instancia desde el snapshot del disco -> bootstrap
#              (25-40 min; la IP cambia)
#   desplegar  push-release.sh con el commit actual -> bundle + bootstrap en la EC2 -> salud
#   destruir   respaldo -> borra nexus-db (snapshot final) y nexus-ec2 -> borra el disco suelto.
#              Quedan bucket, ECR, snapshots y parametros SSM; se listan al final.
#
# --yes omite la confirmacion (para sunsetcli). Los comandos piden escribir una frase antes de tocar nada.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=ops-lib.sh
source "$here/ops-lib.sh"

cmd="${1:-}"
[ "${2:-}" = "--yes" ] && YES=1

respaldo_si_se_puede() {  # respaldo logico a S3 si la EC2 y la RDS estan arriba
  local iid="$1"
  if [ "$(instance_state "$iid")" = running ] && [ "$(db_status)" = available ]; then
    log "respaldo de la base a S3"
    remote "$iid" "./respaldar.sh --s3" 900
  else
    log "se omite el respaldo logico (EC2 o RDS apagada); la RDS tiene sus respaldos automaticos"
  fi
}

cmd_estado() {
  local modo iid st="sin instancia" ip="" tipo="" db url bucket
  modo="$(state_get modo)"; modo="${modo:-activo}"
  iid="$(instance_id)"
  if [ -n "$iid" ]; then
    read -r st tipo < <(awsc ec2 describe-instances --instance-ids "$iid" \
      --query 'Reservations[0].Instances[0].[State.Name,InstanceType]' --output text)
    ip="$(stack_out "$EC2_STACK" PublicIp)"
  fi
  db="$(db_status)"
  url="$(public_url)"
  bucket="$(stack_out "$EC2_STACK" BucketName)"
  echo "Modo            ${modo} (desde $(state_get desde))"
  echo "EC2             ${iid:-—} ${tipo} ${st} ${ip}"
  echo "RDS             ${DB_ID} ${db}"
  echo "URL             ${url}"
  if [ "$st" = running ]; then
    if curl -fsS -m 10 -o /dev/null "$url/api/auth/health"; then echo "Salud           OK"; else echo "Salud           SIN RESPUESTA"; fi
  fi
  echo "Snapshot F2     disco $(state_get root_snapshot) · RDS $(state_get db_snapshot)"
  echo
  echo "Ultimos respaldos en S3:"
  { [ -n "$bucket" ] && awsc s3 ls "s3://$bucket/backups/" | grep -v ssm-logs | tail -3; } || echo "   (ninguno)"
  echo
  echo "Snapshots de EBS (project=nexus):"
  awsc ec2 describe-snapshots --owner-ids self --filters Name=tag:project,Values=nexus \
    --query 'Snapshots[].[StartTime,SnapshotId,VolumeSize,State,Tags[?Key==`nexus-snapshot`].Value|[0]]' --output text | sort | tail -10 || true
  echo
  echo "Snapshots manuales de RDS:"
  awsc rds describe-db-snapshots --snapshot-type manual \
    --query 'DBSnapshots[].[SnapshotCreateTime,DBSnapshotIdentifier,AllocatedStorage,Status]' --output text | sort || true
  echo
  case "$st" in
    running) echo "Costo: ~0,54 USD/h encendida (EC2 g4dn 0,526 + RDS 0,016) + ~19 USD/mes fijo" ;;
    stopped) echo "Costo: F1 ~19 USD/mes (discos, IP, snapshots). Si la RDS se reencendio sola, +0,016 USD/h" ;;
    *) echo "Costo: F2/destruido ~2-7 USD/mes (snapshots, ECR, S3)" ;;
  esac
}

cmd_apagar() {
  local iid
  iid="$(instance_id)"
  [ -n "$iid" ] || die "No hay instancia (esta en F2 o destruida). Usa: restaurar"
  confirm apagar
  respaldo_si_se_puede "$iid"
  if [ "$(instance_state "$iid")" != stopped ]; then
    log "deteniendo la EC2 $iid"
    awsc ec2 stop-instances --instance-ids "$iid" >/dev/null
    awsc ec2 wait instance-stopped --instance-ids "$iid"
  fi
  case "$(db_status)" in
    available)
      log "deteniendo la RDS $DB_ID"
      awsc rds stop-db-instance --db-instance-identifier "$DB_ID" >/dev/null
      wait_db stopped ;;
    stopped) ;;
    *) log "la RDS esta en '$(db_status)'; reintenta apagar en unos minutos para detenerla" ;;
  esac
  state_put modo F1
  state_put desde "$(date -u +%FT%TZ)"
  log "F1 listo. La RDS se vuelve a encender sola a los 7 dias: si la pausa es mas larga, usa congelar."
}

cmd_encender() {
  local iid
  iid="$(instance_id)"
  [ -n "$iid" ] || die "No hay instancia (esta en F2). Usa: restaurar"
  case "$(db_status)" in
    available) ;;
    stopped)
      log "encendiendo la RDS (5-10 min)"
      awsc rds start-db-instance --db-instance-identifier "$DB_ID" >/dev/null
      wait_db available ;;
    *) log "la RDS esta en '$(db_status)'; esperando a que quede disponible"; wait_db available ;;
  esac
  if [ "$(instance_state "$iid")" != running ]; then
    log "encendiendo la EC2 $iid"
    awsc ec2 start-instances --instance-ids "$iid" >/dev/null
    awsc ec2 wait instance-running --instance-ids "$iid"
  fi
  wait_ssm_online "$iid"
  log "bootstrap (compose up + modelos a la GPU)"
  remote "$iid" "./aws/bootstrap.sh" 3600
  health
  state_put modo activo
  state_put desde "$(date -u +%FT%TZ)"
  log "Encendida: $(public_url)  (~0,54 USD/h)"
}

cmd_congelar() {
  local iid vol snap dbsnap tags
  iid="$(instance_id)"
  [ -n "$iid" ] || die "No hay instancia: ya esta en F2 o destruida."
  confirm congelar
  respaldo_si_se_puede "$iid"
  if [ "$(instance_state "$iid")" != stopped ]; then
    log "deteniendo la EC2"
    awsc ec2 stop-instances --instance-ids "$iid" >/dev/null
    awsc ec2 wait instance-stopped --instance-ids "$iid"
  fi
  vol="$(root_volume "$iid")"
  [ -n "$vol" ] || die "No encontre el disco raiz de $iid"
  log "snapshot del disco $vol (puede tardar 10-30 min)"
  snap="$(awsc ec2 create-snapshot --volume-id "$vol" --description "nexus F2 $(date -u +%F)" \
    --tag-specifications "ResourceType=snapshot,Tags=[{Key=project,Value=nexus},{Key=nexus-snapshot,Value=f2},{Key=Name,Value=nexus-f2-$(date -u +%Y%m%d)}]" \
    --query SnapshotId --output text)"
  state_put root_snapshot "$snap"
  wait_snapshot "$snap"

  # Para recrear nexus-db igual al restaurar.
  state_put db_vpc "$(stack_param "$DB_STACK" VpcId)"
  state_put db_subnets "$(stack_param "$DB_STACK" SubnetIds)"

  log "nexus-ec2 sin instancia ni Elastic IP"
  stack_update "$EC2_STACK" InstanceEnabled=false

  tags="$(awsc ec2 describe-volumes --volume-ids "$vol" --query 'Volumes[0].[State,Tags[?Key==`project`].Value|[0]]' --output text)"
  if [ "$tags" = "$(printf 'available\tnexus')" ]; then
    log "borrando el disco suelto $vol (ya esta en $snap)"
    awsc ec2 delete-volume --volume-id "$vol"
  else
    log "AVISO: el disco $vol no quedo suelto o no es de nexus ($tags); no se borra"
  fi

  if [ "$(stack_status "$DB_STACK")" != NONE ]; then
    log "borrando nexus-db (CloudFormation deja un snapshot final)"
    awsc cloudformation delete-stack --stack-name "$DB_STACK"
    awsc cloudformation wait stack-delete-complete --stack-name "$DB_STACK"
  fi
  dbsnap="$(awsc rds describe-db-snapshots --db-instance-identifier "$DB_ID" --snapshot-type manual \
    --query 'reverse(sort_by(DBSnapshots,&SnapshotCreateTime))[0].DBSnapshotIdentifier' --output text | clean)"
  [ -n "$dbsnap" ] || die "No encontre el snapshot final de la RDS"
  state_put db_snapshot "$dbsnap"
  state_put modo F2
  state_put desde "$(date -u +%FT%TZ)"
  log "F2 listo. Disco: $snap · RDS: $dbsnap. Para volver: restaurar"
}

cmd_restaurar() {
  local snap dbsnap vpc subnets sg host iid ip site
  snap="$(state_get root_snapshot)"
  dbsnap="$(state_get db_snapshot)"
  vpc="$(state_get db_vpc)"
  subnets="$(state_get db_subnets)"
  [ -n "$snap" ] && [ -n "$dbsnap" ] && [ -n "$vpc" ] && [ -n "$subnets" ] || die "Falta el estado de F2 en $STATE_PREFIX"
  [ -z "$(instance_id)" ] || die "Ya hay instancia; restaurar es solo para salir de F2."
  confirm restaurar
  sg="$(stack_out "$EC2_STACK" SecurityGroupId)"
  if [ "$(stack_status "$DB_STACK")" = NONE ]; then
    log "RDS desde $dbsnap (10-20 min)"
    awsc cloudformation deploy --stack-name "$DB_STACK" --template-file "$here/nexus-db.yaml" --tags project=nexus \
      --parameter-overrides "VpcId=$vpc" "SubnetIds=$subnets" "AppSecurityGroupId=$sg" "DbSnapshotIdentifier=$dbsnap"
  fi
  host="$(stack_out "$DB_STACK" DbEndpoint)"
  ssm_put "$SSM_PREFIX/DB_HOST" "$host"

  log "instancia desde $snap"
  stack_update "$EC2_STACK" InstanceEnabled=true "RootSnapshotId=$snap"
  iid="$(instance_id)"
  awsc ec2 wait instance-running --instance-ids "$iid"
  wait_ssm_online "$iid"
  ip="$(stack_out "$EC2_STACK" PublicIp)"
  site="$(ssm_get "$SSM_PREFIX/SITE_ADDRESS")"
  if [ "$site" = ":80" ]; then
    ssm_put "$SSM_PREFIX/PUBLIC_URL" "http://$ip"
  fi
  log "bootstrap (la primera lectura de los modelos es lenta: el disco se carga desde el snapshot)"
  remote "$iid" "./aws/bootstrap.sh" 3600
  health 60
  state_put modo activo
  state_put desde "$(date -u +%FT%TZ)"
  log "Restaurada en $ip. Si hay dominio sin DuckDNS, apunta el registro A a esa IP."
}

cmd_desplegar() {
  local iid bucket
  iid="$(instance_id)"
  [ -n "$iid" ] && [ "$(instance_state "$iid")" = running ] || die "La EC2 no esta encendida. Usa: encender"
  "$here/push-release.sh"
  bucket="$(stack_out "$EC2_STACK" BucketName)"
  confirm desplegar
  remote "$iid" "aws s3 cp --only-show-errors s3://$bucket/bundle/deploy-latest.tgz /tmp/deploy.tgz && tar -xzf /tmp/deploy.tgz -C /opt/nexus && ./aws/bootstrap.sh" 3600
  health
}

cmd_destruir() {
  local iid vol="" bucket
  iid="$(instance_id)"
  bucket="$(stack_out "$EC2_STACK" BucketName)"
  echo "Se borran la EC2, su disco, la Elastic IP, la RDS (queda un snapshot final), los logs de CloudWatch y el dashboard."
  echo "Quedan: bucket $bucket, repo ECR, snapshots y parametros SSM."
  confirm "destruir nexus"
  if [ -n "$iid" ]; then
    respaldo_si_se_puede "$iid"
    vol="$(root_volume "$iid")"
  fi
  # Primero la base: su SG referencia al SG de la EC2 y bloquearia el borrado de nexus-ec2.
  if [ "$(stack_status "$DB_STACK")" != NONE ]; then
    log "borrando nexus-db (snapshot final)"
    awsc cloudformation delete-stack --stack-name "$DB_STACK"
    awsc cloudformation wait stack-delete-complete --stack-name "$DB_STACK"
  fi
  if [ "$(stack_status "$EC2_STACK")" != NONE ]; then
    log "borrando nexus-ec2"
    awsc cloudformation delete-stack --stack-name "$EC2_STACK"
    awsc cloudformation wait stack-delete-complete --stack-name "$EC2_STACK"
  fi
  if [ -n "$vol" ] && [ "$(awsc ec2 describe-volumes --volume-ids "$vol" --query 'Volumes[0].State' --output text 2>/dev/null)" = available ]; then
    log "borrando el disco suelto $vol"
    awsc ec2 delete-volume --volume-id "$vol"
  fi
  state_put modo destruido
  state_put desde "$(date -u +%FT%TZ)"
  echo
  echo "Restos (se cobran hasta que se borren a mano):"
  echo "- Bucket:"; awsc s3 ls "s3://$bucket" --recursive --summarize 2>/dev/null | tail -2 || echo "  (no existe)"
  echo "- ECR nexus-backend:"; awsc ecr list-images --repository-name nexus-backend --query 'length(imageIds)' --output text 2>/dev/null || echo "  (no existe)"
  echo "- Snapshots EBS:"; awsc ec2 describe-snapshots --owner-ids self --filters Name=tag:project,Values=nexus \
    --query 'Snapshots[].[SnapshotId,VolumeSize,StartTime]' --output text
  echo "- Snapshots RDS:"; awsc rds describe-db-snapshots --snapshot-type manual \
    --query 'DBSnapshots[].[DBSnapshotIdentifier,AllocatedStorage]' --output text
}

case "$cmd" in
  estado) cmd_estado ;;
  apagar) cmd_apagar ;;
  encender) cmd_encender ;;
  congelar) cmd_congelar ;;
  restaurar) cmd_restaurar ;;
  desplegar) cmd_desplegar ;;
  destruir) cmd_destruir ;;
  *) sed -n '2,20p' "$0"; exit 1 ;;
esac
