# deploy/aws — NEXUS en una EC2 con GPU

Stack CloudFormation `nexus-ec2` (us-east-1, cuenta 051084216118) definido en `nexus-ec2.yaml`:
EC2 `g4dn.xlarge` con la AMI *Deep Learning Base OSS Nvidia Driver GPU AMI (Ubuntu 24.04)*, Elastic IP, SG solo
80/443, rol con SSM + ECR + `/nexus/prod/*` + bucket, bucket S3 (`bundle/`, `backups/`), repo ECR
`nexus-backend` y snapshot diario por DLM. Sin puerto 22: todo por SSM.

Todos los comandos locales usan `AWS_PROFILE=nexus-admin` y `AWS_REGION=us-east-1`.

## Parámetros en SSM (`/nexus/prod/<VARIABLE>`)

Cada parámetro es una línea de `deploy/.env` en la EC2 (`render-env.sh`).

| Parámetro | Tipo | Quién lo pone |
|---|---|---|
| `MYSQL_ROOT_PASSWORD`, `DB_PASSWORD`, `JWT_SECRET`, `AES_SECRET` | SecureString | NOVA, generados con `openssl rand` sin mostrarlos |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | SecureString | el dev (`read -rs` → `put-parameter`) |
| `DUCKDNS_TOKEN` | SecureString | el dev, si el DNS es DuckDNS |
| `DB_USER` (`nexus`), `SITE_ADDRESS`, `PUBLIC_URL`, `BACKUP_BUCKET`, `DUCKDNS_DOMAIN` | String | NOVA |
| `BACKEND_IMAGE` | String | `push-release.sh` |

Sin dominio: `SITE_ADDRESS=:80` y `PUBLIC_URL=http://<elastic-ip>`. Con dominio: `SITE_ADDRESS=<dominio>` y
`PUBLIC_URL=https://<dominio>`.

## Primer despliegue

1. AMI fijada: `aws ssm get-parameter --name /aws/service/deeplearning/ami/x86_64/base-oss-nvidia-driver-gpu-ubuntu-24.04/latest/ami-id`.
2. Subred: una subred por defecto de una AZ que ofrezca el tipo (`ec2 describe-instance-type-offerings --location-type availability-zone`).
3. `aws cloudformation deploy --stack-name nexus-ec2 --template-file deploy/aws/nexus-ec2.yaml --capabilities CAPABILITY_IAM --parameter-overrides ImageId=… VpcId=… SubnetId=…`
4. Parámetros de SSM (tabla de arriba).
5. `deploy/aws/push-release.sh`.
6. Run Command (`AWS-RunShellScript`) en la instancia:
   ```
   mkdir -p /opt/nexus && cd /opt/nexus
   aws s3 cp s3://<bucket>/bundle/deploy-latest.tgz /tmp/deploy.tgz
   tar -xzf /tmp/deploy.tgz -C /opt/nexus
   /opt/nexus/deploy/aws/bootstrap.sh
   ```
7. Verificar: `nvidia-smi`, `ollama ps` (100 % GPU) y `/api/auth/health`.

## Operación

| Rutina | Pasos |
|---|---|
| `deploy` | `push-release.sh` → Run Command del paso 6 |
| `freeze` F1 | Run Command `/opt/nexus/deploy/respaldar.sh --s3` → `ec2 stop-instances` |
| `wake` F1 | `ec2 start-instances` → esperar `instance-status-ok` y SSM `Online` → Run Command `bootstrap.sh` (precalienta) |
| `freeze` F2 | Run Command `respaldar.sh --s3` → `stop-instances` → `create-snapshot` del disco raíz (etiqueta `nexus-snapshot=f2`) y esperar `completed` → `deploy` del stack con `InstanceEnabled=false` → `delete-volume` del disco que queda suelto |
| `restore` F2 | `deploy` del stack con `InstanceEnabled=true RootSnapshotId=<snap>` → Run Command del paso 6 (DuckDNS se actualiza con la IP nueva) |
| `destroy` | Run Command `respaldar.sh --s3` → `delete-stack` → borrar el disco suelto. El bucket y el repo ECR quedan (`Retain`) hasta que se pida borrarlos |

Los modelos y los datos viven en volúmenes Docker en el disco raíz, que tiene `DeleteOnTermination: false`.
