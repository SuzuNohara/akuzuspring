# Resultados RNF-12 y concurrencia — 2026-11-01 14:17 (CDMX)

Servidor: `http://localhost:8080/api`. Hueco usado: Mon 02 Nov 19:00–20:00.
Recomendacion: sin filtro de distancia, cualquier lugar, hasta $1,500.

## 1. RNF-12 (una recomendacion a la vez, limite 10 s)

| # | HTTP | Tiempo (s) | Cumple |
|---|---|---|---|
| 1 | 200 | 10.93 | no |
| 2 | 200 | 8.57 | si |
| 3 | 200 | 8.56 | si |
| 4 | 200 | 8.93 | si |
| 5 | 200 | 8.90 | si |

Mediana 8.90 s · p95 10.93 s · maximo 10.93 s.

## 2. Concurrencia (peticiones simultaneas)

| Servicio | Simultaneas | Correctas | Errores | Mediana (s) | p95 (s) | Maximo (s) |
|---|---|---|---|---|---|---|
| Recomendacion | 5 | 5 | 0 | 11.64 | 13.00 | 13.00 |
| Agenda | 5 | 5 | 0 | 0.04 | 0.05 | 0.05 |
| Recomendacion | 10 | 10 | 0 | 21.96 | 22.28 | 22.28 |
| Agenda | 10 | 10 | 0 | 0.12 | 0.15 | 0.15 |
| Recomendacion | 20 | 19 | 1 (500) | 31.41 | 38.82 | 41.93 |
| Agenda | 20 | 20 | 0 | 0.13 | 0.15 | 0.15 |
