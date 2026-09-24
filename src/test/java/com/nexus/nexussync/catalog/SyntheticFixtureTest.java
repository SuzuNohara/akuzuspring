package com.nexus.nexussync.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests del fixture {@code nexussync/fixtures/catalog-synthetic} (U2-01): se leen desde {@code
 * -Dnexussync.dir} y se saltan si el directorio no existe.
 */
class SyntheticFixtureTest {

  private static final int ACTIVITIES = 80;
  private static final int HOME_ACTIVITIES = 24;
  private static final int CITY_ACTIVITIES = 56;
  private static final int PLACES = 3014;
  private static final Set<String> ACTIVITY_TYPES =
      Set.of(
          "WALK",
          "PICNIC",
          "EXHIBITION_VISIT",
          "GUIDED_TOUR",
          "SHOW",
          "FILM",
          "CONCERT",
          "WORKSHOP",
          "COURSE",
          "SPORT_SESSION",
          "SWIM",
          "CLIMB",
          "CYCLE",
          "HIKE",
          "BOAT_RIDE",
          "DINNER",
          "COFFEE",
          "TASTING",
          "COOKING",
          "SHOPPING",
          "BOARD_GAME",
          "PUZZLE",
          "VIDEOGAME",
          "READING",
          "MEDITATION",
          "VOLUNTEERING",
          "PHOTO_WALK",
          "STARGAZING",
          "CAMPING",
          "OTHER");

  private static Path fixtureDir;
  private static Catalog catalog;

  @BeforeAll
  static void loadFixture() throws CatalogException {
    String dir = System.getProperty("nexussync.dir");
    Assumptions.assumeTrue(dir != null && !dir.isBlank(), "nexussync.dir not set");
    fixtureDir = Path.of(dir).resolve("fixtures").resolve("catalog-synthetic");
    Assumptions.assumeTrue(Files.isDirectory(fixtureDir), "fixture dir missing: " + fixtureDir);
    catalog = CatalogLoader.load(fixtureDir, fixtureDir.resolve(SyntheticLinks.PLACES_FILE));
  }

  private static long count(LocationScope scope) {
    return catalog.activities().values().stream()
        .filter(activity -> activity.locationScope() == scope)
        .count();
  }

  // U2-01
  @Test
  void given_fixture_when_loaded_then_80_activities_24_home_56_city_and_all_types() {
    assertThat(catalog.activities()).hasSize(ACTIVITIES);
    assertThat(count(LocationScope.HOME)).isEqualTo(HOME_ACTIVITIES);
    assertThat(count(LocationScope.CITY)).isEqualTo(CITY_ACTIVITIES);
    assertThat(catalog.activities().values())
        .extracting(Activity::activityType)
        .containsAll(ACTIVITY_TYPES);
    assertThat(catalog.activities().values())
        .allSatisfy(activity -> assertThat(ACTIVITY_TYPES).contains(activity.activityType()));
  }

  // U2-01
  @Test
  void given_fixture_when_loaded_then_places_are_the_real_kb_places() {
    assertThat(catalog.places()).hasSize(PLACES);
    Set<String> placeTypes =
        catalog.places().values().stream().map(Place::placeType).collect(Collectors.toSet());
    for (Activity activity : catalog.activities().values()) {
      if (activity.locationScope() == LocationScope.CITY) {
        assertThat(placeTypes)
            .as(activity.activityId())
            .containsAnyElementsOf(activity.placeTypes());
      }
    }
  }

  // U2-01
  @Test
  void given_fixture_when_loaded_then_every_city_activity_has_3_to_8_places_of_its_types() {
    for (Activity activity : catalog.activities().values()) {
      List<String> links = catalog.links().getOrDefault(activity.activityId(), List.of());
      if (activity.locationScope() == LocationScope.HOME) {
        assertThat(links).as(activity.activityId()).isEmpty();
        continue;
      }
      assertThat(links)
          .as(activity.activityId())
          .hasSizeBetween(SyntheticLinks.MIN_LINKS, SyntheticLinks.MAX_LINKS)
          .doesNotHaveDuplicates()
          .allSatisfy(
              placeId ->
                  assertThat(activity.placeTypes())
                      .contains(catalog.places().get(placeId).placeType()));
    }
    assertThat(catalog.links()).hasSize(CITY_ACTIVITIES);
  }

  // U2-01
  @Test
  void given_fixture_when_regenerated_with_seed_7_then_links_file_is_identical() throws Exception {
    String committed =
        Files.readString(fixtureDir.resolve(SyntheticLinks.LINKS_FILE), StandardCharsets.UTF_8);

    String regenerated = SyntheticLinks.generate(catalog, new Random(SyntheticLinks.SEED));

    assertThat(committed).isEqualTo(regenerated);
  }

  // U2-01
  @Test
  void given_fixture_when_loaded_then_synthetic_rows_are_reviewed_and_consistent() {
    for (Activity activity : catalog.activities().values()) {
      assertThat(activity.interests()).as(activity.activityId()).isNotEmpty();
      assertThat(activity.dayparts()).as(activity.activityId()).isNotEmpty();
      assertThat(activity.durationMin())
          .as(activity.activityId())
          .isPositive()
          .isLessThanOrEqualTo(activity.durationAvg());
      assertThat(activity.durationAvg()).isLessThanOrEqualTo(activity.durationMax());
      assertThat(activity.costMxnPp()).isNotNegative();
      assertThat(activity.placeTypes().isEmpty())
          .as(activity.activityId())
          .isEqualTo(activity.locationScope() == LocationScope.HOME);
    }
    Map<String, Long> byType =
        catalog.activities().values().stream()
            .collect(Collectors.groupingBy(Activity::activityType, Collectors.counting()));
    assertThat(byType).hasSize(ACTIVITY_TYPES.size());
  }
}
