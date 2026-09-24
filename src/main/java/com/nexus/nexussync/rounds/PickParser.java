package com.nexus.nexussync.rounds;

import com.nexus.nexussync.ann.Envelope;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Turns the envelope of one agent into a {@link Pick} (unit U7).
 *
 * <p>The ids are read from {@code payload.picks} (round one) or, if absent, from {@code
 * payload.votes} (round two); reasons from {@code payload.reasons}, aligned by position. Ids that
 * are not offered go to {@code hallucinated}; duplicates are dropped; the list is cut to {@code k}.
 * Any agent may return fewer than {@code k} ids (the mediator does so on purpose): 1..k valid ids
 * give {@link PickStatus#OK}, zero give {@link PickStatus#FAILED}.
 */
public final class PickParser {

  /** Envelope status that marks a successful agent call (spike T-06). */
  static final String SUCCESS = "success";

  private PickParser() {}

  /**
   * Parses the envelope of one agent.
   *
   * @param agent agent that produced the envelope
   * @param env envelope, {@code Optional.empty()} if the agent produced none
   * @param sampleIds ids that were offered to the agent
   * @param k maximum number of ids to keep
   * @return the parsed pick; {@link PickStatus#FAILED} if the envelope is absent, its status is not
   *     {@code success}, the ids are not a list or none of them is valid
   * @implNote O(n) time and space, n = number of returned ids.
   */
  public static Pick parse(Agent agent, Optional<Envelope> env, Set<String> sampleIds, int k) {
    if (env.isEmpty() || !SUCCESS.equals(env.get().status())) {
      return Pick.failed(agent, List.of());
    }
    Object raw = env.get().payload().getOrDefault("picks", env.get().payload().get("votes"));
    if (!(raw instanceof List<?> returned)) {
      return Pick.failed(agent, List.of());
    }
    List<?> rawReasons = env.get().payload().get("reasons") instanceof List<?> r ? r : List.of();
    return collect(agent, returned, rawReasons, sampleIds, k);
  }

  private static Pick collect(
      Agent agent, List<?> returned, List<?> rawReasons, Set<String> sampleIds, int k) {
    Set<String> ids = new LinkedHashSet<>();
    List<String> reasons = new ArrayList<>();
    Set<String> hallucinated = new LinkedHashSet<>();
    for (int i = 0; i < returned.size(); i++) {
      String id = String.valueOf(returned.get(i));
      if (!sampleIds.contains(id)) {
        hallucinated.add(id);
      } else if (ids.size() < k && ids.add(id)) {
        reasons.add(i < rawReasons.size() ? String.valueOf(rawReasons.get(i)) : "");
      }
    }
    if (ids.isEmpty()) {
      return Pick.failed(agent, new ArrayList<>(hallucinated));
    }
    return new Pick(
        agent, new ArrayList<>(ids), reasons, new ArrayList<>(hallucinated), PickStatus.OK);
  }
}
