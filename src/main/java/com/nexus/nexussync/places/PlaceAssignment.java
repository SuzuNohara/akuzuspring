package com.nexus.nexussync.places;

import com.nexus.nexussync.params.PlaceRelax;
import java.util.List;

/**
 * Places proposed for an activity (§3.9).
 *
 * @param activityId slug of the activity
 * @param status outcome of the assignment
 * @param options ordered options, at most {@code placesK}; empty unless {@code OK}
 * @param relaxations relaxations applied, in order
 */
public record PlaceAssignment(
    String activityId,
    PlaceStatus status,
    List<PlaceOption> options,
    List<PlaceRelax> relaxations) {

  /**
   * Copies the lists so the record is immutable.
   *
   * @implNote O(o + r) time and space, o = options, r = relaxations.
   */
  public PlaceAssignment {
    options = List.copyOf(options);
    relaxations = List.copyOf(relaxations);
  }
}
