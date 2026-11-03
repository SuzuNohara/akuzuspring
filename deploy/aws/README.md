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
| `DB_MASTER_PASSWORD`, `DB_PASSWORD`, `JWT_SECRET`, `AES_SECRET` | SecureString | NOVA, con `openssl rand -hex` directo a `put-parameter` (sin mostrarlos) |
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
6. Run Command (`AWS-RunShellScript`) en la instancia:
   ```
   mkdir -p /opt/nexus && cd /opt/nexus
   aws s3 cp s3://<bucket>/bundle/deploy-latest.tgz /tmp/deploy.tgz
   tar -xzf /tmp/deploy.tgz -C /opt/nexus
   /opt/nexus/deploy/aws/render-env.sh
   /opt/nexus/deploy/aws/db-init.sh          # solo la primera vez: carga init.sql en la RDS
   /opt/nexus/deploy/aws/bootstrap.sh
   ```
7. Verificar: `nvidia-smi`, `ollama ps` (100 % GPU), `/api/auth/health` y los conteos que imprime `db-init.sh`.

## Operación

| Rutina | Pasos |
|---|---|
| `deploy` | `push-release.sh` → Run Command: bundle + `bootstrap.sh` |
| `freeze` F1 | Run Command `respaldar.sh --s3` → `ec2 stop-instances` → `rds stop-db-instance` (AWS la vuelve a encender sola a los 7 días) |
| `wake` F1 | `rds start-db-instance` → esperar `available` → `ec2 start-instances` → SSM `Online` → Run Command `bootstrap.sh` |
| `freeze` F2 | `respaldar.sh --s3` → `stop-instances` → `create-snapshot` del disco raíz (`nexus-snapshot=f2`), esperar `completed` → `nexus-ec2` con `InstanceEnabled=false` → `delete-volume` del disco suelto → `delete-stack nexus-db` (deja el snapshot final) |
| `restore` F2 | `nexus-db` con `DbSnapshotIdentifier=<snapshot final>` → `DB_HOST` nuevo en SSM → `nexus-ec2` con `InstanceEnabled=true RootSnapshotId=<snap>` → Run Command: bundle + `bootstrap.sh` (el DNS va a la IP nueva) |
| `destroy` | `respaldar.sh --s3` → `delete-stack nexus-ec2` → borrar el disco suelto → `delete-stack nexus-db` (snapshot final). El bucket, el repo ECR y los snapshots quedan hasta que se pida borrarlos |

En la EC2 quedan Qdrant, los modelos y los certificados de Caddy, en volúmenes Docker sobre el disco raíz
(`DeleteOnTermination: false`). La base vive solo en la RDS.
