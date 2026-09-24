package com.nexus.nexussync.rounds;

import java.util.List;
import java.util.Objects;

/**
 * Parsed answer of one agent in one round (unit U7).
 *
 * @param agent agent that answered
 * @param ids valid activity ids, without duplicates, in the agent's order, at most {@code k}
 * @param reasons reason of each id, aligned with {@code ids} ({@code ""} when none was given)
 * @param hallucinated returned ids that are not part of the offered set
 * @param status outcome of the call
 */
public record Pick(
    Agent agent,
    List<String> ids,
    List<String> reasons,
    List<String> hallucinated,
    PickStatus status) {

  /**
   * Validates the components and copies the lists so the record is immutable.
   *
   * @implNote O(n) time and space in the number of ids.
   */
  public Pick {
    Objects.requireNonNull(agent, "agent");
    Objects.requireNonNull(status, "status");
    ids = List.copyOf(ids);
    reasons = List.copyOf(reasons);
    hallucinated = List.copyOf(hallucinated);
  }

  /**
   * Creates a failed pick with no ids.
   *
   * @param agent agent that failed
   * @param hallucinated invalid ids returned, if any
   * @return a pick with status {@link PickStatus#FAILED}
   * @implNote O(h) time and space.
   */
  public static Pick failed(Agent agent, List<String> hallucinated) {
    return new Pick(agent, List.of(), List.of(), hallucinated, PickStatus.FAILED);
  }

  /**
   * Tells whether the pick can be used by the closers.
   *
   * @return {@code true} if the status is {@link PickStatus#OK}
   * @implNote O(1) time and space.
   */
  public boolean ok() {
    return status == PickStatus.OK;
  }
}
