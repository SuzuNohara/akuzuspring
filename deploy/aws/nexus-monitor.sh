#!/usr/bin/env bash
# [nexus-deploy] Monitoreo de NEXUS en AWS: logs en CloudWatch, metricas y estado de la GPU.
#
# Uso: AWS_PROFILE=nexus-admin deploy/aws/nexus-monitor.sh <comando> [--desde 1h]
#
#   resumen               api + agente + db + ec2 (ventana --desde, por defecto 1h)
#   api                   peticiones por status, latencia p50/p95/max, rutas mas usadas y lentas,
#                         clientes, ultimos 5xx                     (Logs Insights sobre /nexus/prod/caddy)
#   agente                recomendaciones por modo (LLM, LLM_FILLED, RULES_*), llm_ms p50/p95/max, tokens,
#                         casos fuera de presupuesto, avisos [t06b]/[T-06], llamadas a Ollama
#   db                    metricas de la RDS (CPU, creditos, conexiones, memoria, IOPS, latencias, disco)
#                         + consultas lentas (> 1 s) + log de errores
#   ec2                   CPU, red y status checks + GPU, memoria, disco y contenedores (por SSM)
#   logs <servicio>       sigue en vivo: backend | caddy | ollama | qdrant | db-error | db-slow
#   buscar <servicio> <patron>   busca en los logs (sintaxis de filter pattern de CloudWatch)
#   dashboard             URL del dashboard nexus-prod
#
# Los logs de los contenedores existen desde que bootstrap.sh corre con docker-compose.aws.yml de logs
# (commit de monitoreo). Retencion: 14 dias. Las metricas de EC2/RDS son de AWS y no requieren nada.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=ops-lib.sh
source "$here/ops-lib.sh"

cmd="${1:-}"; shift || true
desde="1h"
args=()
while [ $# -gt 0 ]; do
  case "$1" in
    --desde) desde="$2"; shift 2 ;;
    *) args+=("$1"); shift ;;
  esac
done
SINCE="$(to_seconds "$desde")"
NOW="$(date +%s)"
START=$(( NOW - SINCE ))

group_of() {
  case "$1" in
    backend|caddy|ollama|qdrant) echo "$LOG_PREFIX/$1" ;;
    db-error) echo "/aws/rds/instance/$DB_ID/error" ;;
    db-slow) echo "/aws/rds/instance/$DB_ID/slowquery" ;;
    *) die "Servicio desconocido: $1" ;;
  esac
}

# insights <titulo> <grupo> <consulta>: corre Logs Insights y lo imprime como tabla.
insights() {
  local title="$1" group="$2" query="$3" qid st
  echo "── $title"
  if ! qid="$(awsc logs start-query --log-group-name "$group" --start-time "$START" --end-time "$NOW" \
      --query-string "$query" --query queryId --output text 2>/dev/null)"; then
    echo "   (sin el grupo $group: aun no hay logs o no se ha desplegado la capa de logs)"; echo; return 0
  fi
  for _ in $(seq 1 60); do
    st="$(awsc logs get-query-results --query-id "$qid" --query status --output text)"
    case "$st" in Complete|Failed|Cancelled|Timeout) break ;; esac
    sleep 1
  done
  awsc logs get-query-results --query-id "$qid" --output json | python3 -I -c '
import json, sys
rows = [{c["field"]: c["value"] for c in r if c["field"] != "@ptr"} for r in json.load(sys.stdin)["results"]]
if not rows:
    print("   (sin datos en la ventana)"); print(); sys.exit()
cols = list(dict.fromkeys(k for r in rows for k in r))
def fmt(v):
    try:
        f = float(v)
        return v if f.is_integer() and "." not in v else f"{f:.3f}"
    except ValueError:
        return v if len(v) <= 110 else v[:107] + "..."
table = [[fmt(r.get(c, "")) for c in cols] for r in rows]
w = [max(len(c), *(len(t[i]) for t in table)) for i, c in enumerate(cols)]
print("   " + "  ".join(c.ljust(w[i]) for i, c in enumerate(cols)))
for t in table:
    print("   " + "  ".join(v.ljust(w[i]) for i, v in enumerate(t)))
print()'
}

# metric <namespace> <metrica> <dimension=valor> <estadistica> [escala] [unidad]
metric() {
  local ns="$1" name="$2" dim="$3" stat="$4" scale="${5:-1}" unit="${6:-}" v
  v="$(awsc cloudwatch get-metric-statistics --namespace "$ns" --metric-name "$name" \
    --dimensions "Name=${dim%%=*},Value=${dim#*=}" --start-time "$START" --end-time "$NOW" \
    --period "$SINCE" --statistics "$stat" --query "Datapoints[0].$stat" --output text | clean)"
  if [ -z "$v" ]; then v="—"; else v="$(python3 -I -c 'import sys; print(f"{float(sys.argv[1])/float(sys.argv[2]):.2f}")' "$v" "$scale")"; fi
  printf '   %-26s %-8s %s %s\n' "$name" "$stat" "$v" "$unit"
}

cmd_api() {
  local g; g="$(group_of caddy)"
  echo "═══ API (Caddy) · ultimas $desde"
  insights "Peticiones por status" "$g" 'filter request.uri like /^\/api\// | stats count(*) as peticiones by status | sort status'
  insights "Latencia (s)" "$g" 'filter request.uri like /^\/api\// | stats count(*) as n, pct(duration,50) as p50, pct(duration,95) as p95, max(duration) as max'
  insights "Rutas (top 15 por volumen)" "$g" 'filter request.uri like /^\/api\// | parse request.uri /^\/api\/(?<ruta>[a-z-]+\/?[a-z-]*)/ | stats count(*) as n, avg(duration) as prom_s, max(duration) as max_s by request.method, ruta | sort n desc | limit 15'
  insights "Clientes (top 10)" "$g" 'filter request.uri like /^\/api\// | stats count(*) as n by request.client_ip | sort n desc | limit 10'
  insights "Ultimos errores 5xx" "$g" 'filter status >= 500 | fields @timestamp, request.method, request.uri, status, duration | sort @timestamp desc | limit 15'
}

cmd_agente() {
  local g; g="$(group_of backend)"
  echo "═══ Agente (RecommendationComposer) · ultimas $desde"
  insights "Recomendaciones por modo (llm_ms en ms)" "$g" 'filter @message like /compose mode=/
    | parse @message /mode=(?<modo>\S+) attempts=(?<intentos>\d+) .* llm_ms=(?<llm_ms>\d+) over_budget_ms=(?<fuera_ms>\d+) prompt_tokens=(?<tok_in>\d+) eval_tokens=(?<tok_out>\d+) dropped=(?<descartadas>\d+) replaced=(?<reemplazadas>\d+)/
    | stats count(*) as n, pct(llm_ms,50) as p50_ms, pct(llm_ms,95) as p95_ms, max(llm_ms) as max_ms, max(fuera_ms) as max_fuera_ms, avg(tok_out) as tokens_gen, sum(descartadas) as descartadas by modo
    | sort n desc'
  insights "Ultimas 10 composiciones" "$g" 'filter @message like /compose mode=/ | parse @message /compose (?<traza>.*)/ | fields @timestamp, traza | sort @timestamp desc | limit 10'
  insights "Solicitudes de recomendacion" "$g" 'filter @message like /Solicitud \d+:|Planeacion en pareja, solicitud/ | stats count(*) as solicitudes by bin(1h) as hora | sort hora desc | limit 24'
  insights "Avisos del agente y errores" "$g" 'filter @message like /\[t06b\]|\[T-06\]|\[t06-|ERROR/ | fields @timestamp, @message | sort @timestamp desc | limit 15'
  insights "Llamadas a Ollama por ruta y codigo" "$(group_of ollama)" 'filter @message like /\[GIN\]/ | parse @message /\|\s*(?<codigo>\d{3})\s*\|\s*(?<latencia>\S+)\s*\|.*\|\s*(?<metodo>[A-Z]+)\s+"(?<ruta>[^"]+)"/ | stats count(*) as n by ruta, codigo | sort n desc'
}

cmd_db() {
  local d="DBInstanceIdentifier=$DB_ID"
  echo "═══ Base de datos ($DB_ID: $(db_status)) · ultimas $desde"
  echo "── Metricas de CloudWatch (AWS/RDS)"
  metric AWS/RDS CPUUtilization "$d" Average 1 %
  metric AWS/RDS CPUUtilization "$d" Maximum 1 %
  metric AWS/RDS CPUCreditBalance "$d" Minimum 1 creditos
  metric AWS/RDS DatabaseConnections "$d" Maximum 1 conexiones
  metric AWS/RDS FreeableMemory "$d" Minimum 1048576 MB
  metric AWS/RDS SwapUsage "$d" Maximum 1048576 MB
  metric AWS/RDS FreeStorageSpace "$d" Minimum 1073741824 GB
  metric AWS/RDS ReadIOPS "$d" Average 1 IOPS
  metric AWS/RDS WriteIOPS "$d" Average 1 IOPS
  metric AWS/RDS ReadLatency "$d" Average 0.001 ms
  metric AWS/RDS WriteLatency "$d" Average 0.001 ms
  echo
  insights "Consultas lentas (> 1 s)" "$(group_of db-slow)" 'parse @message /Query_time: (?<seg>[\d.]+)\s+Lock_time: (?<lock>[\d.]+)\s+Rows_sent: (?<filas>\d+)\s+Rows_examined: (?<examinadas>\d+)/ | filter ispresent(seg) | stats count(*) as lentas, avg(seg) as prom_s, max(seg) as max_s, max(examinadas) as max_filas_examinadas'
  insights "Ultimas consultas lentas" "$(group_of db-slow)" 'fields @timestamp, @message | sort @timestamp desc | limit 5'
  insights "Log de errores de MySQL" "$(group_of db-error)" 'fields @timestamp, @message | filter @message like /ERROR|Warning|denied/ | sort @timestamp desc | limit 10'
}

cmd_ec2() {
  local iid; iid="$(instance_id)"
  [ -n "$iid" ] || { echo "═══ EC2: sin instancia (F2 o destruida)"; return 0; }
  local d="InstanceId=$iid" st; st="$(instance_state "$iid")"
  echo "═══ EC2 $iid ($st) · ultimas $desde"
  echo "── Metricas de CloudWatch (AWS/EC2)"
  metric AWS/EC2 CPUUtilization "$d" Average 1 %
  metric AWS/EC2 CPUUtilization "$d" Maximum 1 %
  metric AWS/EC2 NetworkIn "$d" Sum 1048576 MB
  metric AWS/EC2 NetworkOut "$d" Sum 1048576 MB
  metric AWS/EC2 StatusCheckFailed "$d" Maximum 1 "(0 = bien)"
  echo
  if [ "$st" = running ]; then
    echo "── En la instancia (SSM): GPU, memoria, disco, contenedores, modelos"
    remote "$iid" 'nvidia-smi --query-gpu=name,utilization.gpu,memory.used,memory.total,temperature.gpu --format=csv || echo "sin GPU"; echo; free -h; echo; df -h / | tail -1; echo; docker ps --format "table {{.Names}}\t{{.Status}}"; echo; docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}"; echo; docker exec nexus-ollama-1 ollama ps' 120
  fi
}

cmd_logs() {
  [ "${#args[@]}" -ge 1 ] || die "Uso: logs <backend|caddy|ollama|qdrant|db-error|db-slow>"
  awsc logs tail "$(group_of "${args[0]}")" --follow --since "$desde" --format short
}

cmd_buscar() {
  [ "${#args[@]}" -ge 2 ] || die "Uso: buscar <servicio> <patron>  (ej. buscar backend '\"compose mode=RULES\"')"
  awsc logs tail "$(group_of "${args[0]}")" --since "$desde" --filter-pattern "${args[1]}" --format short
}

case "$cmd" in
  resumen) cmd_api; cmd_agente; cmd_db; cmd_ec2 ;;
  api) cmd_api ;;
  agente) cmd_agente ;;
  db) cmd_db ;;
  ec2) cmd_ec2 ;;
  logs) cmd_logs ;;
  buscar) cmd_buscar ;;
  dashboard) echo "https://${AWS_REGION}.console.aws.amazon.com/cloudwatch/home?region=${AWS_REGION}#dashboards/dashboard/$(stack_out "$EC2_STACK" DashboardName)" ;;
  *) sed -n '2,22p' "$0"; exit 1 ;;
esac
