# Resultados RNF-12 y concurrencia — 2026-11-01 14:56 (CDMX)

Servidor: `http://localhost:8080/api`. Hueco usado: Mon 02 Nov 19:00–20:00.
Recomendacion: sin filtro de distancia, cualquier lugar, hasta $1,500.

## 1. RNF-12 (una recomendacion a la vez, limite 10 s)

| # | HTTP | Tiempo (s) | Cumple | Selección |
|---|---|---|---|---|
| 1 | 200 | 7.21 | si | VECTOR |
| 2 | 200 | 8.71 | si | VECTOR |
| 3 | 200 | 8.87 | si | VECTOR |
| 4 | 200 | 9.01 | si | VECTOR |
| 5 | 200 | 8.98 | si | VECTOR |

Mediana 8.87 s · p95 9.01 s · maximo 9.01 s.

## 2. Concurrencia (peticiones simultaneas)

| Servicio | Simultaneas | Correctas | Errores | Mediana (s) | p95 (s) | Maximo (s) |
|---|---|---|---|---|---|---|
| Recomendacion | 5 | 5 | 0 | 10.89 | 11.38 | 11.38 |
| Agenda | 5 | 5 | 0 | 0.08 | 0.08 | 0.08 |
| Recomendacion | 10 | 10 | 0 | 14.50 | 18.92 | 18.92 |
| Agenda | 10 | 10 | 0 | 0.07 | 0.08 | 0.08 |
| Recomendacion | 20 | 20 | 0 | 25.64 | 35.83 | 36.00 |
| Agenda | 20 | 20 | 0 | 0.18 | 0.23 | 0.23 |

## 3. Quién redactó (registro del backend, `compose mode=`)

De las 40 recomendaciones de la corrida (5 seguidas + 35 en ráfagas), **ninguna** la redactó
Mistral; todas salieron por reglas con la frase del catálogo. La selección sí usó la búsqueda
vectorial (Qdrant + bge-m3: `VECTOR`) en las 5 seguidas.

| Modo | Cuántas | Qué significa |
|---|---|---|
| `RULES_TIMEOUT` | 13 | Se llamó a Mistral y no respondió dentro de su tiempo (esperó 2.1–7.0 s) |
| `RULES_NO_BUDGET` | 27 | Ya no quedaba presupuesto para llamar a Mistral |
| `LLM` / `LLM_FILLED` | 0 | — |

Las 5 seguidas: 1 `RULES_NO_BUDGET` (la primera, en frío) y 4 `RULES_TIMEOUT`.
En esta corrida no hubo errores 500 (en la de las 14:17, commit `b4e1894`, hubo 1 de 20).
