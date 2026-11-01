# Mistral 7B solo — 2026-11-01 15:01

Equipo: Intel64 Family 6 Model 170 Stepping 4, GenuineIntel · modelo `mistral:7b-instruct-v0.3-q4_K_M` · opciones {'num_ctx': 8192, 'num_predict': 600, 'temperature': 0.2}.

| Corrida | Total (s) | Carga del modelo (s) | Prompt: tokens / s | Generación: tokens / s | Tokens por segundo |
|---|---|---|---|---|---|
| A · Modelo sin cargar | 454.4 | 0.0 | 145 / 7.4 | 160 / 28.4 | 5.64 |
| A · Modelo cargado 1 | 40.6 | 10.2 | 145 / 6.2 | 135 / 24.2 | 5.57 |
| A · Modelo cargado 2 | 29.8 | 0.0 | 145 / 0.2 | 165 / 29.6 | 5.58 |
| B · Tamaño real del agente (cargado) 1 | 151.4 | 0.0 | 1788 / 93.1 | 253 / 58.2 | 4.35 |
| B · Tamaño real del agente (cargado) 2 | 60.9 | 0.0 | 1788 / 0.2 | 267 / 60.7 | 4.40 |
