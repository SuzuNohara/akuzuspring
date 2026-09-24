package com.nexus.nexussync.decision;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Outcome of the couple decision over the final options (unit U8).
 *
 * @param chosen activity chosen by points (by the seeded random draw on a tie)
 * @param scores points of every final id, in the order of the final list (0 if nobody ranked it)
 * @param rankA ranking given by person A, best first
 * @param rankB ranking given by person B, best first
 * @param tie whether more than one id shared the maximum score
 * @param tieCandidates ids tied at the maximum, sorted lexicographically; empty without a tie
 */
public record Decision(
    String chosen,
    Map<String, Integer> scores,
    List<String> rankA,
    List<String> rankB,
    boolean tie,
    List<String> tieCandidates) {

  /**
   * Validates the components and copies the collections so the record is immutable.
   *
   * @implNote O(n) time and space, n = number of final ids.
   */
  public Decision {
    Objects.requireNonNull(chosen, "chosen");
    scores = Collections.unmodifiableMap(new LinkedHashMap<>(scores));
    rankA = List.copyOf(rankA);
    rankB = List.copyOf(rankB);
    tieCandidates = List.copyOf(tieCandidates);
  }
}
