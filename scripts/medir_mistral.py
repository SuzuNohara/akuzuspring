"""
RNF-12: cuanto tarda Mistral solo (sin el presupuesto del agente) en este equipo.

A. Prueba de la Tabla N del capitulo de Pruebas: una frase para cada una de cinco actividades,
   tres corridas (la primera con el modelo descargado, las otras con el modelo cargado).
B. Una llamada del tamano real del agente: 30 candidatas (top-k) del catalogo en Qdrant, elegir 5
   y escribir su frase, con las opciones del agente (num_ctx 8192, num_predict 600, temp 0.2).

Uso (cmd, con Ollama y Qdrant arriba): python scripts\\medir_mistral.py
Escribe scripts/resultados-mistral-<fecha>.md. Solo libreria estandar.
"""
import datetime as dt
import json
import platform
import urllib.request

OLLAMA = "http://127.0.0.1:11434"
QDRANT = "http://127.0.0.1:6333"
MODELO = "mistral:7b-instruct-v0.3-q4_K_M"
OPCIONES = {"num_ctx": 8192, "num_predict": 600, "temperature": 0.2}
NS = 1e9


def post(url, body, timeout=900):
    req = urllib.request.Request(url, data=json.dumps(body).encode("utf-8"), method="POST")
    req.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.loads(r.read())


def generar(prompt, formato=None):
    body = {"model": MODELO, "prompt": prompt, "stream": False, "options": OPCIONES}
    if formato:
        body["format"] = formato
    r = post(f"{OLLAMA}/api/generate", body)
    gen = r.get("eval_duration", 0) / NS
    return {
        "total": r.get("total_duration", 0) / NS,
        "carga": r.get("load_duration", 0) / NS,
        "prompt_s": r.get("prompt_eval_duration", 0) / NS,
        "prompt_tokens": r.get("prompt_eval_count", 0),
        "gen": gen,
        "tokens": r.get("eval_count", 0),
        "tps": (r.get("eval_count", 0) / gen) if gen else 0,
    }


def descargar():
    post(f"{OLLAMA}/api/generate", {"model": MODELO, "keep_alive": 0})


ACTIVIDADES = [
    "Cine al aire libre en Coyoacán",
    "Picnic en el Bosque de Chapultepec",
    "Clase de cocina italiana en casa",
    "Paseo en trajinera por Xochimilco",
    "Noche de juegos de mesa",
]

PROMPT_A = (
    "Eres el asistente de citas de una pareja. Para cada actividad, escribe una sola frase cálida "
    "en español (máximo 25 palabras) que invite a la pareja a hacerla juntos. Responde una frase "
    "por línea, numeradas.\n\n" + "\n".join(f"{i}. {a}" for i, a in enumerate(ACTIVIDADES, 1))
)


def candidatas(n=30):
    r = post(f"{QDRANT}/collections/nexus_catalog_active/points/scroll",
             {"limit": n, "with_payload": True, "with_vector": False}, timeout=30)
    return [p["payload"] for p in r["result"]["points"]]


def prompt_b(cands):
    lineas = [
        f"- id {c.get('activity_id')}: dimensión {c.get('dimension')}, lugar {c.get('place_name')} "
        f"({c.get('borough')}), {c.get('duration_min')} min, ${c.get('cost_mxn_pp')} por persona, "
        f"momento {', '.join(c.get('dayparts', []))}, {'al aire libre' if c.get('is_outdoor') else 'bajo techo'}"
        for c in cands
    ]
    return (
        "Eres el agente de recomendaciones de Nexus, una app para parejas. La pareja tiene libre el "
        "lunes de 19:00 a 20:00, su ánimo de la semana es tranquilo y su presupuesto es de $1,500 por "
        "persona. De estas 30 candidatas elige las 5 que mejor les queden y escribe para cada una una "
        "frase cálida en español (máximo 25 palabras) que explique por qué.\n\n"
        + "\n".join(lineas)
        + '\n\nResponde solo en JSON: {"picks": [{"id": <id>, "frase": "<frase>"}]}'
    )


def fila(nombre, m):
    return (f"| {nombre} | {m['total']:.1f} | {m['carga']:.1f} | {m['prompt_tokens']} / {m['prompt_s']:.1f} | "
            f"{m['tokens']} / {m['gen']:.1f} | {m['tps']:.2f} |")


def main():
    hoy = dt.datetime.now()
    out = [f"# Mistral 7B solo — {hoy:%Y-%m-%d %H:%M}", "",
           f"Equipo: {platform.processor() or platform.machine()} · modelo `{MODELO}` · "
           f"opciones {OPCIONES}.", "",
           "| Corrida | Total (s) | Carga del modelo (s) | Prompt: tokens / s | Generación: tokens / s | Tokens por segundo |",
           "|---|---|---|---|---|---|"]
    print("Descargando el modelo para la corrida en frio...")
    descargar()
    for nombre in ("A · Modelo sin cargar", "A · Modelo cargado 1", "A · Modelo cargado 2"):
        m = generar(PROMPT_A)
        out.append(fila(nombre, m))
        print(nombre, f"{m['total']:.1f} s")
    cands = candidatas()
    for nombre in ("B · Tamaño real del agente (cargado) 1", "B · Tamaño real del agente (cargado) 2"):
        m = generar(prompt_b(cands), formato="json")
        out.append(fila(nombre, m))
        print(nombre, f"{m['total']:.1f} s")
    salida = f"scripts/resultados-mistral-{hoy:%Y-%m-%d}.md"
    with open(salida, "w", encoding="utf-8") as f:
        f.write("\n".join(out) + "\n")
    print("Resultados en", salida)


if __name__ == "__main__":
    main()
