package com.nexus.nexussync.catalog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Deterministic generator of {@code activity_places.csv} for the synthetic catalog fixture ({@code
 * nexussync/fixtures/catalog-synthetic}, §3.2, tasks T-15/T-16).
 *
 * <p>Every {@code CITY} activity is linked to between {@value #MIN_LINKS} and {@value #MAX_LINKS}
 * real places of {@code kb/places.csv} whose {@code place_type} belongs to the activity's {@code
 * place_types} (fewer only when the catalog has fewer candidates). {@code HOME} activities get no
 * link. {@code cost_override} and {@code notes} are left empty and {@code source} is {@code
 * synthetic}.
 *
 * <p>Reproducibility: activities and candidate places are visited in slug order (never in map
 * iteration order, which changes between JVM runs) and every random draw comes from the {@link
 * Random} the caller passes, so {@code generate(catalog, new Random(7))} yields the same bytes on
 * every machine. {@code java.util.Random} is used on purpose: this is a simulation seed, not
 * cryptography (deviation documented in §3.0 of the plan).
 *
 * <p>Command line (test classpath): {@code SyntheticLinks <fixtureDir>}, where {@code fixtureDir}
 * holds {@code activities.csv} and {@code places.csv} (a symlink to {@code kb/places.csv} in the
 * repository). It writes {@code fixtureDir/activity_places.csv} using {@code new Random(SEED)}. The
 * committed fixture was produced exactly this way; {@code SyntheticFixtureTest} checks that it
 * still matches.
 */
public final class SyntheticLinks {

  /** Seed fixed by the plan for the committed fixture. */
  static final long SEED = 7L;

  /** Minimum links per {@code CITY} activity when enough candidates exist. */
  static final int MIN_LINKS = 3;

  /** Maximum links per {@code CITY} activity. */
  static final int MAX_LINKS = 8;

  /** Header of {@code activity_places.csv} (kb/schema.md §2.2). */
  static final String HEADER = "activity_id,place_id,cost_override,notes,source";

  static final String ACTIVITIES_FILE = "activities.csv";
  static final String PLACES_FILE = "places.csv";
  static final String LINKS_FILE = "activity_places.csv";

  private static final String SOURCE = "synthetic";
  private static final char SEPARATOR = ',';
  private static final char NEWLINE = '\n';

  private SyntheticLinks() {}

  /**
   * Generates {@code activity_places.csv} for the fixture directory given as the only argument.
   *
   * @param args {@code [fixtureDir]}: directory with {@code activities.csv} and {@code places.csv}
   * @throws IOException if the output file cannot be written
   * @throws CatalogException if the input CSV files are invalid
   * @throws IllegalArgumentException if the number of arguments is not one
   * @implNote O(a · p) time, O(a + p + l) space, a = activities, p = places, l = links written.
   */
  public static void main(String[] args) throws IOException, CatalogException {
    if (args.length != 1) {
      throw new IllegalArgumentException("usage: SyntheticLinks <fixtureDir>");
    }
    Path dir = Path.of(args[0]);
    Map<String, Activity> activities = CatalogLoader.loadActivities(dir.resolve(ACTIVITIES_FILE));
    Map<String, Place> places = CatalogLoader.loadPlaces(dir.resolve(PLACES_FILE));
    Catalog catalog = new Catalog(activities, places, Map.of());
    String csv = generate(catalog, new Random(SEED));
    Files.writeString(dir.resolve(LINKS_FILE), csv, StandardCharsets.UTF_8);
  }

  /**
   * Builds the full text of {@code activity_places.csv} for the activities and places of {@code
   * catalog}; its {@code links} are ignored.
   *
   * @param catalog activities and places to link
   * @param rng source of every random draw; the same seed yields the same text
   * @return header plus one {@code activity_id,place_id,,,synthetic} row per link, rows grouped by
   *     activity in slug order and sorted by place slug inside each group, {@code \n} terminated
   * @implNote O(a · p + l log l) time, O(p + l) space, a = activities, p = places, l = links.
   */
  static String generate(Catalog catalog, Random rng) {
    StringBuilder out = new StringBuilder(HEADER).append(NEWLINE);
    List<String> activityIds = new ArrayList<>(catalog.activities().keySet());
    Collections.sort(activityIds);
    for (String activityId : activityIds) {
      Activity activity = catalog.activities().get(activityId);
      if (activity.locationScope() != LocationScope.CITY) {
        continue;
      }
      for (String placeId : pick(candidates(activity, catalog.places()), rng)) {
        out.append(activityId)
            .append(SEPARATOR)
            .append(placeId)
            .append(",,,")
            .append(SOURCE)
            .append(NEWLINE);
      }
    }
    return out.toString();
  }

  /** Place slugs whose type matches the activity, in slug order. */
  private static List<String> candidates(Activity activity, Map<String, Place> places) {
    List<String> out = new ArrayList<>();
    for (Place place : places.values()) {
      if (activity.placeTypes().contains(place.placeType())) {
        out.add(place.placeId());
      }
    }
    Collections.sort(out);
    return out;
  }

  /** Draws the link count, shuffles the candidates with {@code rng} and keeps a sorted prefix. */
  private static List<String> pick(List<String> candidates, Random rng) {
    int wanted = MIN_LINKS + rng.nextInt(MAX_LINKS - MIN_LINKS + 1);
    int count = Math.min(wanted, candidates.size());
    List<String> shuffled = new ArrayList<>(candidates);
    Collections.shuffle(shuffled, rng);
    List<String> chosen = new ArrayList<>(shuffled.subList(0, count));
    Collections.sort(chosen);
    return chosen;
  }
}
