"""Genera seed_boroughs_places.sql: alcaldias y lugares aproximados del catalogo sintetico ([T-06]).

Los lugares del catalogo sintetico (activity_places.csv) no traen coordenadas: el places.csv del que
dependian nunca se subio. Mientras llega el catalogo real de la CDMX (U12), cada lugar toma las
coordenadas del centro de su alcaldia, que viene como prefijo de su slug
(`alvaro-obregon-cinemex-park-plaza`). Quedan marcados source='approx-borough' y needs_review=1.

Uso: python -I gen_seed_places.py <activity_places.csv> <salida.sql>
"""
import csv
import sys

# Centro de cada alcaldia: punto de la relacion administrativa en OpenStreetMap (Nominatim,
# consultado el 2026-10-16). Datos (c) colaboradores de OpenStreetMap, licencia ODbL.
# (codigo, nombre, prefijo del slug, lat, lon, relacion OSM)
BOROUGHS = [
    ("ALVARO_OBREGON", "Álvaro Obregón", "alvaro-obregon", 19.390652, -99.195242, 5606683),
    ("AZCAPOTZALCO", "Azcapotzalco", "azcapotzalco", 19.483891, -99.184394, 5605571),
    ("BENITO_JUAREZ", "Benito Juárez", "benito-juarez", 19.371880, -99.157701, 5605589),
    ("COYOACAN", "Coyoacán", "coyoacan", 19.349861, -99.161975, 5605709),
    ("CUAJIMALPA", "Cuajimalpa de Morelos", "cuajimalpa-de-morelos", 19.357243, -99.299625, 5605714),
    ("CUAUHTEMOC", "Cuauhtémoc", "cuauhtemoc", 19.441471, -99.151732, 5605721),
    ("GUSTAVO_A_MADERO", "Gustavo A. Madero", "gustavo-a-madero", 19.482795, -99.113361, 5605835),
    ("IZTACALCO", "Iztacalco", "iztacalco", 19.395745, -99.097453, 5605904),
    ("IZTAPALAPA", "Iztapalapa", "iztapalapa", 19.358863, -99.092540, 5605905),
    ("MAGDALENA_CONTRERAS", "La Magdalena Contreras", "la-magdalena-contreras", 19.304773, -99.241380, 5605960),
    ("MIGUEL_HIDALGO", "Miguel Hidalgo", "miguel-hidalgo", 19.407116, -99.190617, 5606041),
    ("MILPA_ALTA", "Milpa Alta", "milpa-alta", 19.191609, -99.023376, 5606042),
    ("TLAHUAC", "Tláhuac", "tlahuac", 19.270391, -99.004750, 5606536),
    ("TLALPAN", "Tlalpan", "tlalpan", 19.288060, -99.166973, 5606523),
    ("VENUSTIANO_CARRANZA", "Venustiano Carranza", "venustiano-carranza", 19.419166, -99.113548, 5606591),
    ("XOCHIMILCO", "Xochimilco", "xochimilco", 19.263361, -99.104738, 5606640),
]


def sql_str(value):
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def borough_of(place_slug):
    # El prefijo mas largo primero: ninguno es prefijo de otro hoy, pero asi no depende del orden.
    for borough in sorted(BOROUGHS, key=lambda b: len(b[2]), reverse=True):
        if place_slug.startswith(borough[2] + "-"):
            return borough
    raise ValueError(f"Lugar sin alcaldia reconocible: {place_slug}")


def place_name(place_slug, borough):
    # El slug no conserva acentos; needs_review=1 avisa que el nombre es provisional.
    words = place_slug[len(borough[2]) + 1:].split("-")
    return " ".join(w.capitalize() for w in words)


def main(csv_path, out_path):
    with open(csv_path, encoding="utf-8", newline="") as fh:
        pairs = [(row["activity_id"], row["place_id"]) for row in csv.DictReader(fh)]
    place_slugs = sorted({place for _, place in pairs})

    lines = [
        "-- seed_boroughs_places.sql ([T-06] RN-31) -- GENERADO con scripts/gen_seed_places.py, no editar a mano.",
        "-- Alcaldias: centro de su relacion administrativa en OpenStreetMap (ODbL), consultado 2026-10-16.",
        "-- Lugares: los del catalogo sintetico, con las coordenadas del centro de su alcaldia",
        "-- (source='approx-borough', needs_review=1) hasta que llegue el catalogo real (U12).",
        "-- Requiere add_kb_tables.sql, seed_activities_synthetic.sql y add_recommendation_tables.sql.",
        "-- Idempotente: re-ejecutarlo actualiza.",
        "",
        "INSERT INTO `nexus`.`boroughs` (code, name, lat, lon) VALUES",
    ]
    lines.append(",\n".join(
        f"  ({sql_str(code)}, {sql_str(name)}, {lat:.6f}, {lon:.6f})"
        for code, name, _, lat, lon, _ in BOROUGHS))
    lines[-1] += "\nON DUPLICATE KEY UPDATE name = VALUES(name), lat = VALUES(lat), lon = VALUES(lon);"
    lines += [
        "",
        "INSERT INTO `nexus`.`places` (slug, name, family, place_type, lat, lon, borough, source, license, needs_review) VALUES",
    ]
    rows = []
    for slug in place_slugs:
        borough = borough_of(slug)
        rows.append(
            f"  ({sql_str(slug)}, {sql_str(place_name(slug, borough))}, 'UNKNOWN', 'UNKNOWN', "
            f"{borough[3]:.6f}, {borough[4]:.6f}, {sql_str(borough[1])}, 'approx-borough', 'ODbL', 1)")
    lines.append(",\n".join(rows))
    lines[-1] += ("\nON DUPLICATE KEY UPDATE name = VALUES(name), lat = VALUES(lat), lon = VALUES(lon), "
                  "borough = VALUES(borough), source = VALUES(source), needs_review = VALUES(needs_review);")
    lines += ["", "-- Pares actividad-lugar, resueltos por slug."]
    for activity, place in pairs:
        lines.append(
            "INSERT INTO `nexus`.`activity_places` (activity_id, place_id, source) "
            f"SELECT a.id, p.id, 'synthetic' FROM `nexus`.`activities` a JOIN `nexus`.`places` p "
            f"ON p.slug = {sql_str(place)} WHERE a.slug = {sql_str(activity)} "
            "ON DUPLICATE KEY UPDATE source = VALUES(source);")
    lines += [
        "",
        "-- Comprobacion (D2, D3): 16 alcaldias, 301 lugares aproximados, 315 pares.",
        "SELECT (SELECT COUNT(*) FROM `nexus`.`boroughs`) AS boroughs,",
        "       (SELECT COUNT(*) FROM `nexus`.`places` WHERE source = 'approx-borough') AS places,",
        "       (SELECT COUNT(*) FROM `nexus`.`activity_places` ap JOIN `nexus`.`places` p ON p.id = ap.place_id",
        "         WHERE p.source = 'approx-borough') AS activity_places;",
        "",
    ]
    with open(out_path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("\n".join(lines))
    print(f"{len(BOROUGHS)} alcaldias, {len(place_slugs)} lugares, {len(pairs)} pares -> {out_path}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
