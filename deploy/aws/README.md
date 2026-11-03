# deploy/aws — NEXUS en AWS: EC2 con GPU + RDS MySQL

Dos stacks de CloudFormation en us-east-1 (cuenta 051084216118):

| Stack | Plantilla | Qué tiene |
|---|---|---|
| `nexus-ec2` | `nexus-ec2.yaml` | EC2 `g4dn.xlarge` con la AMI *Deep Learning Base OSS Nvidia Driver GPU AMI (Ubuntu 24.04)*, Elastic IP, SG solo 80/443 (sin 22: todo por SSM), rol con SSM + ECR + `/nexus/prod/*` + bucket, bucket S3 (`bundle/`, `backups/`), repo ECR `nexus-backend` y snapshot diario del disco por DLM |
| `nexus-db` | `nexus-db.yaml` | RDS MySQL 8.0 `db.t4g.micro`, 20 GB gp3 cifrado, sin IP pública, 3306 solo desde el SG de la EC2, TLS obligatorio, respaldos de 7 días y snapshot final al borrar el stack |

En la EC2 corren con `docker compose` Caddy, backend, Qdrant y Ollama (`docker-compose.yml` + `docker-compose.gpu.yml`
+ `docker-compose.aws.yml`). El servicio `mysql` del compose solo existe en la laptop: en AWS queda en el perfil
`local-db` y no arranca.

Los comandos locales usan `AWS_PROFILE=nexus-admin` y `AWS_REGION=us-east-1`. El AWS CLI de snap solo muestra
salida en la sesión de NOVA si se canaliza (`aws … | cat`).

## Parámetros en SSM (`/nexus/prod/<VARIABLE>`)

Cada parámetro es una línea de `deploy/.env` en la EC2 (`render-env.sh`).

| Parámetro | Tipo | Quién lo pone |
|---|---|---|
| `DB_MASTER_PASSWORD`, `DB_PASSWORD`, `JWT_SECRET`, `AES_SECRET` | SecureString | NOVA, con `openssl rand -hex` directo a `put-parameter` (sin mostrarlos). `DB_MASTER_PASSWORD` con `-hex 20`: RDS MySQL admite como máximo 41 caracteres |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | SecureString | el dev, en su terminal |
| `DUCKDNS_TOKEN` | SecureString | el dev, si el DNS es DuckDNS |
| `DB_HOST` (salida `DbEndpoint` de `nexus-db`), `DB_MASTER_USER` (`admin`), `DB_USER` (`nexus`), `SITE_ADDRESS`, `PUBLIC_URL`, `BACKUP_BUCKET`, `DUCKDNS_DOMAIN` | String | NOVA |
| `BACKEND_IMAGE` | String | `push-release.sh` |

Sin dominio: `SITE_ADDRESS=:80` y `PUBLIC_URL=http://<elastic-ip>`. Con dominio: `SITE_ADDRESS=<dominio>` y
`PUBLIC_URL=https://<dominio>`.

## Primer despliegue

1. AMI fijada: `aws ssm get-parameter --name /aws/service/deeplearning/ami/x86_64/base-oss-nvidia-driver-gpu-ubuntu-24.04/latest/ami-id`.
2. `nexus-ec2`: `aws cloudformation deploy --stack-name nexus-ec2 --template-file deploy/aws/nexus-ec2.yaml --capabilities CAPABILITY_IAM --parameter-overrides ImageId=… VpcId=… SubnetId=…` (una subred de una AZ que ofrezca el tipo).
3. Secretos en SSM (tabla de arriba), incluida `DB_MASTER_PASSWORD` **antes** de crear `nexus-db`.
4. `nexus-db`: `aws cloudformation deploy --stack-name nexus-db --template-file deploy/aws/nexus-db.yaml --parameter-overrides VpcId=… SubnetIds=<dos o más subredes> AppSecurityGroupId=<salida SecurityGroupId de nexus-ec2>`. Guardar `DbEndpoint` en `/nexus/prod/DB_HOST`.
5. `deploy/aws/push-release.sh`.
6. Run Command (`AWS-RunShellScript`, que corre con `sh`: usar `set -eu`, no `pipefail`; la salida completa va a
   S3 con `--output-s3-bucket-name <bucket> --output-s3-key-prefix backups/ssm-logs`) en la instancia:
   ```
   mkdir -p /opt/nexus && cd /opt/nexus
   aws s3 cp s3://<bucket>/bundle/deploy-latest.tgz /tmp/deploy.tgz
   tar -xzf /tmp/deploy.tgz -C /opt/nexus
   /opt/nexus/deploy/aws/render-env.sh
   /opt/nexus/deploy/aws/db-init.sh          # solo la primera vez: carga init.sql en la RDS
   /opt/nexus/deploy/aws/bootstrap.sh
   ```
7. Verificar: `nvidia-smi`, `ollama ps` (100 % GPU), `/api/auth/health` y los conteos que imprime `db-init.sh`.

## Operación: `nexus-ops.sh`

`AWS_PROFILE=nexus-admin deploy/aws/nexus-ops.sh <comando> [--yes]`. Cada comando pide escribir una frase antes
de tocar nada (`--yes` la omite, para sunsetcli). El estado de las rutinas (modo, snapshots de F2, VPC y subredes
de la base) vive en SSM bajo `/nexus/state/`, fuera de `/nexus/prod` para que no entre al `.env`.

| Comando | Pasos | Costo después |
|---|---|---|
| `estado` | modo, EC2, RDS, salud, respaldos, snapshots | — |
| `apagar` (F1) | `respaldar.sh --s3` → stop EC2 → stop RDS | ~19 USD/mes. **La RDS se reenciende sola a los 7 días** |
| `encender` | start RDS → `available` → start EC2 → SSM `Online` → `bootstrap.sh` → salud | 0,54 USD/h |
| `congelar` (F2) | respaldo → stop → snapshot del disco (`nexus-snapshot=f2`) → `nexus-ec2` con `InstanceEnabled=false` → borra el disco suelto → borra `nexus-db` (snapshot final) | ~7 USD/mes |
| `restaurar` | `nexus-db` desde el snapshot final → `DB_HOST` → `nexus-ec2` con `InstanceEnabled=true RootSnapshotId=<snap>` → `PUBLIC_URL` con la IP nueva (si no hay dominio) → `bootstrap.sh` | 0,54 USD/h |
| `desplegar` | `push-release.sh` → bundle + `bootstrap.sh` → salud | — |
| `destruir` | respaldo → borra `nexus-db` (snapshot final) **antes** que `nexus-ec2` (el SG de la base referencia al de la EC2) → borra el disco suelto → lista los restos | ~2–4 USD/mes en restos |

Las rutinas usan `update-stack --use-previous-template`: no aplican cambios de plantilla pendientes. Tras
`restaurar`, `DbSnapshotIdentifier` queda fijado en `nexus-db`: no vaciarlo (reemplazaría la base).
`RootSnapshotId` solo se cambia sin instancia (con instancia, la reemplaza).

En la EC2 quedan Qdrant, los modelos y los certificados de Caddy, en volúmenes Docker sobre el disco raíz
(`DeleteOnTermination: false`). La base vive solo en la RDS.

## Monitoreo: `nexus-monitor.sh`

`AWS_PROFILE=nexus-admin deploy/aws/nexus-monitor.sh <comando> [--desde 15m|1h|1d|7d]`

| Comando | Fuente | Qué muestra |
|---|---|---|
| `api` | Logs Insights sobre `/nexus/prod/caddy` (una línea JSON por petición; Caddy oculta `Authorization` y `Cookie`) | peticiones por status, latencia p50/p95/max, rutas top, clientes top, últimos 5xx |
| `agente` | `/nexus/prod/backend` (`compose mode=… llm_ms=…`) y `/nexus/prod/ollama` (`[GIN]`) | recomendaciones por modo (LLM, LLM_FILLED, RULES_*), llm_ms p50/p95/max, tokens, fuera de presupuesto, avisos `[t06b]`, llamadas a Ollama |
| `db` | métricas `AWS/RDS` + `/aws/rds/instance/nexus-prod-mysql/{slowquery,error}` | CPU, créditos, conexiones, memoria, swap, IOPS, latencias, disco; consultas > 1 s; errores |
| `ec2` | métricas `AWS/EC2` + SSM | CPU, red, status checks; GPU, memoria, disco, contenedores, `ollama ps` |
| `logs <svc>` / `buscar <svc> <patrón>` | `aws logs tail` | en vivo o filtrado: `backend`, `caddy`, `ollama`, `qdrant`, `db-error`, `db-slow` |
| `resumen` / `dashboard` | todo / dashboard `nexus-prod` | métricas `NEXUS/*` (ApiRequests, Api5xx, ApiLatency, AgentCompositions, AgentLlm, AgentRules, BackendErrors) + EC2 + RDS |

Los contenedores mandan los logs con el driver `awslogs` (`docker-compose.aws.yml`); Docker guarda copia local,
así que `docker compose logs` sigue funcionando. Retención 14 días (`LogRetentionDays`).

No disponible: Performance Insights (no existe para `db.t4g.micro`), memoria/disco/GPU como métricas de CloudWatch
(requieren el CloudWatch Agent; `ec2` los lee por SSM), y el texto de cada consulta SQL (el log `general` o el
plugin de auditoría lo darían, a costa de volumen y datos personales en los logs).
