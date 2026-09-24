package com.nexus.nexussync.bench;

import com.nexus.nexussync.ann.Envelope;
import com.nexus.nexussync.catalog.Activity;
import com.nexus.nexussync.catalog.Catalog;
import com.nexus.nexussync.context.Context;
import com.nexus.nexussync.context.Profile;
import com.nexus.nexussync.params.Feature;
import com.nexus.nexussync.params.Params;
import com.nexus.nexussync.params.SamplerParams;
import com.nexus.nexussync.rounds.Agent;
import com.nexus.nexussync.rounds.GateExecutor;
import com.nexus.nexussync.sampler.FeatureExtractor;
import com.nexus.nexussync.sampler.Sample;
import com.nexus.nexussync.sampler.SampleItem;
import com.nexus.nexussync.sampler.Scorer;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link GateExecutor} without AI for the pre-screening of the calibration (unit U13, Fase C3).
 *
 * <p>Each persona picks the {@code kPick} sampled ids with the best {@link Scorer#score} under the
 * {@code truthWeights} of its profile; the mediator does the same under the feature-wise mean of
 * both persons' weights (only the available one when a single person has them). In round two each
 * persona votes the best {@code min(kVote, |shortlist|)} ids of the shortlist the same way. Ties
 * are broken lexicographically by id. Features come from {@link FeatureExtractor} when the catalog
 * holds the activity (as {@code TruthRanker} does), and from the sample item otherwise. A persona
 * without {@code truthWeights} produces no envelope; round two before any round one produces none
 * either. Envelopes have status {@code success}, the ids under {@code picks} or {@code votes} and
 * the fixed reason {@value #REASON} per id.
 *
 * <p>No agent is called, so the executor is not {@link #billable()} and the gate never touches the
 * call budget (D-26). Round two reuses the context and features of the last round one: use one
 * instance per gate run at a time.
 */
public final class OracleExecutor implements GateExecutor {

  /** Reason attached to every id returned by the oracle. */
  static final String REASON = "oracle: truth score";

  private static final String SUCCESS = "success";
  private static final String PICKS = "picks";
  private static final String VOTES = "votes";
  private static final String REASONS = "reasons";
  private static final List<Agent> PERSONAS = List.of(Agent.A, Agent.B);

  private final Catalog cat;
  private final SamplerParams sp;
  private final AtomicReference<Optional<State>> last = new AtomicReference<>(Optional.empty());

  /**
   * Creates an oracle over a catalog.
   *
   * @param cat catalog holding the sampled activities, never {@code null}
   * @param sp sampler parameters used by the feature extractor, never {@code null}
   * @implNote O(1) time and space.
   */
  public OracleExecutor(Catalog cat, SamplerParams sp) {
    this.cat = Objects.requireNonNull(cat, "cat");
    this.sp = Objects.requireNonNull(sp, "sp");
  }

  /**
   * Picks for the requested agents by their truth weights.
   *
   * @implNote O(n · f + a · n log n) time and O(n · f) space, n sample items, f features, a agents.
   */
  @Override
  public Map<Agent, Optional<Envelope>> round1(
      Path runDir, Context ctx, Sample s, Params p, Set<Agent> agents) {
    State state = new State(ctx, features(ctx, s));
    last.set(Optional.of(state));
    List<String> ids = List.copyOf(state.features().keySet());
    Map<Agent, Optional<Envelope>> out = new EnumMap<>(Agent.class);
    for (Agent agent : EnumSet.copyOf(agents)) {
      Optional<Map<Feature, Double>> w = weights(agent, ctx);
      out.put(
          agent, w.map(t -> envelope(agent, PICKS, best(ids, t, state, p.rounds().pickCount()))));
    }
    return out;
  }

  /**
   * Votes of both personas over the shortlist by their truth weights.
   *
   * @implNote O(s log s) time and O(s) space, s = shortlist size.
   */
  @Override
  public Map<Agent, Optional<Envelope>> round2(Path runDir, List<String> shortlist, Params p) {
    Map<Agent, Optional<Envelope>> out = new EnumMap<>(Agent.class);
    Optional<State> state = last.get();
    int k = Math.min(p.rounds().voteCount(), shortlist.size());
    for (Agent persona : PERSONAS) {
      out.put(
          persona,
          state.flatMap(
              st ->
                  weights(persona, st.ctx())
                      .map(w -> envelope(persona, VOTES, best(shortlist, w, st, k)))));
    }
    return out;
  }

  /**
   * The oracle calls no agent (D-26).
   *
   * @return {@code false}
   * @implNote O(1) time and space.
   */
  @Override
  public boolean billable() {
    return false;
  }

  /**
   * Weights driving an agent: the persona's {@code truthWeights}, or their feature-wise mean for
   * the mediator.
   *
   * @param agent agent to drive
   * @param ctx couple context
   * @return the weights, empty when the needed {@code truthWeights} are missing
   * @implNote O(f) time and space, f = features.
   */
  static Optional<Map<Feature, Double>> weights(Agent agent, Context ctx) {
    return switch (agent) {
      case A -> ctx.a().truthWeights();
      case B -> ctx.b().truthWeights();
      case M -> mean(ctx.a(), ctx.b());
    };
  }

  private static Optional<Map<Feature, Double>> mean(Profile a, Profile b) {
    List<Map<Feature, Double>> present =
        List.of(a, b).stream().map(Profile::truthWeights).flatMap(Optional::stream).toList();
    if (present.isEmpty()) {
      return Optional.empty();
    }
    Map<Feature, Double> out = new EnumMap<>(Feature.class);
    for (Feature f : Feature.values()) {
      double sum = present.stream().mapToDouble(w -> w.getOrDefault(f, 0.0)).sum();
      out.put(f, sum / present.size());
    }
    return Optional.of(Collections.unmodifiableMap(out));
  }

  private Map<String, Map<Feature, Double>> features(Context ctx, Sample s) {
    Map<String, Map<Feature, Double>> out = new LinkedHashMap<>();
    for (SampleItem item : s.items()) {
      Activity a = cat.activities().get(item.activityId());
      out.putIfAbsent(
          item.activityId(), a == null ? item.features() : FeatureExtractor.of(a, ctx, cat, sp));
    }
    return out;
  }

  private static List<String> best(List<String> ids, Map<Feature, Double> w, State state, int k) {
    Comparator<String> byScore =
        Comparator.comparingDouble(
            (String id) -> -Scorer.score(state.features().getOrDefault(id, Map.of()), w));
    return ids.stream()
        .distinct()
        .sorted(byScore.thenComparing(Comparator.naturalOrder()))
        .limit(Math.max(0, k))
        .toList();
  }

  private static Envelope envelope(Agent agent, String key, List<String> ids) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put(key, ids);
    payload.put(REASONS, Collections.nCopies(ids.size(), REASON));
    return new Envelope(agent.name().toLowerCase(Locale.ROOT), SUCCESS, payload);
  }

  /** Context and features of the last round one, reused by round two. */
  private record State(Context ctx, Map<String, Map<Feature, Double>> features) {}
}
