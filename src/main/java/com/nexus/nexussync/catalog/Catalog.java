package com.nexus.nexussync.catalog;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The three tables of the catalog, indexed by slug (§3.2).
 *
 * <p>Maps and lists are copied defensively and exposed unmodifiable (deviation D-08).
 *
 * @param activities activities by {@code activity_id}
 * @param places places by {@code place_id}
 * @param links place ids of every linked activity, by {@code activity_id}, in file order
 */
public record Catalog(
    Map<String, Activity> activities, Map<String, Place> places, Map<String, List<String>> links) {

  /**
   * Copies the maps and every list of links.
   *
   * @implNote O(a + p + l) time and space, a = activities, p = places, l = link rows.
   */
  public Catalog {
    activities = Map.copyOf(activities);
    places = Map.copyOf(places);
    links =
        links.entrySet().stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
  }
}
