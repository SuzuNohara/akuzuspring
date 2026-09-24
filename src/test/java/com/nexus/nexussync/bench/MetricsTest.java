package com.nexus.nexussync.bench;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.nexus.nexussync.catalog.Activity;
import com.nexus.nexussync.catalog.Catalog;
import com.nexus.nexussync.catalog.LocationScope;
import com.nexus.nexussync.catalog.Place;
import com.nexus.nexussync.learning.Weights;
import com.nexus.nexussync.params.Feature;
import com.nexus.nexussync.params.Intersection;
import com.nexus.nexussync.places.PlaceAssignment;
import com.nexus.nexussync.places.PlaceOption;
import com.nexus.nexussync.places.PlaceStatus;
import com.nexus.nexussync.rounds.Agent;
import com.nexus.nexussync.rounds.Closure;
import com.nexus.nexussync.rounds.GateResult;
import com.nexus.nexussync.rounds.Pick;
import com.nexus.nexussync.rounds.PickStatus;
import com.nexus.nexussync.sampler.Sample;
import com.nexus.nexussync.sampler.SampleItem;
import com.nexus.nexussync.sampler.SampleStatus;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MetricsTest {

  private static final Map<Feature, Double> TRUTH =
      Map.of(Feature.INTEREST, 0.6, Feature.PRICE, 0.3, Feature.NOVELTY, 0.1);

  private static Pick ok(Agent agent, List<String> ids, List<String> hallucinated) {
    List<String> reasons = ids.stream().map(id -> "").toList();
    return new Pick(agent, ids, reasons, hallucinated, PickStatus.OK);
  }

  private static RunRecord closed(Closure closure, List<Pick> r1, RunRecord.Evidence evidence) {
    return BenchFixtures.record(
        BenchFixtures.gate(closure, List.of("x"), r1, List.of()),
        BenchFixtures.emptySample(),
        Optional.empty(),
        Duration.ofSeconds(2),
        evidence);
  }

  // U11-03
  @Test
  void given_fourClosures_when_of_then_quarterRatesAndHallucinationOverReturnedIds() {
    List<Pick> withInvented =
        List.of(
            ok(Agent.A, List.of("x", "y"), List.of("z")),
            ok(Agent.B, List.of("x"), List.of()),
            ok(Agent.M, List.of("x", "y", "w"), List.of()));
    List<RunRecord> rs =
        List.of(
            closed(Closure.F1, withInvented, BenchFixtures.noEvidence()),
            closed(Closure.F2, List.of(), BenchFixtures.noEvidence()),
            closed(Closure.F3, List.of(), BenchFixtures.noEvidence()),
            closed(Closure.AI_UNAVAILABLE, List.of(), BenchFixtures.noEvidence()));

    Map<String, Double> m = Metrics.of(rs);

    assertThat(m).containsOnlyKeys(Metrics.NAMES);
    assertThat(m.get(Metrics.F1)).isEqualTo(0.25);
    assertThat(m.get(Metrics.F2)).isEqualTo(0.25);
    assertThat(m.get(Metrics.F3)).isEqualTo(0.25);
    assertThat(m.get(Metrics.AI_UNAVAILABLE)).isEqualTo(0.25);
    assertThat(m.get(Metrics.HALLUCINATION)).isCloseTo(1.0 / 7.0, within(1e-12));
    assertThat(m.get(Metrics.MEAN_SECONDS)).isEqualTo(2.0);
    assertThat(m.get(Metrics.TRUTH_ALIGNMENT)).isNaN();
    assertThat(m.get(Metrics.TYPE_DIVERSITY)).isNaN();
    assertThat(m.get(Metrics.HOURS_PARSED)).isNaN();
    assertThat(m.get(Metrics.CHOSEN_TOP3)).isNaN();
  }

  // U11-03
  @Test
  void given_noRuns_when_of_then_everyMetricNaN() {
    Map<String, Double> m = Metrics.of(List.of());

    assertThat(m.keySet()).containsExactlyElementsOf(Metrics.NAMES);
    assertThat(m.values()).hasSize(11).allSatisfy(v -> assertThat(v).isNaN());
  }

  // U11-04
  @Test
  void given_learnedWeightsEqualToTruth_when_of_then_truthAlignmentOne() {
    Map<Agent, Map<Feature, Double>> truth = new EnumMap<>(Agent.class);
    truth.put(Agent.A, TRUTH);
    truth.put(Agent.B, TRUTH);
    RunRecord r =
        BenchFixtures.record(
            BenchFixtures.gate(Closure.F1, List.of("x"), List.of(), List.of()),
            BenchFixtures.emptySample(),
            Optional.of(BenchFixtures.weights(TRUTH)),
            Duration.ZERO,
            new RunRecord.Evidence(truth, 0, List.of(), 0, 0));

    assertThat(Metrics.of(List.of(r)).get(Metrics.TRUTH_ALIGNMENT)).isCloseTo(1.0, within(1e-12));
  }

  // U11-04
  @Test
  void given_differentWeights_when_of_then_truthAlignmentWithinUnitInterval() {
    Map<Agent, Map<Feature, Double>> truth = new EnumMap<>(Agent.class);
    truth.put(Agent.A, TRUTH);
    truth.put(Agent.B, Map.of(Feature.SEASON, 1.0));
    Weights learned = BenchFixtures.weights(Map.of(Feature.INTEREST, 0.2, Feature.SEASON, 0.8));
    RunRecord aligned =
        BenchFixtures.record(
            BenchFixtures.gate(Closure.F1, List.of("x"), List.of(), List.of()),
            BenchFixtures.emptySample(),
            Optional.of(learned),
            Duration.ZERO,
            new RunRecord.Evidence(truth, 0, List.of(), 0, 0));
    RunRecord noTruth =
        BenchFixtures.record(
            BenchFixtures.gate(Closure.F1, List.of("x"), List.of(), List.of()),
            BenchFixtures.emptySample(),
            Optional.of(learned),
            Duration.ZERO,
            BenchFixtures.noEvidence());

    double t = Metrics.of(List.of(aligned, noTruth)).get(Metrics.TRUTH_ALIGNMENT);

    assertThat(t).isBetween(0.0, 1.0).isLessThan(1.0);
    assertThat(Metrics.cosine(Map.of(), TRUTH)).isEmpty();
    assertThat(Metrics.cosine(TRUTH, Map.of())).isEmpty();
  }

  @Test
  void given_personaFirstPicksAndTypes_when_of_then_top3DiversityIntersectionAndHours() {
    Sample sample =
        new Sample(
            List.of(
                item("s1", 0.9),
                item("s2", 0.8),
                item("s3", 0.7),
                item("s4", 0.1),
                item("s5", 0.0)),
            List.of(),
            SampleStatus.OK,
            Map.of());
    Map<Agent, Map<Feature, Double>> truth = new EnumMap<>(Agent.class);
    truth.put(Agent.A, Map.of(Feature.INTEREST, 1.0));
    truth.put(Agent.B, Map.of(Feature.INTEREST, 1.0));
    List<Pick> r1 =
        List.of(
            ok(Agent.A, List.of("s2", "s5"), List.of()),
            ok(Agent.B, List.of("s5", "s2"), List.of()),
            ok(Agent.M, List.of("s5"), List.of()));
    RunRecord r =
        BenchFixtures.record(
            BenchFixtures.gate(Closure.F3, List.of("s2", "s5"), r1, List.of()),
            sample,
            Optional.empty(),
            Duration.ofSeconds(4),
            new RunRecord.Evidence(truth, 2, List.of("MUSEUM", "MUSEUM", "BAR", "PARK"), 4, 3));
    RunRecord empty = closed(Closure.F1, List.of(), BenchFixtures.noEvidence());

    Map<String, Double> m = Metrics.of(List.of(r, empty));

    assertThat(m.get(Metrics.CHOSEN_TOP3)).isEqualTo(0.5);
    assertThat(m.get(Metrics.TYPE_DIVERSITY)).isEqualTo(0.75);
    assertThat(m.get(Metrics.MEAN_INTERSECTION)).isEqualTo(1.0);
    assertThat(m.get(Metrics.HOURS_PARSED)).isEqualTo(0.75);
    assertThat(m.get(Metrics.MEAN_SECONDS)).isEqualTo(3.0);
  }

  @Test
  void given_failedPersonaWithTruth_when_of_then_noPersonaInvocationCounted() {
    Map<Agent, Map<Feature, Double>> truth = new EnumMap<>(Agent.class);
    truth.put(Agent.A, TRUTH);
    RunRecord r =
        closed(
            Closure.AI_UNAVAILABLE,
            List.of(Pick.failed(Agent.A, List.of("ghost"))),
            new RunRecord.Evidence(truth, 0, List.of(), 0, 0));

    Map<String, Double> m = Metrics.of(List.of(r));

    assertThat(m.get(Metrics.CHOSEN_TOP3)).isNaN();
    assertThat(m.get(Metrics.HALLUCINATION)).isEqualTo(1.0);
  }

  @Test
  void given_roundOnePicks_when_intersectionF1_then_ruleByModeAndFailures() {
    Pick a = ok(Agent.A, List.of("x", "y", "z"), List.of());
    Pick b = ok(Agent.B, List.of("x", "y"), List.of());
    Pick m = ok(Agent.M, List.of("x"), List.of());
    Pick failedA = Pick.failed(Agent.A, List.of());
    Pick failedB = Pick.failed(Agent.B, List.of());
    Pick failedM = Pick.failed(Agent.M, List.of());

    assertThat(RunRecord.Evidence.intersectionF1(List.of(a, b, m), Intersection.TRIPLE))
        .isEqualTo(1);
    assertThat(
            RunRecord.Evidence.intersectionF1(
                List.of(a, b, m), Intersection.PAIR_MEDIATOR_TIEBREAK))
        .isEqualTo(2);
    assertThat(RunRecord.Evidence.intersectionF1(List.of(a, b, failedM), Intersection.TRIPLE))
        .isEqualTo(2);
    assertThat(RunRecord.Evidence.intersectionF1(List.of(failedA, b, m), Intersection.TRIPLE))
        .isEqualTo(1);
    assertThat(RunRecord.Evidence.intersectionF1(List.of(a, failedB, m), Intersection.TRIPLE))
        .isEqualTo(1);
    assertThat(RunRecord.Evidence.intersectionF1(List.of(a, failedB, failedM), Intersection.TRIPLE))
        .isZero();
    assertThat(RunRecord.Evidence.intersectionF1(List.of(failedA, failedB, m), Intersection.TRIPLE))
        .isZero();
    assertThat(RunRecord.Evidence.intersectionF1(List.of(), Intersection.TRIPLE)).isZero();
  }

  @Test
  void given_placesWithAndWithoutHours_when_evidenceOf_then_countsDeclaredAndParsed() {
    Catalog cat =
        new Catalog(
            Map.of("x", activity("x", "MUSEUM"), "y", activity("y", "BAR")),
            Map.of(
                "p1", place("p1", Optional.of("Mo-Su 09:00-18:00")),
                "p2", place("p2", Optional.of("sunrise-sunset")),
                "p3", place("p3", Optional.of(" ")),
                "p4", place("p4", Optional.empty())),
            Map.of());
    PlaceAssignment places =
        new PlaceAssignment(
            "x",
            PlaceStatus.OK,
            List.of(
                option("p1", false),
                option("p2", true),
                option("p3", true),
                option("p4", true),
                option("missing", false)),
            List.of());
    GateResult gate = BenchFixtures.gate(Closure.F1, List.of("x", "y", "zz"), List.of(), List.of());

    RunRecord.Evidence e =
        RunRecord.Evidence.of(
            BenchFixtures.context(Optional.of(TRUTH), Optional.empty()),
            gate,
            Optional.of(places),
            cat,
            Intersection.TRIPLE);

    assertThat(e.hoursDeclared()).isEqualTo(2);
    assertThat(e.hoursParsed()).isEqualTo(1);
    assertThat(e.finalTypes()).containsExactly("MUSEUM", "BAR", "");
    assertThat(e.truthWeights()).containsOnlyKeys(Agent.A);
    assertThat(
            RunRecord.Evidence.of(
                    BenchFixtures.context(Optional.empty(), Optional.of(TRUTH)),
                    gate,
                    Optional.empty(),
                    cat,
                    Intersection.TRIPLE)
                .truthWeights())
        .containsOnlyKeys(Agent.B);
  }

  private static SampleItem item(String id, double interest) {
    return new SampleItem(id, 0.0, Map.of(Feature.INTEREST, interest), false);
  }

  private static PlaceOption option(String id, boolean hoursUnknown) {
    return new PlaceOption(id, OptionalDouble.empty(), OptionalDouble.empty(), hoursUnknown, true);
  }

  private static Place place(String id, Optional<String> hours) {
    return new Place(id, id, "MUSEUM", 19.4, -99.1, "Coyoacan", hours, false, true);
  }

  static Activity activity(String id, String type) {
    return new Activity(
        id,
        id,
        type,
        LocationScope.CITY,
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of("ANY"),
        60,
        60,
        60,
        0,
        0,
        5,
        0,
        0,
        "FREE",
        false,
        Set.of(),
        Set.of(),
        "");
  }
}
