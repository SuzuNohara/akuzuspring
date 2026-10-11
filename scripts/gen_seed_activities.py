"""Genera seed_activities_synthetic.sql a partir del catalogo sintetico de nexussync."""
import csv
import re
import sys

SRC = r"C:\Users\akari\Nexus\nexussync\fixtures\catalog-synthetic\activities.csv"
DST = r"C:\Users\akari\Nexus\Nexus-backend-incremento2\src\main\resources\db\seed_activities_synthetic.sql"

COLUMNS = [
    "slug", "title", "activity_type", "location_scope", "place_types", "dayparts", "seasons",
    "duration_min", "duration_avg", "duration_max", "difficulty_physical", "difficulty_mental",
    "collaboration", "preparation", "requires_booking", "equipment", "cost_mxn_pp", "price_band",
    "is_outdoor", "weather_ok", "ambience", "description", "rationale", "enriched_by",
    "confidence", "reviewed_by", "needs_review",
]
NUMERIC = {
    "duration_min", "duration_avg", "duration_max", "difficulty_physical", "difficulty_mental",
    "collaboration", "preparation", "requires_booking", "cost_mxn_pp", "is_outdoor",
    "confidence", "needs_review",
}


# El catalogo de origen viene sin acentos. Se restituyen solo en los textos que ve la persona;
# el slug (llave) no cambia, asi que volver a ejecutar el script actualiza las filas existentes.
DISPLAY_TEXT = {"title", "description", "rationale"}
ACCENTS = {
    "acuatico": "acuático", "antelacion": "antelación", "area": "área", "artesanias": "artesanías",
    "asegurandose": "asegurándose", "basquetbol": "básquetbol", "botanico": "botánico",
    "cafe": "café", "cafes": "cafés", "cafeteria": "cafetería", "campana": "campaña",
    "capitulo": "capítulo", "ceramica": "cerámica", "clasica": "clásica", "clasico": "clásico",
    "clasicos": "clásicos", "contemporaneo": "contemporáneo", "conversacion": "conversación",
    "coordinacion": "coordinación", "coreografia": "coreografía", "decoracion": "decoración",
    "despues": "después", "dia": "día", "dias": "días", "disenada": "diseñada",
    "disenar": "diseñar", "diversion": "diversión", "duo": "dúo", "epoca": "época",
    "espectaculo": "espectáculo", "exposicion": "exposición", "facil": "fácil",
    "fisica": "física", "fisico": "físico", "fotografia": "fotografía",
    "fotografica": "fotográfica", "frias": "frías", "funcion": "función", "galeria": "galería",
    "guia": "guía", "historico": "histórico", "historicos": "históricos", "jardin": "jardín",
    "leccion": "lección", "libreria": "librería", "librerias": "librerías", "linea": "línea",
    "ludica": "lúdica", "manana": "mañana", "maquinas": "máquinas", "maraton": "maratón",
    "mas": "más", "meditacion": "meditación", "menu": "menú", "mexico": "méxico",
    "musica": "música", "pais": "país", "parrafos": "párrafos", "pelicula": "película",
    "peliculas": "películas", "practica": "práctica", "preparacion": "preparación",
    "publica": "pública", "reforestacion": "reforestación", "reservacion": "reservación",
    "rincon": "rincón", "salon": "salón", "sesion": "sesión", "sofa": "sofá",
    "tecnica": "técnica", "telefono": "teléfono", "tematica": "temática", "trafico": "tráfico",
    "transmision": "transmisión", "valentin": "valentín",
}


def accent(text):
    def fix(match):
        word = match.group(0)
        fixed = ACCENTS.get(word.lower())
        if fixed is None:
            return word
        return fixed.capitalize() if word[0].isupper() else fixed
    return re.sub(r"[^\W\d_]+", fix, text)


def sql_value(column, value):
    if column in DISPLAY_TEXT:
        value = accent(value)
    if column in NUMERIC:
        float(value)  # falla ruidosamente si no es numero
        return value
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


rows = list(csv.DictReader(open(SRC, encoding="utf-8-sig")))
lines = [
    "-- seed_activities_synthetic.sql (RF-34) -- GENERADO, no editar a mano.",
    "-- Origen: nexussync/fixtures/catalog-synthetic/activities.csv (" + str(len(rows)) + " actividades",
    "-- sinteticas, enriched_by='synthetic'). Sustituir por el catalogo real (kb/, nexus-IDEAS-01)",
    "-- cuando exista: el codigo no cambia, solo estos datos.",
    "-- Requiere la tabla activities (add_kb_tables.sql). Idempotente: re-ejecutarlo actualiza.",
    "",
    "INSERT INTO `nexus`.`activities` (" + ", ".join(COLUMNS) + ") VALUES",
]
values = []
for row in rows:
    record = dict(row)
    record["slug"] = row["activity_id"]
    values.append("  (" + ", ".join(sql_value(c, record[c]) for c in COLUMNS) + ")")
lines.append(",\n".join(values))
updates = [c for c in COLUMNS if c != "slug"]
lines.append("ON DUPLICATE KEY UPDATE")
lines.append(",\n".join("  " + c + " = VALUES(" + c + ")" for c in updates) + ";")

with open(DST, "w", encoding="utf-8", newline="\n") as fh:
    fh.write("\n".join(lines) + "\n")
print(len(rows), "filas ->", DST)
