package com.nexus.nexussync.rounds;

import com.nexus.nexussync.ann.Envelope;
import com.nexus.nexussync.context.Context;
import com.nexus.nexussync.params.Params;
import com.nexus.nexussync.sampler.Sample;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Programmable {@link GateExecutor} for tests: answers per agent and round, and can make an agent
 * produce no envelope for its next {@code n} calls, whatever the round.
 */
final class FakeGateExecutor implements GateExecutor {

  private static final List<Agent> ROUND1 = List.of(Agent.A, Agent.B, Agent.M);
  private static final List<Agent> ROUND2 = List.of(Agent.A, Agent.B);

  private final Map<Agent, Map<Integer, Optional<Envelope>>> responses = new EnumMap<>(Agent.class);
  private final Map<Agent, Integer> failures = new EnumMap<>(Agent.class);
  private final List<List<String>> shortlists = new ArrayList<>();
  private int round1Calls;
  private int round2Calls;

  /**
   * Programs the answer of an agent in a round.
   *
   * @param agent agent to program
   * @param round 1 or 2
   * @param env envelope to return; empty means no envelope
   * @return this fake
   */
  FakeGateExecutor respond(Agent agent, int round, Optional<Envelope> env) {
    responses.computeIfAbsent(agent, ag -> new HashMap<>()).put(round, env);
    return this;
  }

  /**
   * Makes the next {@code times} calls of an agent produce no envelope.
   *
   * @param agent agent that fails
   * @param times number of consecutive failed calls
   * @return this fake
   */
  FakeGateExecutor failTimes(Agent agent, int times) {
    failures.put(agent, times);
    return this;
  }

  @Override
  public Map<Agent, Optional<Envelope>> round1(Path runDir, Context ctx, Sample s, Params p) {
    round1Calls++;
    return answer(1, ROUND1);
  }

  @Override
  public Map<Agent, Optional<Envelope>> round2(Path runDir, List<String> shortlist, Params p) {
    round2Calls++;
    shortlists.add(List.copyOf(shortlist));
    return answer(2, ROUND2);
  }

  int round1Calls() {
    return round1Calls;
  }

  int round2Calls() {
    return round2Calls;
  }

  List<List<String>> shortlists() {
    return List.copyOf(shortlists);
  }

  private Map<Agent, Optional<Envelope>> answer(int round, List<Agent> agents) {
    Map<Agent, Optional<Envelope>> out = new EnumMap<>(Agent.class);
    for (Agent ag : agents) {
      int left = failures.getOrDefault(ag, 0);
      if (left > 0) {
        failures.put(ag, left - 1);
        out.put(ag, Optional.empty());
      } else {
        out.put(ag, responses.getOrDefault(ag, Map.of()).getOrDefault(round, Optional.empty()));
      }
    }
    return out;
  }

  /**
   * Builds a successful envelope whose payload lists {@code ids} under {@code key}, with one reason
   * {@code "<id>-<agent id>"} per id.
   *
   * @param id dispatch id ({@code a}, {@code b} or {@code m})
   * @param key {@code picks} or {@code votes}
   * @param ids returned ids
   * @return an optional envelope with status {@code success}
   */
  static Optional<Envelope> envelope(String id, String key, String... ids) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put(key, List.of(ids));
    List<String> reasons = new ArrayList<>();
    for (String activity : ids) {
      reasons.add(activity + "-" + id);
    }
    payload.put("reasons", reasons);
    return Optional.of(new Envelope(id, "success", payload));
  }
}
