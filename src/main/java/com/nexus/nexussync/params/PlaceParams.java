package com.nexus.nexussync.params;

import java.util.List;

/**
 * Parameters of the place assignment (unit U9).
 *
 * @param radiusKm search radius around the midpoint in kilometres
 * @param placesK number of place options returned
 * @param relaxOrder order in which place constraints are relaxed
 * @param requireVerified whether only verified places are eligible
 */
public record PlaceParams(
    double radiusKm, int placesK, List<PlaceRelax> relaxOrder, boolean requireVerified) {

  /**
   * Copies the relaxation order defensively so the record is immutable.
   *
   * @implNote O(r) time and space, r = relaxation steps.
   */
  public PlaceParams {
    relaxOrder = List.copyOf(relaxOrder);
  }
}
