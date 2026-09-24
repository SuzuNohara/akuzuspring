package com.nexus.nexussync.rounds;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Outcome of the two-round gate (unit U7).
 *
 * @param finalIds final list, best first, at most {@code finalSize}
 * @param closure how the final list was produced
 * @param remaining sample ids not in the final list, most promising first ("more options")
 * @param round1 parsed round-one picks (empty if round one was not run)
 * @param round2 parsed round-two votes (empty if round two was not run)
 * @param rankSums rank sum of every final id over the round-one picks
 * @param reasons reasons of every final id, per agent that picked it
 */
public record GateResult(
    List<String> finalIds,
    Closure closure,
    List<String> remaining,
    List<Pick> round1,
    List<Pick> round2,
    Map<String, Integer> rankSums,
    Map<String, Map<Agent, String>> reasons) {

  /**
   * Validates the closure and copies the collections so the record is immutable; map iteration
   * order is preserved.
   *
   * @implNote O(n) time and space in the size of the collections.
   */
  public GateResult {
    Objects.requireNonNull(closure, "closure");
    finalIds = List.copyOf(finalIds);
    remaining = List.copyOf(remaining);
    round1 = List.copyOf(round1);
    round2 = List.copyOf(round2);
    rankSums = Collections.unmodifiableMap(new LinkedHashMap<>(rankSums));
    Map<String, Map<Agent, String>> copy = new LinkedHashMap<>();
    reasons.forEach((id, byAgent) -> copy.put(id, copyByAgent(byAgent)));
    reasons = Collections.unmodifiableMap(copy);
  }

  private static Map<Agent, String> copyByAgent(Map<Agent, String> byAgent) {
    Map<Agent, String> out = new EnumMap<>(Agent.class);
    out.putAll(byAgent);
    return Collections.unmodifiableMap(out);
  }
}
