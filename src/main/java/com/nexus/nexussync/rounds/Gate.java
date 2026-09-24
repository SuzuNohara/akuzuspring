package com.nexus.nexussync.rounds;

import com.nexus.nexussync.ann.AnnException;
import com.nexus.nexussync.ann.CallBudget;
import com.nexus.nexussync.ann.Envelope;
import com.nexus.nexussync.context.Context;
import com.nexus.nexussync.params.Params;
import com.nexus.nexussync.params.RoundsParams;
import com.nexus.nexussync.sampler.Sample;
import com.nexus.nexussync.sampler.SampleItem;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Two-round gate between the sample and the couple's decision (unit U7).
 *
 * <p>Round one: both personas pick and the mediator recommends; each failed persona is retried
 * once. Both personas OK: F1 = intersection ({@link Closers#f1}); if it reaches {@code thresholdR1}
 * it closes as {@link Closure#F1}; otherwise, when {@code maxRounds >= 2}, both personas vote over
 * the shortlist (round-one ids outside F1, {@code kVote = min(kVote, |shortlist|)}) and F2 closes
 * if it reaches the threshold; otherwise the fill policy closes as {@link Closure#F3}. One persona
 * failed: with {@code allowTwoAi} and the mediator OK, {@link Closure#DEGRADED_F1} = surviving
 * persona ∩ mediator, then filled; otherwise {@link Closure#AI_UNAVAILABLE}. Both personas failed,
 * or the call budget exhausted before round one: {@link Closure#AI_UNAVAILABLE} with the top {@code
 * finalSize} of the sample by score.
 *
 * <p>Every agent call, retries included, consumes one unit of the {@link CallBudget}; if a unit
 * cannot be obtained the executor is not called. {@code runDir} is handed to the executor; the gate
 * itself writes nothing in this version.
 */
public final class Gate {

  private static final List<Agent> ROUND1 = List.of(Agent.A, Agent.B, Agent.M);
  private static final List<Agent> PERSONAS = List.of(Agent.A, Agent.B);

  /**
   * Creates a stateless gate.
   *
   * @implNote O(1) time and space.
   */
  public Gate() {
    // Stateless: all inputs are method arguments.
  }

  /**
   * Runs the gate over a sample.
   *
   * @param s sample offered to the agents
   * @param ctx couple context, handed to the executor
   * @param p parameters of the experiment (the gate reads {@link Params#rounds()})
   * @param ex executor of the agent rounds
   * @param runDir directory of the run, handed to the executor
   * @param budget call budget shared by the whole run
   * @return the final list, its closure and the parsed picks
   * @throws AnnException if the executor fails to render, launch or read a round
   * @implNote O(n log n) time and O(n) space in the sample size, plus at most five agent calls.
   */
  public GateResult run(
      Sample s, Context ctx, Params p, GateExecutor ex, Path runDir, CallBudget budget)
      throws AnnException {
    RoundsParams rp = p.rounds();
    List<String> sampleIds = s.items().stream().map(SampleItem::activityId).distinct().toList();
    Set<String> offered = new LinkedHashSet<>(sampleIds);
    if (!consume(budget, ROUND1.size())) {
      return unavailable(s, rp, sampleIds, List.of());
    }
    Map<Agent, Pick> picks = parse(ex.round1(runDir, ctx, s, p), ROUND1, offered, rp.pickCount());
    List<Agent> retry = retryable(picks, budget);
    if (!retry.isEmpty()) {
      picks.putAll(parse(ex.round1(runDir, ctx, s, p), retry, offered, rp.pickCount()));
    }
    List<Pick> round1 = ROUND1.stream().map(picks::get).toList();
    Pick a = picks.get(Agent.A);
    Pick b = picks.get(Agent.B);
    if (!a.ok() && !b.ok()) {
      return unavailable(s, rp, sampleIds, round1);
    }
    Round r = new Round(rp, sampleIds, round1, a, b, picks.get(Agent.M));
    if (!a.ok() || !b.ok()) {
      return degraded(s, r);
    }
    return full(r, ex, runDir, p, budget);
  }

  private GateResult degraded(Sample s, Round r) {
    Pick survivor = r.a().ok() ? r.a() : r.b();
    if (!r.rp().allowTwoAi() || !r.m().ok()) {
      return unavailable(s, r.rp(), r.sampleIds(), r.round1());
    }
    List<String> ordered =
        top(RankAggregator.order(Closers.intersect(survivor, r.m()), r.round1(), r.rp()), r.rp());
    List<String> filled = Closers.f3(ordered, r.a(), r.b(), r.m(), r.rp());
    return finish(r, Closure.DEGRADED_F1, filled, List.of());
  }

  private GateResult full(Round r, GateExecutor ex, Path runDir, Params p, CallBudget budget)
      throws AnnException {
    RoundsParams rp = r.rp();
    List<String> f1 = Closers.f1(r.a(), r.b(), r.m(), rp);
    if (f1.size() >= rp.thresholdR1()) {
      return finish(r, Closure.F1, top(RankAggregator.order(f1, r.round1(), rp), rp), List.of());
    }
    List<String> shortlist = shortlist(r.round1(), f1);
    List<Pick> votes = List.of();
    List<String> current = f1;
    if (rp.maxRounds() >= 2 && !shortlist.isEmpty() && consume(budget, PERSONAS.size())) {
      int voteK = Math.min(rp.voteCount(), shortlist.size());
      Map<Agent, Pick> v =
          parse(ex.round2(runDir, shortlist, p), PERSONAS, new LinkedHashSet<>(shortlist), voteK);
      votes = List.of(v.get(Agent.A), v.get(Agent.B));
      current = Closers.f2(f1, v.get(Agent.A), v.get(Agent.B));
      if (current.size() >= rp.thresholdR1()) {
        return finish(r, Closure.F2, top(RankAggregator.order(current, r.round1(), rp), rp), votes);
      }
    }
    List<String> ordered = top(RankAggregator.order(current, r.round1(), rp), rp);
    return finish(r, Closure.F3, Closers.f3(ordered, r.a(), r.b(), r.m(), rp), votes);
  }

  private static GateResult finish(
      Round r, Closure closure, List<String> finalIds, List<Pick> round2) {
    Map<String, Map<Agent, String>> reasons = new LinkedHashMap<>();
    for (String id : finalIds) {
      Map<Agent, String> byAgent = new EnumMap<>(Agent.class);
      r.round1().stream()
          .filter(pk -> pk.ok() && pk.ids().contains(id))
          .forEach(pk -> byAgent.put(pk.agent(), pk.reasons().get(pk.ids().indexOf(id))));
      reasons.put(id, byAgent);
    }
    return new GateResult(
        finalIds,
        closure,
        RankAggregator.remaining(r.sampleIds(), finalIds, r.round1()),
        r.round1(),
        round2,
        RankAggregator.rankSum(finalIds, r.round1(), r.rp().pickCount()),
        reasons);
  }

  private static GateResult unavailable(
      Sample s, RoundsParams rp, List<String> sampleIds, List<Pick> round1) {
    List<String> best =
        s.items().stream()
            .sorted(
                Comparator.comparingDouble(SampleItem::score)
                    .reversed()
                    .thenComparing(SampleItem::activityId))
            .map(SampleItem::activityId)
            .distinct()
            .limit(Math.max(0, rp.finalSize()))
            .toList();
    Round r =
        new Round(
            rp,
            sampleIds,
            round1,
            Pick.failed(Agent.A, List.of()),
            Pick.failed(Agent.B, List.of()),
            Pick.failed(Agent.M, List.of()));
    return finish(r, Closure.AI_UNAVAILABLE, best, List.of());
  }

  private static Map<Agent, Pick> parse(
      Map<Agent, Optional<Envelope>> envelopes, List<Agent> agents, Set<String> offered, int k) {
    Map<Agent, Pick> out = new EnumMap<>(Agent.class);
    for (Agent ag : agents) {
      Optional<Envelope> env = envelopes.getOrDefault(ag, Optional.empty());
      out.put(ag, PickParser.parse(ag, env, offered, k));
    }
    return out;
  }

  private static List<String> shortlist(List<Pick> round1, List<String> f1) {
    Set<String> out = new LinkedHashSet<>();
    round1.stream().filter(Pick::ok).forEach(pk -> out.addAll(pk.ids()));
    f1.forEach(out::remove);
    return new ArrayList<>(out);
  }

  private static List<Agent> retryable(Map<Agent, Pick> picks, CallBudget budget) {
    List<Agent> out = new ArrayList<>();
    for (Agent persona : PERSONAS) {
      if (!picks.get(persona).ok() && budget.tryConsume()) {
        out.add(persona);
      }
    }
    return out;
  }

  private static boolean consume(CallBudget budget, int calls) {
    for (int i = 0; i < calls; i++) {
      if (!budget.tryConsume()) {
        return false;
      }
    }
    return true;
  }

  private static List<String> top(List<String> ids, RoundsParams rp) {
    return ids.subList(0, Math.min(ids.size(), Math.max(0, rp.finalSize())));
  }

  /** State of a gate run after round one. */
  private record Round(
      RoundsParams rp, List<String> sampleIds, List<Pick> round1, Pick a, Pick b, Pick m) {}
}
