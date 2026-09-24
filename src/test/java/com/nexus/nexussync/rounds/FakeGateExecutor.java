package com.nexus.nexussync.rounds;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.nexus.nexussync.ann.AnnException;
import com.nexus.nexussync.ann.Envelope;
import com.nexus.nexussync.context.Context;
import com.nexus.nexussync.params.Params;
import com.nexus.nexussync.sampler.Sample;
import com.nexus.nexussync.sampler.SampleItem;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Programmable {@link GateExecutor} for tests: answers per agent and round, and can make an agent
 * produce no envelope for its next {@code n} calls, whatever the round.
 *
 * <p>With {@link #echoSample()} an agent without a programmed answer picks the first {@code k} ids
 * of the sample (round one) or of the shortlist (round two). Every call records the agents it was
 * asked for and writes, like {@link ArkannieExecutor}, a {@code round<N>[-attempt].ann} listing the
 * requested dispatches and a {@code round<N>[-attempt].out.md} in the arkannie output format with
 * the envelopes it returned, so a run made with this fake can be replayed.
 */
public final class FakeGateExecutor implements GateExecutor {

  private static final List<Agent> ROUND2 = List.of(Agent.A, Agent.B);
  private static final ObjectMapper YAML =
      YAMLMapper.builder().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER).build();

  private final Map<Agent, Map<Integer, Optional<Envelope>>> responses = new EnumMap<>(Agent.class);
  private final Map<Agent, Integer> failures = new EnumMap<>(Agent.class);
  private final List<List<String>> shortlists = new ArrayList<>();
  private final List<Set<Agent>> round1Agents = new ArrayList<>();
  private boolean echo;
  private int round2Calls;

  /** Creates a fake with no programmed answer: every agent produces no envelope. */
  public FakeGateExecutor() {
    // Answers are programmed with respond, failTimes and echoSample.
  }

  /**
   * Programs the answer of an agent in a round.
   *
   * @param agent agent to program
   * @param round 1 or 2
   * @param env envelope to return; empty means no envelope
   * @return this fake
   */
  public FakeGateExecutor respond(Agent agent, int round, Optional<Envelope> env) {
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
  public FakeGateExecutor failTimes(Agent agent, int times) {
    failures.put(agent, times);
    return this;
  }

  /**
   * Makes every agent without a programmed answer pick the first {@code k} offered ids.
   *
   * @return this fake
   */
  public FakeGateExecutor echoSample() {
    echo = true;
    return this;
  }

  @Override
  public Map<Agent, Optional<Envelope>> round1(
      Path runDir, Context ctx, Sample s, Params p, Set<Agent> agents) throws AnnException {
    round1Agents.add(Set.copyOf(agents));
    List<String> offered = s.items().stream().map(SampleItem::activityId).toList();
    List<Agent> ordered = List.copyOf(EnumSet.copyOf(agents));
    Map<Agent, Optional<Envelope>> out = answer(1, ordered, offered, p.rounds().pickCount());
    record(runDir, 1, out);
    return out;
  }

  @Override
  public Map<Agent, Optional<Envelope>> round2(Path runDir, List<String> shortlist, Params p)
      throws AnnException {
    round2Calls++;
    shortlists.add(List.copyOf(shortlist));
    int k = Math.min(p.rounds().voteCount(), shortlist.size());
    Map<Agent, Optional<Envelope>> out = answer(2, ROUND2, shortlist, k);
    record(runDir, 2, out);
    return out;
  }

  /**
   * Number of round-one calls, retries included.
   *
   * @return the number of calls
   */
  public int round1Calls() {
    return round1Agents.size();
  }

  /**
   * Agents requested by every round-one call, in call order.
   *
   * @return one set per call
   */
  public List<Set<Agent>> round1Agents() {
    return List.copyOf(round1Agents);
  }

  /**
   * Number of round-two calls.
   *
   * @return the number of calls
   */
  public int round2Calls() {
    return round2Calls;
  }

  /**
   * Shortlists received by round two, in call order.
   *
   * @return one list per call
   */
  public List<List<String>> shortlists() {
    return List.copyOf(shortlists);
  }

  private Map<Agent, Optional<Envelope>> answer(
      int round, List<Agent> agents, List<String> offered, int k) {
    Map<Agent, Optional<Envelope>> out = new EnumMap<>(Agent.class);
    for (Agent ag : agents) {
      int left = failures.getOrDefault(ag, 0);
      if (left > 0) {
        failures.put(ag, left - 1);
        out.put(ag, Optional.empty());
      } else {
        out.put(ag, programmed(ag, round).orElseGet(() -> echoed(ag, round, offered, k)));
      }
    }
    return out;
  }

  private Optional<Optional<Envelope>> programmed(Agent ag, int round) {
    Map<Integer, Optional<Envelope>> byRound = responses.getOrDefault(ag, Map.of());
    return byRound.containsKey(round) ? Optional.of(byRound.get(round)) : Optional.empty();
  }

  private Optional<Envelope> echoed(Agent ag, int round, List<String> offered, int k) {
    if (!echo) {
      return Optional.empty();
    }
    String id = ag.name().toLowerCase(Locale.ROOT);
    List<String> ids = offered.subList(0, Math.min(Math.max(0, k), offered.size()));
    return envelope(id, round == 1 ? "picks" : "votes", ids.toArray(String[]::new));
  }

  /** Writes the program and the output of the call next to the traces, as arkannie would. */
  private static void record(Path runDir, int round, Map<Agent, Optional<Envelope>> out)
      throws AnnException {
    String base = "round" + round;
    String suffix = "";
    int attempt = 2;
    while (Files.exists(runDir.resolve(base + suffix + ".ann"))) {
      suffix = "-" + attempt++;
    }
    StringBuilder program = new StringBuilder("# ann v0.3\n");
    StringBuilder output = new StringBuilder("---\nid: fake\nstatus: success\n---\n");
    int section = 1;
    for (Map.Entry<Agent, Optional<Envelope>> e : out.entrySet()) {
      String id = e.getKey().name().toLowerCase(Locale.ROOT);
      program.append("[fake] --id=").append(id).append('\n');
      if (e.getValue().isPresent()) {
        output.append("\n## r-").append(section++).append("\n\n```yaml\n");
        output.append(yaml(e.getValue().get())).append("```\n");
      }
    }
    try {
      Files.createDirectories(runDir);
      Files.writeString(runDir.resolve(base + suffix + ".ann"), program, StandardCharsets.UTF_8);
      Files.writeString(runDir.resolve(base + suffix + ".out.md"), output, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new AnnException(AnnException.Kind.RENDER, "fake cannot record in " + runDir, e);
    }
  }

  private static String yaml(Envelope env) throws AnnException {
    Map<String, Object> block = new LinkedHashMap<>();
    block.put("id", env.id());
    block.put("status", env.status());
    block.put("payload", env.payload());
    try {
      return YAML.writeValueAsString(block);
    } catch (JsonProcessingException e) {
      throw new AnnException(AnnException.Kind.RENDER, "fake cannot write yaml", e);
    }
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
  public static Optional<Envelope> envelope(String id, String key, String... ids) {
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
