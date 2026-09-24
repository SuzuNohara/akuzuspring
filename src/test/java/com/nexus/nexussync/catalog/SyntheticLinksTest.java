package com.nexus.nexussync.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests de {@link SyntheticLinks}: generador determinista de {@code activity_places.csv}. */
class SyntheticLinksTest {

  private static final int PARKS = 12;
  private static final int MUSEUMS = 2;

  private static Activity activity(String id, LocationScope scope, Set<String> placeTypes) {
    return new Activity(
        id,
        id,
        "OTHER",
        scope,
        placeTypes,
        Set.of("juegos"),
        Set.of(Daypart.AFTERNOON),
        Set.of("ANY"),
        60,
        90,
        120,
        1,
        1,
        5,
        1,
        0,
        "FREE",
        false,
        Set.of("ANY"),
        Set.of("SOCIAL"),
        id);
  }

  private static Place place(String id, String type) {
    return new Place(id, id, type, 19.4, -99.1, "Tlalpan", Optional.empty(), true, false);
  }

  /**
   * 12 parks + 2 museums, one CITY activity per type and one HOME activity; insertion order mixed.
   */
  private static Catalog catalog() {
    Map<String, Place> places = new LinkedHashMap<>();
    for (int i = PARKS; i >= 1; i--) {
      places.put("parque-" + i, place("parque-" + i, "PARK"));
    }
    for (int i = 1; i <= MUSEUMS; i++) {
      places.put("museo-" + i, place("museo-" + i, "MUSEUM"));
    }
    Map<String, Activity> activities = new LinkedHashMap<>();
    activities.put("z-picnic", activity("z-picnic", LocationScope.CITY, Set.of("PARK")));
    activities.put("rompecabezas", activity("rompecabezas", LocationScope.HOME, Set.of()));
    activities.put("a-museo", activity("a-museo", LocationScope.CITY, Set.of("MUSEUM")));
    return new Catalog(activities, places, Map.of());
  }

  private static Map<String, List<String>> parse(String csv) {
    List<String> lines = csv.lines().toList();
    assertThat(lines.get(0)).isEqualTo(SyntheticLinks.HEADER);
    Map<String, List<String>> out = new LinkedHashMap<>();
    for (String line : lines.subList(1, lines.size())) {
      String[] cells = line.split(",", -1);
      assertThat(cells).as("row %s", line).hasSize(5);
      assertThat(cells[2]).isEmpty();
      assertThat(cells[3]).isEmpty();
      assertThat(cells[4]).isEqualTo("synthetic");
      out.computeIfAbsent(cells[0], key -> new ArrayList<>()).add(cells[1]);
    }
    return out;
  }

  // U2-11
  @Test
  void given_same_seed_when_generated_twice_then_output_is_byte_for_byte_identical() {
    Catalog catalog = catalog();

    String first = SyntheticLinks.generate(catalog, new Random(SyntheticLinks.SEED));
    String second = SyntheticLinks.generate(catalog, new Random(SyntheticLinks.SEED));

    assertThat(first.getBytes(StandardCharsets.UTF_8))
        .isEqualTo(second.getBytes(StandardCharsets.UTF_8));
    assertThat(first).endsWith("\n").doesNotContain("\r");
  }

  // U2-11
  @Test
  void given_catalog_when_generated_then_only_city_activities_get_places_of_their_types() {
    Map<String, List<String>> links =
        parse(SyntheticLinks.generate(catalog(), new Random(SyntheticLinks.SEED)));

    assertThat(List.copyOf(links.keySet())).containsExactly("a-museo", "z-picnic");
    assertThat(links.get("z-picnic"))
        .hasSizeBetween(SyntheticLinks.MIN_LINKS, SyntheticLinks.MAX_LINKS)
        .doesNotHaveDuplicates()
        .isSorted()
        .allMatch(id -> id.startsWith("parque-"));
    assertThat(links.get("a-museo")).containsExactly("museo-1", "museo-2");
  }

  // U2-11
  @Test
  void given_different_seeds_when_generated_then_at_least_one_output_differs() {
    Catalog catalog = catalog();
    String base = SyntheticLinks.generate(catalog, new Random(SyntheticLinks.SEED));

    List<String> others = new ArrayList<>();
    for (long seed = 1; seed <= 5; seed++) {
      others.add(SyntheticLinks.generate(catalog, new Random(seed)));
    }

    assertThat(others).anySatisfy(other -> assertThat(other).isNotEqualTo(base));
  }

  // U2-11
  @Test
  void given_catalog_without_city_activities_when_generated_then_only_header() {
    Map<String, Activity> activities =
        Map.of("rompecabezas", activity("rompecabezas", LocationScope.HOME, Set.of()));
    Catalog catalog = new Catalog(activities, Map.of(), Map.of());

    String csv = SyntheticLinks.generate(catalog, new Random(SyntheticLinks.SEED));

    assertThat(csv).isEqualTo(SyntheticLinks.HEADER + "\n");
  }

  // U2-11
  @Test
  void given_fixture_dir_when_main_runs_then_links_file_matches_generate(@TempDir Path dir)
      throws Exception {
    Files.copy(
        CatalogLoaderTest.resource("activities-min.csv"),
        dir.resolve(SyntheticLinks.ACTIVITIES_FILE));
    Files.copy(
        CatalogLoaderTest.resource("places-min.csv"), dir.resolve(SyntheticLinks.PLACES_FILE));

    SyntheticLinks.main(new String[] {dir.toString()});

    Path links = dir.resolve(SyntheticLinks.LINKS_FILE);
    Catalog catalog = CatalogLoader.load(dir, dir.resolve(SyntheticLinks.PLACES_FILE));
    assertThat(Files.readString(links, StandardCharsets.UTF_8))
        .isEqualTo(SyntheticLinks.generate(catalog, new Random(SyntheticLinks.SEED)));
    assertThat(catalog.links().get("picnic-en-el-parque"))
        .containsExactly("parque-dos", "parque-tres", "parque-uno");
    assertThat(catalog.links().get("visita-a-museo")).containsExactly("museo-uno");
    assertThat(catalog.links()).doesNotContainKey("rompecabezas");
  }

  // U2-11
  @Test
  void given_wrong_argument_count_when_main_runs_then_illegal_argument() {
    assertThatThrownBy(() -> SyntheticLinks.main(new String[0]))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fixtureDir");
  }
}
