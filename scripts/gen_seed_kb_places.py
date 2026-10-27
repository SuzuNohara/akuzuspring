"""Genera el SQL del catalogo real de lugares ([kb-lugares] U1, U2, U4).

Lee la base final de Suzu (nexus-agent/data/kb-final: places.csv y place_anchors.csv, sin
tocarlos), aplica nuestras correcciones manuales (scripts/kb_corrections.csv) y escribe dos
archivos junto a la salida:
  * seed_places_kb_final.sql: los lugares, con su popularidad (score), por bloques de 500.
  * seed_activity_places_kb_final.sql: enlaces actividad x lugar por tipo y manuales.

Uso: python -I gen_seed_kb_places.py <places.csv> <place_anchors.csv> <kb_corrections.csv> <salida.sql>
"""

import csv
import logging
import sys
from collections.abc import Iterable, Mapping, Sequence
from pathlib import Path
from typing import Final, NamedTuple

log = logging.getLogger("gen_seed_kb_places")

Place = dict[str, str | None]

# Columnas de places.csv, en el orden de `places` (add_kb_tables.sql). `place_id` es nuestro `slug`.
CSV_COLUMNS: Final = (
    "place_id", "name", "osm_type", "osm_id", "family", "place_type", "lat", "lon", "address",
    "neighborhood", "borough", "opening_hours", "website", "phone", "is_outdoor", "weather_ok",
    "ambience", "description", "source", "license", "verified_by", "verified_at", "needs_review",
)
DB_COLUMNS: Final = ("slug", *CSV_COLUMNS[1:])
NUMERIC: Final = frozenset({"osm_id", "lat", "lon", "is_outdoor", "needs_review"})
# Columnas NOT NULL con valor por omision en la tabla: vacio toma ese valor, no NULL.
NOT_NULL_DEFAULTS: Final = {"weather_ok": "ANY", "ambience": "", "description": ""}

BLOCK_SIZE: Final = 500
ACTIONS: Final = frozenset({"EXCLUDE", "RETYPE", "LINK"})
LINKS_FILE: Final = "seed_activity_places_kb_final.sql"

HEADER: Final = """\
-- seed_places_kb_final.sql ([kb-lugares] U1) -- GENERADO con scripts/gen_seed_kb_places.py, no editar a mano.
-- 3 142 lugares reales de la CDMX (base final de Suzu, nexus-agent/data/kb-final, commit da5f3dd)
-- con las correcciones manuales de scripts/kb_corrections.csv. Idempotente: re-ejecutarlo actualiza.
-- Creditos (U10):
--   Datos (c) colaboradores de OpenStreetMap, licencia ODbL (https://www.openstreetmap.org/copyright).
--   Fuente: INEGI, DENUE 05_2026 (Terminos de Libre Uso de la Informacion del INEGI).
-- Requiere add_kb_tables.sql y add_places_score.sql. Correr DESPUES de cleanup_places_approx.sql.

SET NAMES utf8mb4;

-- Guardia: 54 slugs viejos coinciden con los nuevos; si quedan lugares approx-borough, se aborta.
DROP PROCEDURE IF EXISTS `nexus`.`kb_guard`;
DELIMITER //
CREATE PROCEDURE `nexus`.`kb_guard`()
BEGIN
  IF (SELECT COUNT(*) FROM `nexus`.`places` WHERE source = 'approx-borough') > 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Corre antes cleanup_places_approx.sql';
  END IF;
END//
DELIMITER ;
CALL `nexus`.`kb_guard`();
DROP PROCEDURE `nexus`.`kb_guard`;
"""

LINKS_HEADER: Final = """\
-- seed_activity_places_kb_final.sql ([kb-lugares] U4) -- GENERADO con scripts/gen_seed_kb_places.py.
-- Enlaces actividad x lugar: por tipo (place_types de la actividad x place_type del lugar) y
-- manuales (LINK de scripts/kb_corrections.csv). Correr despues de seed_places_kb_final.sql y
-- seed_activities_kb_nuevas.sql. Idempotente.

SET NAMES utf8mb4;

INSERT INTO `nexus`.`activity_places` (activity_id, place_id, source)
SELECT a.id, p.id, 'kb-type' FROM `nexus`.`activities` a
JOIN `nexus`.`places` p ON FIND_IN_SET(p.place_type, REPLACE(REPLACE(a.place_types, ' ', ''), '|', ',')) > 0
WHERE a.location_scope = 'CITY' AND p.source IN ('osm', 'inegi-denue')
ON DUPLICATE KEY UPDATE source = VALUES(source);
"""

LINKS_REPORTS: Final = """\
-- Comprobacion 1: actividades de ciudad sin ningun lugar (esperado: ninguna).
SELECT a.slug FROM `nexus`.`activities` a
LEFT JOIN `nexus`.`activity_places` ap ON ap.activity_id = a.id
WHERE a.location_scope = 'CITY' AND ap.place_id IS NULL;

-- Comprobacion 2: lugares sin ninguna actividad, por tipo.
SELECT p.place_type, COUNT(*) AS lugares FROM `nexus`.`places` p
LEFT JOIN `nexus`.`activity_places` ap ON ap.place_id = p.id
WHERE ap.place_id IS NULL GROUP BY p.place_type ORDER BY lugares DESC;

-- Comprobacion 3: enlaces por actividad (sesgo de restaurantes, R de U4).
SELECT a.slug, COUNT(*) AS lugares FROM `nexus`.`activity_places` ap
JOIN `nexus`.`activities` a ON a.id = ap.activity_id GROUP BY a.slug ORDER BY lugares DESC;
"""


class CorrectionError(ValueError):
    """Correccion manual invalida o contradictoria."""


class Correction(NamedTuple):
    place_id: str
    action: str
    value: str
    note: str


def sql_str(value: str) -> str:
    """Literal de texto de MySQL: escapa la diagonal invertida y la comilla simple."""
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def read_places(path: Path) -> list[Place]:
    """Lugares de places.csv; un campo vacio queda en None.

    Time: O(n) | Space: O(n)
    """
    with path.open(encoding="utf-8", newline="") as fh:
        return [{c: (row[c] or None) for c in CSV_COLUMNS} for row in csv.DictReader(fh)]


def read_scores(path: Path) -> dict[str, float]:
    """Popularidad (score) de cada lugar segun place_anchors.csv.

    Time: O(n) | Space: O(n)
    """
    with path.open(encoding="utf-8", newline="") as fh:
        return {row["place_id"]: float(row["score"]) for row in csv.DictReader(fh) if row["score"]}


def read_corrections(path: Path, known_ids: set[str]) -> list[Correction]:
    """Correcciones manuales; error con el numero de linea si algo no es valido.

    Time: O(n) | Space: O(n)
    """
    corrections = []
    with path.open(encoding="utf-8", newline="") as fh:
        for line, row in enumerate(csv.DictReader(fh), start=2):
            correction = Correction(*(str(row.get(k) or "").strip() for k in Correction._fields))
            _check_correction(correction, known_ids, line)
            corrections.append(correction)
    return corrections


def _check_correction(c: Correction, known_ids: set[str], line: int) -> None:
    if c.action not in ACTIONS:
        raise CorrectionError(f"línea {line}: acción desconocida {c.action!r}")
    if c.place_id not in known_ids:
        raise CorrectionError(f"línea {line}: el lugar {c.place_id!r} no está en places.csv")
    if c.action != "EXCLUDE" and not c.value:
        raise CorrectionError(f"línea {line}: {c.action} necesita un valor")


def apply_corrections(
    places: Sequence[Place], corrections: Iterable[Correction]
) -> tuple[list[Place], list[tuple[str, str]]]:
    """Quita los EXCLUDE, cambia el tipo de los RETYPE y junta los LINK (lugar, actividad).

    No modifica `places`. Time: O(n + c) | Space: O(n + c)
    """
    by_action: dict[str, list[Correction]] = {a: [] for a in ACTIONS}
    for c in corrections:
        by_action[c.action].append(c)
    excluded = {c.place_id for c in by_action["EXCLUDE"]}
    contradictions = sorted(excluded & {c.place_id for c in by_action["LINK"]})
    if contradictions:
        raise CorrectionError(f"EXCLUDE y LINK sobre el mismo lugar: {', '.join(contradictions)}")
    new_type = {c.place_id: c.value for c in by_action["RETYPE"]}
    kept = [
        {**p, "place_type": new_type.get(str(p["place_id"]), p["place_type"])}
        for p in places
        if p["place_id"] not in excluded
    ]
    return kept, [(c.place_id, c.value) for c in by_action["LINK"]]


def _value(column: str, raw: str | None) -> str:
    if raw is None:
        return sql_str(NOT_NULL_DEFAULTS[column]) if column in NOT_NULL_DEFAULTS else "NULL"
    if column in NUMERIC:
        float(raw)  # ValueError si no es numero: no se escribe texto sin comillas en el SQL
        return raw
    return sql_str(raw)


def _row(place: Place, scores: Mapping[str, float]) -> str:
    score = scores.get(str(place["place_id"]))
    values = [_value(c, place[c]) for c in CSV_COLUMNS]
    values.append("NULL" if score is None else repr(score))
    return "  (" + ", ".join(values) + ")"


def places_sql(places: Sequence[Place], scores: Mapping[str, float]) -> str:
    """INSERT por bloques de 500 con upsert por slug.

    Time: O(n) | Space: O(n)
    """
    columns = ", ".join([*DB_COLUMNS, "score"])
    update = ", ".join(f"{c} = VALUES({c})" for c in [*DB_COLUMNS[1:], "score"])
    blocks = []
    for start in range(0, len(places), BLOCK_SIZE):
        rows = ",\n".join(_row(p, scores) for p in places[start:start + BLOCK_SIZE])
        blocks.append(
            f"INSERT INTO `nexus`.`places` ({columns}) VALUES\n{rows}\n"
            f"ON DUPLICATE KEY UPDATE {update};\n"
        )
    return "\n".join(blocks)


def places_file(places: Sequence[Place], scores: Mapping[str, float]) -> str:
    """Archivo completo de lugares: cabecera con creditos y guardia, bloques y comprobacion."""
    check = (
        "-- Comprobacion: total, slugs distintos y lugares sin score (esperado 0).\n"
        "SELECT COUNT(*) AS lugares, COUNT(DISTINCT slug) AS slugs, SUM(score IS NULL) AS sin_score\n"
        "FROM `nexus`.`places`;\n"
    )
    return "\n".join([HEADER, places_sql(places, scores), check])


def links_sql(manual_links: Iterable[tuple[str, str]]) -> str:
    """Enlaces por tipo, un INSERT ... SELECT por enlace manual y las consultas de reporte.

    Time: O(m) | Space: O(m)
    """
    manual = [
        "INSERT INTO `nexus`.`activity_places` (activity_id, place_id, source) "
        "SELECT a.id, p.id, 'manual' FROM `nexus`.`activities` a JOIN `nexus`.`places` p "
        f"ON p.slug = {sql_str(place)} WHERE a.slug = {sql_str(activity)} "
        "ON DUPLICATE KEY UPDATE source = VALUES(source);"
        for place, activity in manual_links
    ]
    return "\n".join([LINKS_HEADER, "-- Enlaces manuales (LINK).", *manual, "", LINKS_REPORTS])


def main(argv: Sequence[str]) -> int:
    """Escribe los dos archivos SQL; devuelve 0 si todo salio bien."""
    places_csv, anchors_csv, corrections_csv, out = (Path(a) for a in argv)
    places = read_places(places_csv)
    known = {str(p["place_id"]) for p in places}
    kept, links = apply_corrections(places, read_corrections(corrections_csv, known))
    out.write_text(places_file(kept, read_scores(anchors_csv)), encoding="utf-8", newline="\n")
    (out.parent / LINKS_FILE).write_text(links_sql(links), encoding="utf-8", newline="\n")
    log.info("%d lugares (%d excluidos), %d enlaces manuales -> %s",
             len(kept), len(places) - len(kept), len(links), out.parent)
    return 0


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(message)s")
    if len(sys.argv) != 5:
        log.error(__doc__)
        sys.exit(2)
    sys.exit(main(sys.argv[1:]))
