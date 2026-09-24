package com.nexus.nexussync.bench;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.nexus.nexussync.decision.Decision;
import com.nexus.nexussync.learning.Weights;
import com.nexus.nexussync.params.Feature;
import com.nexus.nexussync.rounds.Agent;
import com.nexus.nexussync.rounds.Closure;
import com.nexus.nexussync.sampler.Sample;
import com.nexus.nexussync.sampler.SampleItem;
import com.nexus.nexussync.sampler.SampleStatus;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CalibrationMetricsTest {

  private static final Map<String, GoldEntry> GOLD =
      Map.of("1-2", new GoldEntry("1-2", Set.of("PARK"), Set.of("BAR"), Set.of("x9"), "prueba"));

  private static final Map<Feature, Double> INTEREST = Map.of(Feature.INTEREST, 1.0);
  private static final Map<Feature, Double> PRICE = Map.of(Feature.PRICE, 1.0);

  /** Run of {@code couple} with {@code seed}, final ids and their types. */
  private static RunRecord run(
      String couple, long seed, List<String> finalIds, List<String> types, int calls) {
    return run(couple, seed, finalIds, types, Optional.empty(), Map.of(), calls);
  }

  private static RunRecord run(
      String couple,
      long seed,
      List<String> finalIds,
      List<String> types,
      Optional<Weights> after,
      Map<Agent, Map<Feature, Double>> truth,
      int calls) {
    return new RunRecord(
        "exp-" + couple + "-" + seed,
        couple,
        "exp",
        "abcd1234",
        seed,
        BenchFixtures.emptySample(),
        BenchFixtures.gate(Closure.F1, finalIds, List.of(), List.of()),
        Optional.empty(),
        Optional.empty(),
        BenchFixtures.weights(INTEREST),
        after,
        Duration.ofSeconds(1),
        new RunRecord.Evidence(truth, 0, types, 0, 0, calls));
  }

  private static Map<Agent, Map<Feature, Double>> truth(
      Map<Feature, Double> a, Map<Feature, Double> b) {
    Map<Agent, Map<Feature, Double>> t = new EnumMap<>(Agent.class);
    t.put(Agent.A, a);
    t.put(Agent.B, b);
    return t;
  }

  @Test
  void given_runs_when_ofWithGold_then_baseMetricsFollowedByCalibrationOnes() {
    Map<String, Double> m = Metrics.of(List.of(run("1-2", 1, List.of(), List.of(), 0)), GOLD);

    List<String> keys = new ArrayList<>(Metrics.NAMES);
    keys.addAll(Metrics.CALIBRATION_NAMES);
    assertThat(m.keySet()).containsExactlyElementsOf(keys);
    assertThat(Metrics.of(List.of(), GOLD).values()).allMatch(v -> v.isNaN());
  }

  // U13-02
  @Test
  void given_goldRuns_when_of_then_goldHitIsMeanOverRunsWithGold() {
    List<RunRecord> rs =
        List.of(
            run("1-2", 1, List.of("p1", "b1"), List.of("PARK", "BAR"), 0),
            run("1-2", 2, List.of("m1"), List.of("MUSEUM"), 0),
            run("3-4", 1, List.of("p1"), List.of("PARK"), 0));

    assertThat(Metrics.of(rs, GOLD).get(Metrics.GOLD_HIT)).isEqualTo(0.5);
    assertThat(Metrics.of(List.of(rs.get(0)), GOLD).get(Metrics.GOLD_HIT)).isEqualTo(1.0);
    assertThat(Metrics.of(List.of(rs.get(1)), GOLD).get(Metrics.GOLD_HIT)).isZero();
  }

  // U13-02
  @Test
  void given_noGold_when_of_then_goldMetricsNaN() {
    List<RunRecord> rs = List.of(run("3-4", 1, List.of("p1"), List.of("PARK"), 0));

    Map<String, Double> m = Metrics.of(rs, GOLD);

    assertThat(m.get(Metrics.GOLD_HIT)).isNaN();
    assertThat(m.get(Metrics.GOLD_VIOLATION)).isNaN();
    assertThat(Metrics.of(rs, Map.of()).get(Metrics.GOLD_HIT)).isNaN();
  }

  // U13-03
  @Test
  void given_forbiddenTypeOrId_when_of_then_violationCountsEitherOne() {
    List<RunRecord> rs =
        List.of(
            run("1-2", 1, List.of("b1"), List.of("BAR"), 0),
            run("1-2", 2, List.of("x9"), List.of("PARK"), 0),
            run("1-2", 3, List.of("p1"), List.of("PARK"), 0),
            run("3-4", 1, List.of("x9"), List.of("BAR"), 0));

    assertThat(Metrics.of(rs, GOLD).get(Metrics.GOLD_VIOLATION))
        .isCloseTo(2.0 / 3.0, within(1e-12));
  }

  // U13-03 (D-32: chapter 8 §8.4, alignment of the decision with A and with B, every run)
  @Test
  void given_decisionsAndFinals_when_of_then_fairnessGapIsMeanGapOverEveryRunWithOutcome() {
    Map<Feature, Double> both = new EnumMap<>(Feature.class);
    both.put(Feature.INTEREST, 0.5);
    both.put(Feature.PRICE, 0.5);
    List<RunRecord> rs =
        List.of(
            decided("x", truth(INTEREST, PRICE)),
            decided("x", truth(INTEREST, INTEREST)),
            undecided(List.of("x", "y"), truth(INTEREST, PRICE)),
            decided("x", Map.of(Agent.A, INTEREST)),
            undecided(List.of("absent"), truth(INTEREST, PRICE)));

    double gap = Metrics.of(rs, GOLD).get(Metrics.FAIRNESS_GAP);

    assertThat(gap).isCloseTo(1.0 / 3, within(1e-9)).isBetween(0.0, 1.0);
    assertThat(Metrics.of(rs.subList(3, 5), GOLD).get(Metrics.FAIRNESS_GAP)).isNaN();
    assertThat(Metrics.of(List.of(decided("x", truth(both, both))), GOLD))
        .containsEntry(Metrics.FAIRNESS_GAP, 0.0);
  }

  // U13-03
  @Test
  void given_decisionWithoutLearnedWeights_when_of_then_runStillCountsForFairness() {
    RunRecord r = decided("y", truth(INTEREST, PRICE));

    assertThat(r.after()).isEmpty();
    assertThat(Metrics.of(List.of(r), GOLD).get(Metrics.FAIRNESS_GAP)).isEqualTo(1.0);
  }

  /** Sample {@code x} = pure interest, {@code y} = pure price. */
  private static Sample fairnessSample() {
    return new Sample(
        List.of(new SampleItem("x", 1.0, INTEREST, false), new SampleItem("y", 1.0, PRICE, false)),
        List.of(),
        SampleStatus.OK,
        Map.of());
  }

  /** Run whose couple chose {@code chosen} out of {@code x, y}; nothing learned. */
  private static RunRecord decided(String chosen, Map<Agent, Map<Feature, Double>> truth) {
    Decision d = new Decision(chosen, Map.of(chosen, 6), List.of(), List.of(), false, List.of());
    return fairnessRun(List.of("x", "y"), Optional.of(d), truth);
  }

  /** Run with a final list and no decision. */
  private static RunRecord undecided(List<String> finalIds, Map<Agent, Map<Feature, Double>> t) {
    return fairnessRun(finalIds, Optional.empty(), t);
  }

  private static RunRecord fairnessRun(
      List<String> finalIds, Optional<Decision> d, Map<Agent, Map<Feature, Double>> truth) {
    return new RunRecord(
        "exp-1-2-f",
        "1-2",
        "exp",
        "abcd1234",
        1L,
        fairnessSample(),
        BenchFixtures.gate(Closure.F1, finalIds, List.of(), List.of()),
        d,
        Optional.empty(),
        BenchFixtures.weights(INTEREST),
        Optional.empty(),
        Duration.ofSeconds(1),
        new RunRecord.Evidence(truth, 0, List.of(), 0, 0, 0));
  }

  // U13-04
  @Test
  void given_identicalFinalsAcrossThreeSeeds_when_of_then_stabilityOne() {
    List<RunRecord> rs =
        List.of(
            run("1-2", 1, List.of("a", "b"), List.of(), 0),
            run("1-2", 2, List.of("b", "a"), List.of(), 0),
            run("1-2", 3, List.of("a", "b"), List.of(), 0));

    assertThat(Metrics.of(rs, GOLD).get(Metrics.STABILITY)).isEqualTo(1.0);
  }

  // U13-04
  @Test
  void given_disjointFinalsAcrossThreeSeeds_when_of_then_stabilityZero() {
    List<RunRecord> rs =
        List.of(
            run("1-2", 1, List.of("a"), List.of(), 0),
            run("1-2", 2, List.of("b"), List.of(), 0),
            run("1-2", 3, List.of("c"), List.of(), 0));

    assertThat(Metrics.of(rs, GOLD).get(Metrics.STABILITY)).isZero();
  }

  // U13-04
  @Test
  void given_singleSeed_when_of_then_stabilityNaN() {
    List<RunRecord> rs =
        List.of(
            run("1-2", 1, List.of("a"), List.of(), 0),
            run("1-2", 1, List.of("b"), List.of(), 0),
            run("3-4", 7, List.of("a"), List.of(), 0));

    assertThat(Metrics.of(rs, GOLD).get(Metrics.STABILITY)).isNaN();
  }

  // U13-04
  @Test
  void given_twoCouplesAndIterations_when_of_then_meanOfCouplesPairedByIteration() {
    List<RunRecord> rs =
        List.of(
            run("1-2", 1, List.of("a", "b"), List.of(), 0),
            run("1-2", 1, List.of("c"), List.of(), 0),
            run("1-2", 2, List.of("b", "c"), List.of(), 0),
            run("1-2", 2, List.of("c"), List.of(), 0),
            run("1-2", 2, List.of("z"), List.of(), 0),
            run("3-4", 1, List.of("x"), List.of(), 0),
            run("3-4", 2, List.of("y"), List.of(), 0));

    // couple 1-2: (1/3 + 1) / 2 = 2/3; couple 3-4: 0 -> mean 1/3
    assertThat(Metrics.of(rs, GOLD).get(Metrics.STABILITY)).isCloseTo(1.0 / 3.0, within(1e-12));
  }

  @Test
  void given_setsOfIds_when_jaccard_then_intersectionOverUnion() {
    assertThat(Metrics.jaccard(List.of(), List.of())).isEqualTo(1.0);
    assertThat(Metrics.jaccard(List.of("a", "b"), List.of("b", "c"))).isEqualTo(1.0 / 3.0);
    assertThat(Metrics.jaccard(List.of("a"), List.of())).isZero();
  }

  @Test
  void given_callsPerRun_when_of_then_meanOfConsumedCalls() {
    List<RunRecord> rs =
        List.of(run("1-2", 1, List.of(), List.of(), 5), run("1-2", 2, List.of(), List.of(), 3));

    assertThat(Metrics.of(rs, GOLD).get(Metrics.CALLS_PER_RUN)).isEqualTo(4.0);
    assertThat(new RunRecord.Evidence(Map.of(), 0, List.of(), 0, 0).calls()).isZero();
  }
}
