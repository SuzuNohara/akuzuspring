"""Pruebas de gen_seed_kb_places.py ([kb-lugares] U1, U2, U4).

Uso (desde la raiz del repo): python -I -m unittest discover -s scripts -p "test_*.py"
"""

import re
import tempfile
import unittest
from pathlib import Path
from typing import Final

import gen_seed_kb_places as gen

KB_FINAL: Final = Path(__file__).resolve().parents[2] / "nexus-agent" / "data" / "kb-final"
CORRECTIONS: Final = Path(__file__).resolve().parent / "kb_corrections.csv"

CSV_HEADER: Final = (
    "place_id,name,osm_type,osm_id,family,place_type,lat,lon,address,neighborhood,borough,"
    "opening_hours,website,phone,is_outdoor,weather_ok,ambience,description,source,license,"
    "verified_by,verified_at,needs_review"
)


def place_row(slug: str, name: str = "Lugar", place_type: str = "PARK", **extra: str) -> str:
    values = {
        "place_id": slug, "name": name, "osm_type": "way", "osm_id": "123", "family": "PARQUES",
        "place_type": place_type, "lat": "19.400000", "lon": "-99.100000", "address": "",
        "neighborhood": "Centro", "borough": "Cuauhtémoc", "opening_hours": "", "website": "",
        "phone": "", "is_outdoor": "1", "weather_ok": "ANY", "ambience": "QUIET",
        "description": "Un parque.", "source": "osm", "license": "ODbL", "verified_by": "",
        "verified_at": "", "needs_review": "1",
    }
    values.update(extra)
    return ",".join(csv_quote(values[c]) for c in CSV_HEADER.split(","))


def csv_quote(value: str) -> str:
    return '"' + value.replace('"', '""') + '"' if any(ch in value for ch in ',"\n') else value


class TempFiles(unittest.TestCase):
    def setUp(self) -> None:
        self._dir = tempfile.TemporaryDirectory()
        self.root = Path(self._dir.name)

    def tearDown(self) -> None:
        self._dir.cleanup()

    def write(self, name: str, lines: list[str]) -> Path:
        path = self.root / name
        path.write_text("\n".join(lines) + "\n", encoding="utf-8")
        return path

    def places_csv(self, rows: list[str]) -> Path:
        return self.write("places.csv", [CSV_HEADER, *rows])


class ReadPlacesTest(TempFiles):
    def test_read_places_empty_field_is_none(self) -> None:
        path = self.places_csv([place_row("a-1")])

        places = gen.read_places(path)

        self.assertIsNone(places[0]["address"])
        self.assertIsNone(places[0]["verified_at"])
        self.assertEqual(places[0]["name"], "Lugar")


class SqlStrTest(unittest.TestCase):
    def test_sql_str_escapes_quote_and_backslash_keeping_accents(self) -> None:
        self.assertEqual(gen.sql_str("Fonda D'Alex, Coyoacán"), "'Fonda D''Alex, Coyoacán'")
        self.assertEqual(gen.sql_str("a\\b"), "'a\\\\b'")


class PlacesSqlTest(TempFiles):
    def test_places_sql_writes_every_column_and_score_in_order(self) -> None:
        places = gen.read_places(self.places_csv([place_row(f"a-{i}") for i in range(3)]))

        sql = gen.places_sql(places, {"a-0": 22.19})

        self.assertIn("(" + ", ".join([*gen.DB_COLUMNS, "score"]) + ")", sql)
        self.assertEqual(sql.count("('a-"), 3)
        self.assertIn("'a-0', 'Lugar', 'way', 123, 'PARQUES', 'PARK', 19.400000, -99.100000, NULL,", sql)
        self.assertIn("NULL, NULL, 1, 22.19)", sql)

    def test_places_sql_empty_nullable_is_null_and_missing_score_is_null(self) -> None:
        places = gen.read_places(self.places_csv([place_row("a-1", osm_id="", osm_type="")]))

        sql = gen.places_sql(places, {})

        self.assertIn("('a-1', 'Lugar', NULL, NULL,", sql)
        self.assertIn("NULL, NULL, 1, NULL)", sql)

    def test_places_sql_not_null_text_columns_keep_defaults(self) -> None:
        row = place_row("a-1", weather_ok="", ambience="", description="")
        places = gen.read_places(self.places_csv([row]))

        sql = gen.places_sql(places, {})

        self.assertIn("'ANY', '', '', 'osm'", sql)

    def test_places_sql_splits_in_blocks_of_500(self) -> None:
        places = gen.read_places(self.places_csv([place_row(f"a-{i}") for i in range(1201)]))

        sql = gen.places_sql(places, {})

        blocks = [len(re.findall(r"^  \('a-", b, re.MULTILINE)) for b in sql.split("INSERT INTO")[1:]]
        self.assertEqual(blocks, [500, 500, 201])
        self.assertEqual(sql.count("ON DUPLICATE KEY UPDATE"), 3)


class ReadScoresTest(TempFiles):
    def test_read_scores_maps_place_to_score(self) -> None:
        path = self.write("anchors.csv", [
            "place_id,gmaps_place_id,categories,score,last_review_at,vitality_status,"
            "vitality_checked_at,match_source,match_sim,match_dist_m",
            "a-1,X,AIRE_LIBRE,22.19,2026-10-22,vital,2026-10-23,osm,1.0,83",
        ])

        self.assertEqual(gen.read_scores(path), {"a-1": 22.19})


class HeaderTest(unittest.TestCase):
    def test_places_file_starts_with_names_credits_and_guard(self) -> None:
        sql = gen.places_file([], {})

        self.assertIn("SET NAMES utf8mb4;", sql)
        self.assertIn("colaboradores de OpenStreetMap", sql)
        self.assertIn("ODbL", sql)
        self.assertIn("Fuente: INEGI, DENUE 05_2026", sql)
        self.assertLess(sql.index("SET NAMES"), sql.index("approx-borough"))
        self.assertIn("SIGNAL SQLSTATE '45000'", sql)


class ReadCorrectionsTest(TempFiles):
    HEADER: Final = "place_id,action,value,note"

    def test_read_corrections_rejects_unknown_action_with_line(self) -> None:
        path = self.write("c.csv", [self.HEADER, "a-1,RETYPE,MALL,", "a-1,EXCLUIR,,"])

        with self.assertRaisesRegex(gen.CorrectionError, "línea 3"):
            gen.read_corrections(path, {"a-1"})

    def test_read_corrections_rejects_unknown_place_with_line(self) -> None:
        path = self.write("c.csv", [self.HEADER, "zz,EXCLUDE,,"])

        with self.assertRaisesRegex(gen.CorrectionError, "línea 2"):
            gen.read_corrections(path, {"a-1"})

    def test_read_corrections_requires_value_for_retype_and_link(self) -> None:
        path = self.write("c.csv", [self.HEADER, "a-1,LINK,,"])

        with self.assertRaisesRegex(gen.CorrectionError, "línea 2"):
            gen.read_corrections(path, {"a-1"})


class ApplyCorrectionsTest(TempFiles):
    def setUp(self) -> None:
        super().setUp()
        rows = [place_row("a-1", place_type="OTHER"), place_row("a-2"), place_row("a-3")]
        self.places = gen.read_places(self.places_csv(rows))

    def test_apply_corrections_excludes_place(self) -> None:
        kept, _ = gen.apply_corrections(self.places, [gen.Correction("a-2", "EXCLUDE", "", "")])

        self.assertEqual([p["place_id"] for p in kept], ["a-1", "a-3"])

    def test_apply_corrections_retypes_only_place_type(self) -> None:
        kept, _ = gen.apply_corrections(self.places, [gen.Correction("a-1", "RETYPE", "MALL", "")])

        self.assertEqual(kept[0]["place_type"], "MALL")
        self.assertEqual(kept[0]["family"], self.places[0]["family"])
        self.assertEqual(self.places[0]["place_type"], "OTHER")

    def test_apply_corrections_returns_manual_links(self) -> None:
        corrections = [gen.Correction("a-3", "LINK", "paseo-en-trajinera", "")]

        _, links = gen.apply_corrections(self.places, corrections)

        self.assertEqual(links, [("a-3", "paseo-en-trajinera")])

    def test_apply_corrections_rejects_exclude_and_link_on_same_place(self) -> None:
        corrections = [gen.Correction("a-3", "EXCLUDE", "", ""), gen.Correction("a-3", "LINK", "x", "")]

        with self.assertRaisesRegex(gen.CorrectionError, "a-3"):
            gen.apply_corrections(self.places, corrections)


class LinksSqlTest(unittest.TestCase):
    def test_links_sql_has_type_cross_manual_links_and_reports(self) -> None:
        sql = gen.links_sql([("a-3", "paseo-en-trajinera")])

        self.assertIn("'kb-type'", sql)
        self.assertIn("REPLACE(REPLACE(a.place_types, ' ', ''), '|', ',')", sql)
        self.assertIn("p.source IN ('osm', 'inegi-denue')", sql)
        self.assertIn(
            "SELECT a.id, p.id, 'manual' FROM `nexus`.`activities` a JOIN `nexus`.`places` p "
            "ON p.slug = 'a-3' WHERE a.slug = 'paseo-en-trajinera' "
            "ON DUPLICATE KEY UPDATE source = VALUES(source);", sql)
        self.assertIn("ap.place_id IS NULL", sql)
        self.assertIn("GROUP BY p.place_type", sql)


class MainTest(TempFiles):
    def test_main_writes_places_and_links_files(self) -> None:
        places = self.places_csv([place_row("a-1")])
        anchors = self.write("anchors.csv", ["place_id,score", "a-1,1.5"])
        corrections = self.write("c.csv", ["place_id,action,value,note", "a-1,LINK,remo-en-el-lago,"])
        out = self.root / "seed_places_kb_final.sql"

        code = gen.main([str(places), str(anchors), str(corrections), str(out)])

        self.assertEqual(code, 0)
        self.assertIn("'a-1'", out.read_text(encoding="utf-8"))
        links = (self.root / gen.LINKS_FILE).read_text(encoding="utf-8")
        self.assertIn("'remo-en-el-lago'", links)


@unittest.skipUnless((KB_FINAL / "places.csv").exists(), "kb-final no esta en esta maquina")
class RealCatalogTest(unittest.TestCase):
    def test_real_catalog_unique_slugs_inside_cdmx_with_source(self) -> None:
        places = gen.read_places(KB_FINAL / "places.csv")
        known = {p["place_id"] for p in places if p["place_id"]}
        corrections = gen.read_corrections(CORRECTIONS, known) if CORRECTIONS.exists() else []
        excluded = {c.place_id for c in corrections if c.action == "EXCLUDE"}

        kept, _ = gen.apply_corrections(places, corrections)
        sql = gen.places_sql(kept, gen.read_scores(KB_FINAL / "place_anchors.csv"))

        self.assertEqual(len(kept), 3142 - len(excluded))
        self.assertEqual(len({p["place_id"] for p in kept}), len(kept))
        for p in kept:
            self.assertTrue(19.0 <= float(p["lat"] or 0) <= 19.6, p["place_id"])
            self.assertTrue(-99.4 <= float(p["lon"] or 0) <= -98.9, p["place_id"])
            self.assertTrue(p["source"] and p["license"], p["place_id"])
        self.assertEqual(sql.count("\n  ('"), len(kept))


if __name__ == "__main__":
    unittest.main()
