"""
RNF-12 y concurrencia (capitulo de Pruebas, punto 17).

1. RNF-12: una recomendacion completa (Qdrant + Ollama o reglas) debe responder en 10 s o menos.
   Se piden N recomendaciones una tras otra y se mide cada una.
2. Concurrencia: rafagas de peticiones simultaneas a la recomendacion y a la agenda
   (GET /events/user/{id}); se mide el tiempo (mediana, p95, maximo) y los errores.

Uso (cmd, con el backend arrancado):
    python scripts\\carga_rnf12.py
    python scripts\\carga_rnf12.py --base http://localhost:8080/api --secuencial 5 --rafagas 5,10,20

Pide el correo y la contrasena al iniciar (no se guardan). Solo usa la libreria estandar.
Escribe los resultados en scripts/resultados-rnf12-<fecha>.md.
"""
import argparse
import concurrent.futures as cf
import datetime as dt
import getpass
import json
import statistics
import time
import urllib.error
import urllib.request

CDMX = dt.timezone(dt.timedelta(hours=-6))
LIMITE_RNF12 = 10.0


def http(method, url, token=None, body=None, timeout=60):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            payload = r.read()
            return r.status, time.perf_counter() - t0, payload
    except urllib.error.HTTPError as e:
        return e.code, time.perf_counter() - t0, e.read()
    except Exception as e:  # tiempo agotado, conexion rechazada
        return 0, time.perf_counter() - t0, str(e).encode("utf-8")


def primer_hueco(base, token, yo, pareja, minimo_min=60):
    """El primer hueco libre de los dos, desde manana, de al menos una hora (en UTC)."""
    status, _, body = http("GET", f"{base}/availability/mutual/{yo}/{pareja}", token)
    if status != 200:
        raise SystemExit(f"No se pudo leer la disponibilidad de los dos (HTTP {status}).")
    manana = (dt.datetime.now(CDMX) + dt.timedelta(days=1)).date().isoformat()
    for dia in json.loads(body).get("mutualAvailability", []):
        if dia["date"] < manana:
            continue
        for h in dia["mutualFreeSlots"]:
            ini = dt.datetime.fromisoformat(f"{dia['date']}T{h['start']}").replace(tzinfo=CDMX)
            fin = dt.datetime.fromisoformat(f"{dia['date']}T{h['end']}").replace(tzinfo=CDMX)
            if (fin - ini).total_seconds() >= minimo_min * 60:
                return ini.astimezone(dt.timezone.utc), fin.astimezone(dt.timezone.utc)
    raise SystemExit("No hay un hueco libre de los dos de 1 h o mas desde manana.")


def resumen(tiempos):
    if not tiempos:
        return "—", "—", "—"
    orden = sorted(tiempos)
    p95 = orden[max(0, int(round(0.95 * len(orden))) - 1)]
    return f"{statistics.median(orden):.2f}", f"{p95:.2f}", f"{orden[-1]:.2f}"


def rafaga(n, fn):
    with cf.ThreadPoolExecutor(max_workers=n) as pool:
        return list(pool.map(lambda _: fn(), range(n)))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://localhost:8080/api")
    ap.add_argument("--secuencial", type=int, default=5)
    ap.add_argument("--rafagas", default="5,10,20")
    a = ap.parse_args()

    correo = input("Correo: ").strip()
    clave = getpass.getpass("Contrasena: ")
    status, _, body = http("POST", f"{a.base}/auth/login", body={"email": correo, "password": clave})
    clave = None
    if status != 200:
        raise SystemExit(f"No se pudo iniciar sesion (HTTP {status}).")
    sesion = json.loads(body)
    token, yo = sesion["token"], sesion["userId"]

    status, _, body = http("GET", f"{a.base}/link/status/{yo}", token)
    pareja = json.loads(body).get("partner", {}).get("userId") if status == 200 else None
    if not pareja:
        raise SystemExit("La cuenta no tiene pareja vinculada.")
    ini, fin = primer_hueco(a.base, token, yo, pareja)
    params = {
        "zoneType": "NONE", "scope": "ANY", "maxCostMxn": 1500,
        "slotStart": ini.isoformat().replace("+00:00", "Z"),
        "slotEnd": fin.isoformat().replace("+00:00", "Z"),
    }

    def recomendar():
        return http("POST", f"{a.base}/recommendations/{yo}", token, params, timeout=60)

    def agenda():
        return http("GET", f"{a.base}/events/user/{yo}", token, timeout=60)

    hoy = dt.datetime.now(CDMX)
    lineas = [
        f"# Resultados RNF-12 y concurrencia — {hoy:%Y-%m-%d %H:%M} (CDMX)", "",
        f"Servidor: `{a.base}`. Hueco usado: {ini.astimezone(CDMX):%a %d %b %H:%M}–{fin.astimezone(CDMX):%H:%M}.",
        "Recomendacion: sin filtro de distancia, cualquier lugar, hasta $1,500.", "",
        "## 1. RNF-12 (una recomendacion a la vez, limite 10 s)", "",
        "| # | HTTP | Tiempo (s) | Cumple | Selección |", "|---|---|---|---|---|",
    ]
    secuenciales = []
    for i in range(1, a.secuencial + 1):
        s, t, body = recomendar()
        secuenciales.append(t)
        # VECTOR = busqueda en Qdrant; CATALOG = catalogo de MySQL. Quien redacto (Mistral o
        # reglas) solo sale en el registro del backend: "compose mode=...".
        fuente = json.loads(body).get("selectionSource", "?") if s == 200 else "—"
        lineas.append(f"| {i} | {s} | {t:.2f} | {'si' if s == 200 and t <= LIMITE_RNF12 else 'no'} | {fuente} |")
        print(f"recomendacion {i}: HTTP {s} en {t:.2f} s ({fuente})")
    med, p95, mx = resumen(secuenciales)
    lineas += ["", f"Mediana {med} s · p95 {p95} s · maximo {mx} s.", "",
               "## 2. Concurrencia (peticiones simultaneas)", "",
               "| Servicio | Simultaneas | Correctas | Errores | Mediana (s) | p95 (s) | Maximo (s) |",
               "|---|---|---|---|---|---|---|"]
    for n in [int(x) for x in a.rafagas.split(",") if x.strip()]:
        for nombre, fn in (("Recomendacion", recomendar), ("Agenda", agenda)):
            res = rafaga(n, fn)
            ok = [t for s, t, _ in res if s == 200]
            errores = sorted({s for s, _, _ in res if s != 200})
            med, p95, mx = resumen([t for _, t, _ in res])
            lineas.append(f"| {nombre} | {n} | {len(ok)} | {n - len(ok)}{' (' + ', '.join(map(str, errores)) + ')' if errores else ''} | {med} | {p95} | {mx} |")
            print(f"{nombre} x{n}: {len(ok)}/{n} correctas, mediana {med} s, max {mx} s")

    salida = f"scripts/resultados-rnf12-{hoy:%Y-%m-%d}.md"
    with open(salida, "w", encoding="utf-8") as f:
        f.write("\n".join(lineas) + "\n")
    print(f"\nResultados en {salida}")


if __name__ == "__main__":
    main()
