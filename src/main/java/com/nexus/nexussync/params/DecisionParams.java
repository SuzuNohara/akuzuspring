package com.nexus.nexussync.params;

import java.util.List;

/**
 * Parameters of the couple decision (unit U8).
 *
 * <p>{@code rankPoints} is a {@code List<Integer>} instead of the {@code int[]} of the design so
 * the record keeps value equality and stays immutable (deviation D-07).
 *
 * @param rankPoints points granted to the first, second and third ranked activity
 * @param moreOptionsBatch activities served per "more options" request
 * @param decisionTtlHours hours before a pending decision expires
 * @param expiredWeight weight of an expired decision in the learning step
 * @param partialWeight weight of a partial decision in the learning step
 * @param rejectBlockDays days a rejected activity is blocked
 */
public record DecisionParams(
    List<Integer> rankPoints,
    int moreOptionsBatch,
    int decisionTtlHours,
    double expiredWeight,
    double partialWeight,
    int rejectBlockDays) {

  /**
   * Copies the rank points defensively so the record is immutable.
   *
   * @implNote O(k) time and space, k = number of rank points.
   */
  public DecisionParams {
    rankPoints = List.copyOf(rankPoints);
  }
}
