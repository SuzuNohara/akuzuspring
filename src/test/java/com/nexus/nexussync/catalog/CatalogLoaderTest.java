package com.nexus.nexussync.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests de {@link CatalogLoader}: lectura tipada por nombre de cabecera de los 3 CSV (§3.2). */
class CatalogLoaderTest {

  private static final String ACTIVITIES_HEADER =
      "activity_id,title,activity_type,location_scope,place_types,interests,dayparts,seasons,"
          + "duration_min,duration_avg,duration_max,difficulty_physical,difficulty_mental,"
          + "collaboration,preparation,requires_booking,equipment,cost_mxn_pp,price_band,"
          + "is_outdoor,weather_ok,ambience,description,rationale,enriched_by,enriched_at,"
          + "confidence,reviewed_by,needs_review";

  private static final String PUZZLE_ROW =
      "rompecabezas,Rompecabezas,PUZZLE,HOME,,juegos|quedarse-en-casa,AFTERNOON|NIGHT,ANY,60,120,"
          + "240,0,3,7,1,0,rompecabezas,0,FREE,0,ANY,HOME|QUIET,Armar un rompecabezas.,Casa.,"
          + "synthetic,2026-09-23,1.0,synthetic,0";

  private static final String PICNIC_ROW =
      "picnic-en-el-parque,Picnic en el parque,PICNIC,CITY,PARK|PICNIC_SITE,picnics,AFTERNOON,ANY,"
          + "90,120,180,1,1,6,3,0,canasta|manta,150,LOW,1,CLOUDY|SUNNY,NATURE|ROMANTIC,"
          + "Comer en el pasto.,Parque.,synthetic,2026-09-23,1.0,synthetic,0";

  static Path resource(String name) throws URISyntaxException {
    URL url = CatalogLoaderTest.class.getResource("/nexussync/catalog/" + name);
    assertThat(url).as("test resource %s", name).isNotNull();
    return Path.of(url.toURI());
  }

  private static Path writeCsv(Path dir, String name, String header, String... rows)
      throws IOException {
    Path csv = dir.resolve(name);
    Files.writeString(csv, header + "\n" + String.join("\n", rows) + "\n", StandardCharsets.UTF_8);
    return csv;
  }

  // ── T-13: activities ─────────────────────────────────────────────────────

  // U2-03
  @Test
  void given_min_csv_when_activities_loaded_then_rows_are_typed_and_indexed_by_id()
      throws Exception {
    Map<String, Activity> activities = CatalogLoader.loadActivities(resource("activities-min.csv"));

    assertThat(activities)
        .containsOnlyKeys("rompecabezas", "picnic-en-el-parque", "visita-a-museo");
    Activity puzzle = activities.get("rompecabezas");
    assertThat(puzzle.title()).isEqualTo("Rompecabezas");
    assertThat(puzzle.activityType()).isEqualTo("PUZZLE");
    assertThat(puzzle.locationScope()).isEqualTo(LocationScope.HOME);
    assertThat(puzzle.placeTypes()).isEmpty();
    assertThat(puzzle.interests()).containsExactlyInAnyOrder("juegos", "quedarse-en-casa");
    assertThat(puzzle.dayparts()).containsExactlyInAnyOrder(Daypart.AFTERNOON, Daypart.NIGHT);
    assertThat(puzzle.seasons()).containsExactly("ANY");
    assertThat(puzzle.durationMin()).isEqualTo(60);
    assertThat(puzzle.durationAvg()).isEqualTo(120);
    assertThat(puzzle.durationMax()).isEqualTo(240);
    assertThat(puzzle.difficultyPhysical()).isZero();
    assertThat(puzzle.difficultyMental()).isEqualTo(3);
    assertThat(puzzle.collaboration()).isEqualTo(7);
    assertThat(puzzle.preparation()).isEqualTo(1);
    assertThat(puzzle.costMxnPp()).isZero();
    assertThat(puzzle.priceBand()).isEqualTo("FREE");
    assertThat(puzzle.outdoor()).isFalse();
    assertThat(puzzle.weatherOk()).containsExactly("ANY");
    assertThat(puzzle.ambience()).containsExactlyInAnyOrder("HOME", "QUIET");
    assertThat(puzzle.description()).isEqualTo("Armar un rompecabezas.");
  }

  // U2-03
  @Test
  void given_min_csv_when_activities_loaded_then_city_row_keeps_place_types_and_quoted_text()
      throws Exception {
    Map<String, Activity> activities = CatalogLoader.loadActivities(resource("activities-min.csv"));

    Activity picnic = activities.get("picnic-en-el-parque");
    assertThat(picnic.locationScope()).isEqualTo(LocationScope.CITY);
    assertThat(picnic.placeTypes()).containsExactlyInAnyOrder("PARK", "PICNIC_SITE");
    assertThat(picnic.outdoor()).isTrue();
    assertThat(picnic.costMxnPp()).isEqualTo(150);
    assertThat(picnic.weatherOk()).containsExactlyInAnyOrder("CLOUDY", "SUNNY");
    assertThat(picnic.description()).isEqualTo("Comer en el pasto, con canasta.");
    assertThat(activities.get("visita-a-museo").dayparts())
        .containsExactlyInAnyOrder(Daypart.MORNING, Daypart.AFTERNOON);
  }

  // U2-04
  @Test
  void given_csv_with_bom_when_activities_loaded_then_same_as_without_bom() throws Exception {
    Map<String, Activity> plain = CatalogLoader.loadActivities(resource("activities-min.csv"));

    Map<String, Activity> withBom = CatalogLoader.loadActivities(resource("activities-bom.csv"));

    assertThat(withBom).isEqualTo(plain);
    assertThat(withBom).containsKey("rompecabezas");
  }

  // U2-05
  @Test
  void given_shuffled_columns_and_extra_column_when_activities_loaded_then_same_as_min()
      throws Exception {
    Map<String, Activity> plain = CatalogLoader.loadActivities(resource("activities-min.csv"));

    Map<String, Activity> shuffled =
        CatalogLoader.loadActivities(resource("activities-shuffled.csv"));

    assertThat(shuffled).isEqualTo(plain);
  }

  // U2-05
  @Test
  void given_missing_required_column_when_activities_loaded_then_exception_names_file_and_column()
      throws Exception {
    Path csv = resource("activities-missing-col.csv");

    assertThatThrownBy(() -> CatalogLoader.loadActivities(csv))
        .isInstanceOf(CatalogException.class)
        .hasMessageContaining("activities-missing-col.csv")
        .hasMessageContaining("row 1")
        .hasMessageContaining("dayparts");
  }

  @Test
  void given_non_numeric_duration_when_activities_loaded_then_exception_names_row_and_column(
      @TempDir Path dir) throws Exception {
    Path csv =
        writeCsv(
            dir,
            "activities.csv",
            ACTIVITIES_HEADER,
            PUZZLE_ROW,
            PICNIC_ROW.replace(",90,120,180,", ",noventa,120,180,"));

    assertThatThrownBy(() -> CatalogLoader.loadActivities(csv))
        .isInstanceOf(CatalogException.class)
        .hasMessageContaining("activities.csv")
        .hasMessageContaining("row 3")
        .hasMessageContaining("duration_min")
        .hasMessageContaining("noventa")
        .hasCauseInstanceOf(NumberFormatException.class);
  }

  @Test
  void given_unknown_location_scope_when_activities_loaded_then_exception_names_column(
      @TempDir Path dir) throws Exception {
    Path csv =
        writeCsv(dir, "activities.csv", ACTIVITIES_HEADER, PUZZLE_ROW.replace(",HOME,", ",MOON,"));

    assertThatThrownBy(() -> CatalogLoader.loadActivities(csv))
        .isInstanceOf(CatalogException.class)
        .hasMessageContaining("row 2")
        .hasMessageContaining("location_scope")
        .hasMessageContaining("MOON")
        .hasMessageContaining("HOME");
  }

  @Test
  void given_unknown_daypart_when_activities_loaded_then_exception_names_column(@TempDir Path dir)
      throws Exception {
    Path csv =
        writeCsv(
            dir,
            "activities.csv",
            ACTIVITIES_HEADER,
            PUZZLE_ROW.replace("AFTERNOON|NIGHT", "AFTERNOON|MIDNIGHT"));

    assertThatThrownBy(() -> CatalogLoader.loadActivities(csv))
        .isInstanceOf(CatalogException.class)
        .hasMessageContaining("row 2")
        .hasMessageContaining("dayparts")
        .hasMessageContaining("MIDNIGHT");
  }

  @Test
  void given_duplicated_activity_id_when_activities_loaded_then_exception(@TempDir Path dir)
      throws Exception {
    Path csv = writeCsv(dir, "activities.csv", ACTIVITIES_HEADER, PUZZLE_ROW, PUZZLE_ROW);

    assertThatThrownBy(() -> CatalogLoader.loadActivities(csv))
        .isInstanceOf(CatalogException.class)
        .hasMessageContaining("row 3")
        .hasMessageContaining("activity_id")
        .hasMessageContaining("rompecabezas");
  }

  @Test
  void given_empty_activity_id_when_activities_loaded_then_exception(@TempDir Path dir)
      throws Exception {
    Path csv =
        writeCsv(
            dir, "activities.csv", ACTIVITIES_HEADER, PUZZLE_ROW.replaceFirst("^rompecabezas", ""));

    assertThatThrownBy(() -> CatalogLoader.loadActivities(csv))
        .isInstanceOf(CatalogException.class)
        .hasMessageContaining("row 2")
        .hasMessageContaining("activity_id");
  }

  @Test
  void given_missing_file_when_activities_loaded_then_exception_with_io_cause(@TempDir Path dir) {
    Path csv = dir.resolve("nope.csv");

    assertThatThrownBy(() -> CatalogLoader.loadActivities(csv))
        .isInstanceOf(CatalogException.class)
        .hasMessageContaining("nope.csv")
        .hasCauseInstanceOf(IOException.class);
  }

  @Test
  void given_only_header_when_activities_loaded_then_empty_map(@TempDir Path dir) throws Exception {
    Path csv = writeCsv(dir, "activities.csv", ACTIVITIES_HEADER);

    Map<String, Activity> activities = CatalogLoader.loadActivities(csv);

    assertThat(activities).isEmpty();
  }

  @Test
  void given_loaded_activities_when_mutated_then_unsupported() throws Exception {
    Map<String, Activity> activities = CatalogLoader.loadActivities(resource("activities-min.csv"));

    assertThatThrownBy(() -> activities.put("x", activities.get("rompecabezas")))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(List.copyOf(activities.keySet()))
        .containsExactly("rompecabezas", "picnic-en-el-parque", "visita-a-museo");
  }

  // ── T-14: places, links and load ─────────────────────────────────────────

  private static final String LINKS_HEADER = "activity_id,place_id,cost_override,notes,source";

  private static Path fixtureDir(Path dir, String... linkRows) throws Exception {
    Files.copy(resource("activities-min.csv"), dir.resolve("activities.csv"));
    writeCsv(dir, "activity_places.csv", LINKS_HEADER, linkRows);
    return dir;
  }

  // U2-06
  @Test
  void given_min_places_csv_when_places_loaded_then_rows_are_typed_and_indexed_by_id()
      throws Exception {
    Map<String, Place> places = CatalogLoader.loadPlaces(resource("places-min.csv"));

    assertThat(List.copyOf(places.keySet()))
        .containsExactly("parque-uno", "parque-dos", "parque-tres", "museo-uno");
    Place parqueUno = places.get("parque-uno");
    assertThat(parqueUno.name()).isEqualTo("Parque Uno");
    assertThat(parqueUno.placeType()).isEqualTo("PARK");
    assertThat(parqueUno.lat()).isEqualTo(19.42);
    assertThat(parqueUno.lon()).isEqualTo(-99.16);
    assertThat(parqueUno.borough()).isEqualTo("Cuauhtémoc");
    assertThat(parqueUno.openingHours()).contains("Mo-Su 06:00-20:00");
    assertThat(parqueUno.outdoor()).isTrue();
    assertThat(parqueUno.verified()).isTrue();
  }

  // U2-06
  @Test
  void given_min_places_csv_when_places_loaded_then_blank_hours_and_verified_by_are_empty()
      throws Exception {
    Map<String, Place> places = CatalogLoader.loadPlaces(resource("places-min.csv"));

    Place parqueDos = places.get("parque-dos");
    assertThat(parqueDos.name()).isEqualTo("Parque Dos, el chico");
    assertThat(parqueDos.openingHours()).isEmpty();
    assertThat(parqueDos.verified()).isFalse();
    Place museo = places.get("museo-uno");
    assertThat(museo.placeType()).isEqualTo("MUSEUM");
    assertThat(museo.outdoor()).isFalse();
    assertThat(museo.openingHours()).contains("Tu-Su 10:00-18:00");
    assertThatThrownBy(() -> places.put("x", museo))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  // U2-07
  @Test
  void given_non_numeric_lat_when_places_loaded_then_exception_names_row_and_column()
      throws Exception {
    Path csv = resource("places-bad-lat.csv");

    assertThatThrownBy(() -> CatalogLoader.loadPlaces(csv))
        .isInstanceOf(CatalogException.class)
        .hasMessageContaining("places-bad-lat.csv")
        .hasMessageContaining("row 3")
        .hasMessageContaining("lat")
        .hasMessageContaining("diecinueve")
        .hasCauseInstanceOf(NumberFormatException.class);
  }

  // U2-07
  @Test
  void given_places_csv_without_place_type_when_places_loaded_then_exception_names_column(
      @TempDir Path dir) throws Exception {
    Path csv =
        writeCsv(
            dir,
            "places.csv",
            "place_id,name,opening_hours,lat,lon,borough",
            "parque-uno,Parque,,19.4,-99.1,Tlalpan");

    assertThatThrownBy(() -> CatalogLoader.loadPlaces(csv))
        .isInstanceOf(CatalogException.class)
        .hasMessageContaining("row 1")
        .hasMessageContaining("place_type");
  }

  // U2-08
  @Test
  void given_link_to_unknown_place_when_links_loaded_then_exception_names_row_and_column()
      throws Exception {
    Map<String, Activity> activities = CatalogLoader.loadActivities(resource("activities-min.csv"));
    Map<String, Place> places = CatalogLoader.loadPlaces(resource("places-min.csv"));
    Path csv = resource("links-orphan-place.csv");

    assertThatThrownBy(() -> CatalogLoader.loadLinks(csv, activities, places))
        .isInstanceOf(CatalogException.class)
        .hasMessageContaining("links-orphan-place.csv")
        .hasMessageContaining("row 3")
        .hasMessageContaining("place_id")
        .hasMessageContaining("parque-fantasma");
  }

  // U2-08
  @Test
  void given_link_to_unknown_activity_when_links_loaded_then_exception_names_row_and_column()
      throws Exception {
    Map<String, Activity> activities = CatalogLoader.loadActivities(resource("activities-min.csv"));
    Map<String, Place> places = CatalogLoader.loadPlaces(resource("places-min.csv"));
    Path csv = resource("links-orphan-activity.csv");

    assertThatThrownBy(() -> CatalogLoader.loadLinks(csv, activities, places))
        .isInstanceOf(CatalogException.class)
        .hasMessageContaining("links-orphan-activity.csv")
        .hasMessageContaining("row 2")
        .hasMessageContaining("activity_id")
        .hasMessageContaining("actividad-fantasma");
  }

  // U2-08
  @Test
  void given_duplicated_pair_when_links_loaded_then_exception_names_row(@TempDir Path dir)
      throws Exception {
    Map<String, Activity> activities = CatalogLoader.loadActivities(resource("activities-min.csv"));
    Map<String, Place> places = CatalogLoader.loadPlaces(resource("places-min.csv"));
    Path csv =
        writeCsv(
            dir,
            "activity_places.csv",
            LINKS_HEADER,
            "picnic-en-el-parque,parque-uno,,,synthetic",
            "picnic-en-el-parque,parque-dos,50,,synthetic",
            "picnic-en-el-parque,parque-uno,,,synthetic");

    assertThatThrownBy(() -> CatalogLoader.loadLinks(csv, activities, places))
        .isInstanceOf(CatalogException.class)
        .hasMessageContaining("row 4")
        .hasMessageContaining("place_id")
        .hasMessageContaining("parque-uno");
  }

  // U2-08
  @Test
  void given_non_numeric_cost_override_when_links_loaded_then_exception_names_column(
      @TempDir Path dir) throws Exception {
    Map<String, Activity> activities = CatalogLoader.loadActivities(resource("activities-min.csv"));
    Map<String, Place> places = CatalogLoader.loadPlaces(resource("places-min.csv"));
    Path csv =
        writeCsv(
            dir,
            "activity_places.csv",
            LINKS_HEADER,
            "picnic-en-el-parque,parque-uno,gratis,,synthetic");

    assertThatThrownBy(() -> CatalogLoader.loadLinks(csv, activities, places))
        .isInstanceOf(CatalogException.class)
        .hasMessageContaining("row 2")
        .hasMessageContaining("cost_override")
        .hasMessageContaining("gratis");
  }

  // U2-09
  @Test
  void given_valid_links_when_links_loaded_then_place_ids_grouped_by_activity_in_file_order(
      @TempDir Path dir) throws Exception {
    Map<String, Activity> activities = CatalogLoader.loadActivities(resource("activities-min.csv"));
    Map<String, Place> places = CatalogLoader.loadPlaces(resource("places-min.csv"));
    Path csv =
        writeCsv(
            dir,
            "activity_places.csv",
            LINKS_HEADER,
            "picnic-en-el-parque,parque-tres,,,synthetic",
            "visita-a-museo,museo-uno,120,con guia,synthetic",
            "picnic-en-el-parque,parque-uno,,,synthetic");

    Map<String, List<String>> links = CatalogLoader.loadLinks(csv, activities, places);

    assertThat(List.copyOf(links.keySet()))
        .containsExactly("picnic-en-el-parque", "visita-a-museo");
    assertThat(links.get("picnic-en-el-parque")).containsExactly("parque-tres", "parque-uno");
    assertThat(links.get("visita-a-museo")).containsExactly("museo-uno");
    assertThat(links).doesNotContainKey("rompecabezas");
    assertThatThrownBy(() -> links.get("visita-a-museo").add("x"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  // U2-09
  @Test
  void given_cost_override_column_when_links_read_then_optional_int(@TempDir Path dir)
      throws Exception {
    Path csv =
        writeCsv(
            dir,
            "activity_places.csv",
            LINKS_HEADER,
            "picnic-en-el-parque,parque-uno,,,synthetic",
            "visita-a-museo,museo-uno,120,,synthetic");

    List<Link> links = CatalogLoader.readLinks(csv);

    assertThat(links)
        .containsExactly(
            new Link("picnic-en-el-parque", "parque-uno", OptionalInt.empty()),
            new Link("visita-a-museo", "museo-uno", OptionalInt.of(120)));
  }

  // U2-09
  @Test
  void given_catalog_dir_and_places_csv_when_loaded_then_catalog_has_the_three_tables(
      @TempDir Path dir) throws Exception {
    Path catalogDir =
        fixtureDir(
            dir,
            "picnic-en-el-parque,parque-uno,,,synthetic",
            "picnic-en-el-parque,parque-dos,,,synthetic",
            "visita-a-museo,museo-uno,,,synthetic");

    Catalog catalog = CatalogLoader.load(catalogDir, resource("places-min.csv"));

    assertThat(catalog.activities())
        .containsOnlyKeys("rompecabezas", "picnic-en-el-parque", "visita-a-museo");
    assertThat(catalog.places())
        .containsOnlyKeys("parque-uno", "parque-dos", "parque-tres", "museo-uno");
    assertThat(catalog.links()).containsOnlyKeys("picnic-en-el-parque", "visita-a-museo");
    assertThat(catalog.links().get("picnic-en-el-parque"))
        .containsExactly("parque-uno", "parque-dos");
  }

  // U2-09
  @Test
  void given_catalog_dir_with_only_link_header_when_loaded_then_no_links(@TempDir Path dir)
      throws Exception {
    Path catalogDir = fixtureDir(dir);

    Catalog catalog = CatalogLoader.load(catalogDir, resource("places-min.csv"));

    assertThat(catalog.links()).isEmpty();
    assertThat(catalog.activities()).hasSize(3);
  }

  // U2-09
  @Test
  void given_catalog_dir_without_links_file_when_loaded_then_exception_names_file(@TempDir Path dir)
      throws Exception {
    Files.copy(resource("activities-min.csv"), dir.resolve("activities.csv"));

    assertThatThrownBy(() -> CatalogLoader.load(dir, resource("places-min.csv")))
        .isInstanceOf(CatalogException.class)
        .hasMessageContaining("activity_places.csv")
        .hasCauseInstanceOf(IOException.class);
  }

  // U2-09
  @Test
  void given_catalog_dir_with_dangling_link_when_loaded_then_exception(@TempDir Path dir)
      throws Exception {
    Path catalogDir = fixtureDir(dir, "picnic-en-el-parque,parque-fantasma,,,synthetic");

    assertThatThrownBy(() -> CatalogLoader.load(catalogDir, resource("places-min.csv")))
        .isInstanceOf(CatalogException.class)
        .hasMessageContaining("activity_places.csv")
        .hasMessageContaining("row 2")
        .hasMessageContaining("parque-fantasma");
  }
}
