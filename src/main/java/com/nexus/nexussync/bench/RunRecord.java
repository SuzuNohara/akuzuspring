package com.nexus.nexussync.bench;

import com.nexus.nexussync.catalog.Activity;
import com.nexus.nexussync.catalog.Catalog;
import com.nexus.nexussync.catalog.Place;
import com.nexus.nexussync.context.Context;
import com.nexus.nexussync.context.Profile;
import com.nexus.nexussync.decision.Decision;
import com.nexus.nexussync.learning.Weights;
import com.nexus.nexussync.params.Feature;
import com.nexus.nexussync.params.Intersection;
import com.nexus.nexussync.places.PlaceAssignment;
import com.nexus.nexussync.places.PlaceOption;
import com.nexus.nexussync.rounds.Agent;
import com.nexus.nexussync.rounds.GateResult;
import com.nexus.nexussync.rounds.Pick;
import com.nexus.nexussync.sampler.Sample;
import java.time.Duration;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Everything the bench keeps about one simulated date of one couple (unit U11, §3.11).
 *
 * <p>The components of the plan are completed with {@link Evidence}: the data {@link Metrics} needs
 * and cannot recompute from the other components without the catalog, the parameters or the
 * profiles (declared deviation of T-59).
 *
 * @param runId identifier of the run ({@code RunIds.of})
 * @param coupleId couple identifier
 * @param experiment experiment name
 * @param paramsHash hash of the parameter set
 * @param seed seed of the experiment
 * @param sample sample offered to the agents
 * @param gate outcome of the gate
 * @param decision simulated decision of the couple; empty when there was nothing to decide or a
 *     profile has no {@code truthWeights}
 * @param places places proposed for the chosen activity; empty without decision
 * @param before weights used by this run (learned or initial)
 * @param after weights learned from this run; empty without decision
 * @param elapsed wall-clock time of the run
 * @param evidence bench-only data for the metrics
 */
public record RunRecord(
    String runId,
    String coupleId,
    String experiment,
    String paramsHash,
    long seed,
    Sample sample,
    GateResult gate,
    Optional<Decision> decision,
    Optional<PlaceAssignment> places,
    Weights before,
    Optional<Weights> after,
    Duration elapsed,
    Evidence evidence) {

  /**
   * Validates the components.
   *
   * @implNote O(1) time and space.
   */
  public RunRecord {
    Objects.requireNonNull(runId, "runId");
    Objects.requireNonNull(sample, "sample");
    Objects.requireNonNull(gate, "gate");
    Objects.requireNonNull(before, "before");
    Objects.requireNonNull(elapsed, "elapsed");
    Objects.requireNonNull(evidence, "evidence");
  }

  /**
   * Bench-only data of a run, never shown to the agents.
   *
   * @param truthWeights hidden weights of each person that has them ({@link Agent#A}, {@link
   *     Agent#B})
   * @param intersectionF1 size of the round-one intersection under the {@code intersection} rule
   *     (surviving persona ∩ mediator when one persona failed; 0 when no intersection exists)
   * @param finalTypes {@code activity_type} of every final id, in the order of the final list
   * @param hoursDeclared proposed places whose catalog entry has non-empty opening hours
   * @param hoursParsed among those, the places whose hours could be interpreted
   */
  public record Evidence(
      Map<Agent, Map<Feature, Double>> truthWeights,
      int intersectionF1,
      List<String> finalTypes,
      int hoursDeclared,
      int hoursParsed) {

    /**
     * Copies the collections so the record is immutable.
     *
     * @implNote O(a·f + n) time and space, a persons, f features, n final ids.
     */
    public Evidence {
      Map<Agent, Map<Feature, Double>> copy = new EnumMap<>(Agent.class);
      truthWeights.forEach((agent, w) -> copy.put(agent, Map.copyOf(w)));
      truthWeights = Collections.unmodifiableMap(copy);
      finalTypes = List.copyOf(finalTypes);
    }

    /**
     * Gathers the evidence of a finished run.
     *
     * @param ctx context of the couple
     * @param gate outcome of the gate
     * @param places places proposed for the chosen activity, if any
     * @param cat catalog of the run
     * @param mode intersection rule of round one
     * @return the evidence
     * @implNote O(n + k) time and space, n final ids, k picked ids and proposed places.
     */
    public static Evidence of(
        Context ctx,
        GateResult gate,
        Optional<PlaceAssignment> places,
        Catalog cat,
        Intersection mode) {
      Map<Agent, Map<Feature, Double>> truth = new EnumMap<>(Agent.class);
      truth(ctx.a()).ifPresent(w -> truth.put(Agent.A, w));
      truth(ctx.b()).ifPresent(w -> truth.put(Agent.B, w));
      List<String> types =
          gate.finalIds().stream()
              .map(id -> Optional.ofNullable(cat.activities().get(id)))
              .map(a -> a.map(Activity::activityType).orElse(""))
              .toList();
      List<PlaceOption> options = places.map(PlaceAssignment::options).orElse(List.of());
      int declared = 0;
      int parsed = 0;
      for (PlaceOption o : options) {
        if (hasHours(cat.places().get(o.placeId()))) {
          declared++;
          parsed += o.hoursUnknown() ? 0 : 1;
        }
      }
      return new Evidence(truth, intersectionF1(gate.round1(), mode), types, declared, parsed);
    }

    private static Optional<Map<Feature, Double>> truth(Profile p) {
      return p.truthWeights();
    }

    private static boolean hasHours(Place place) {
      return place != null && place.openingHours().filter(h -> !h.isBlank()).isPresent();
    }

    /**
     * Size of the round-one intersection: {@code A ∩ B} (∩ M under {@code TRIPLE} with the mediator
     * OK) when both personas answered, surviving persona ∩ M when only one did and the mediator
     * answered, 0 otherwise.
     *
     * @param round1 round-one picks in the order A, B, M; fewer than three means no round one
     * @param mode intersection rule
     * @return the size of the intersection
     * @implNote O(k) time and space, k picked ids.
     */
    static int intersectionF1(List<Pick> round1, Intersection mode) {
      if (round1.size() < Agent.values().length) {
        return 0;
      }
      Pick a = round1.get(0);
      Pick b = round1.get(1);
      Pick m = round1.get(2);
      if (a.ok() && b.ok()) {
        Set<String> x = new HashSet<>(a.ids());
        x.retainAll(b.ids());
        if (mode == Intersection.TRIPLE && m.ok()) {
          x.retainAll(m.ids());
        }
        return x.size();
      }
      if ((a.ok() || b.ok()) && m.ok()) {
        Set<String> x = new HashSet<>(a.ok() ? a.ids() : b.ids());
        x.retainAll(m.ids());
        return x.size();
      }
      return 0;
    }
  }
}
