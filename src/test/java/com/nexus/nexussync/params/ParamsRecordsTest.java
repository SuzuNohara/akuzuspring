package com.nexus.nexussync.params;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Construction, equality and defensive copies of the U1 parameter records (T-07). */
final class ParamsRecordsTest {

  /**
   * Builds a complete, valid {@link Params} for the tests of this package.
   *
   * @return a fully populated parameter set with the pipeline v0.1 values
   */
  static Params sample() {
    return new Params(
        "sample",
        7L,
        Path.of("fixtures/catalog-synthetic"),
        Path.of("fixtures/catalog-synthetic/places.csv"),
        sampler(),
        new RoundsParams(
            15, 5, 5, Intersection.TRIPLE, 5, Fill.ALTERNATE_AB, RankAggregation.RANK_SUM, 2, true),
        new AgentsParams("haiku", 90, "haiku", 120, MediatorClimate.AGGREGATED),
        new DecisionParams(List.of(3, 2, 1), 5, 48, 0.5, 0.5, 30),
        new PlaceParams(
            15.0,
            3,
            List.of(PlaceRelax.RADIUS, PlaceRelax.HOURS_UNKNOWN, PlaceRelax.WEATHER),
            false),
        new LearningParams(0.3, 0.45, 0.15, 3, 0.02, 0.6),
        new ContextParams(7, 90),
        runtime(Path.of("/tmp/nexussync")),
        new RubricParams(
            Map.of("compatibilidad", 0.5, "equilibrio", 0.5),
            Map.of("max_por_tipo", 2),
            Map.of("presupuesto_excedido", 1.0)),
        new BenchParams(0.0));
  }

  static SamplerParams sampler() {
    return new SamplerParams(
        40,
        15,
        15.0,
        30,
        2,
        0.25,
        0.6,
        weights(),
        LearningMethod.MULTIPLICATIVE,
        0.3,
        ExplorationMethod.EPS_GREEDY,
        0.35,
        5.0,
        true,
        1.0,
        List.of(FilterName.COOLDOWN, FilterName.WEATHER, FilterName.BUDGET, FilterName.RADIUS),
        4);
  }

  static RuntimeParams runtime(Path nexussyncDir) {
    return new RuntimeParams(
        ExecutorKind.ARKANNIE,
        nexussyncDir,
        Path.of("arkannie/bin/arkannie"),
        Optional.empty(),
        200,
        "0.3.0");
  }

  static Map<Feature, Double> weights() {
    Map<Feature, Double> w = new HashMap<>();
    for (Feature f : Feature.values()) {
      w.put(f, 1.0);
    }
    return w;
  }

  @Test
  void given_sameValues_when_constructedTwice_then_equalWithSameHashCode() {
    Params first = sample();
    Params second = sample();

    assertThat(first).isEqualTo(second);
    assertThat(first.hashCode()).isEqualTo(second.hashCode());
    assertThat(first.sampler()).isEqualTo(second.sampler());
    assertThat(first.runtime().replayDir()).isEmpty();
  }

  @Test
  void given_differentSeed_when_compared_then_notEqual() {
    Params base = sample();
    Params other =
        new Params(
            base.experiment(),
            8L,
            base.catalogDir(),
            base.placesCsv(),
            base.sampler(),
            base.rounds(),
            base.agents(),
            base.decision(),
            base.place(),
            base.learning(),
            base.context(),
            base.runtime(),
            base.rubric(),
            base.bench());

    assertThat(other).isNotEqualTo(base);
  }

  @Test
  void given_mutableRelaxOrder_when_samplerBuilt_then_copyIsIndependentAndUnmodifiable() {
    List<FilterName> order = new ArrayList<>(List.of(FilterName.BUDGET));
    Map<Feature, Double> w = weights();
    SamplerParams sampler = withRelax(order, w);

    order.add(FilterName.RADIUS);
    w.put(Feature.PRICE, 9.0);

    assertThat(sampler.relaxOrder()).containsExactly(FilterName.BUDGET);
    assertThat(sampler.weightsInit()).containsEntry(Feature.PRICE, 1.0);
    assertThatThrownBy(() -> sampler.relaxOrder().add(FilterName.SCOPE))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> sampler.weightsInit().put(Feature.SEASON, 2.0))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void given_mutableRankPoints_when_decisionBuilt_then_copyIsIndependentAndUnmodifiable() {
    List<Integer> points = new ArrayList<>(List.of(3, 2, 1));
    DecisionParams decision = new DecisionParams(points, 5, 48, 0.5, 0.5, 30);

    points.set(0, 99);

    assertThat(decision.rankPoints()).containsExactly(3, 2, 1);
    assertThatThrownBy(() -> decision.rankPoints().add(0))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void given_mutablePlaceRelax_when_placeBuilt_then_unmodifiable() {
    List<PlaceRelax> relax = new ArrayList<>(List.of(PlaceRelax.RADIUS));
    PlaceParams place = new PlaceParams(15.0, 3, relax, true);

    relax.clear();

    assertThat(place.relaxOrder()).containsExactly(PlaceRelax.RADIUS);
    assertThatThrownBy(() -> place.relaxOrder().add(PlaceRelax.WEATHER))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void given_mutableRubricMaps_when_rubricBuilt_then_unmodifiable() {
    Map<String, Double> criteria = new HashMap<>(Map.of("a", 1.0));
    Map<String, Integer> balance = new HashMap<>(Map.of("b", 1));
    Map<String, Double> descarte = new HashMap<>(Map.of("c", 1.0));
    final RubricParams rubric = new RubricParams(criteria, balance, descarte);
    criteria.put("z", 2.0);
    balance.clear();
    descarte.clear();

    assertThat(rubric.criteria()).containsOnlyKeys("a");
    assertThat(rubric.balance()).containsOnlyKeys("b");
    assertThat(rubric.descarte()).containsOnlyKeys("c");
    assertThatThrownBy(() -> rubric.criteria().put("y", 1.0))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> rubric.balance().put("y", 1))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> rubric.descarte().put("y", 1.0))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void given_allEnums_when_valuesListed_then_matchDesign() {
    assertThat(Feature.values()).hasSize(7);
    assertThat(FilterName.values()).hasSize(8);
    assertThat(Intersection.values())
        .containsExactly(Intersection.TRIPLE, Intersection.PAIR_MEDIATOR_TIEBREAK);
    assertThat(Fill.values()).containsExactly(Fill.ALTERNATE_AB, Fill.MEDIATOR_FIRST, Fill.NONE);
    assertThat(RankAggregation.values())
        .containsExactly(
            RankAggregation.RANK_SUM, RankAggregation.BORDA, RankAggregation.MEDIATOR_PRIORITY);
    assertThat(LearningMethod.values())
        .containsExactly(LearningMethod.MULTIPLICATIVE, LearningMethod.NONE);
    assertThat(ExplorationMethod.values())
        .containsExactly(ExplorationMethod.EPS_GREEDY, ExplorationMethod.NONE);
    assertThat(MediatorClimate.values())
        .containsExactly(MediatorClimate.NONE, MediatorClimate.AGGREGATED);
    assertThat(PlaceRelax.values())
        .containsExactly(PlaceRelax.RADIUS, PlaceRelax.HOURS_UNKNOWN, PlaceRelax.WEATHER);
    assertThat(ExecutorKind.values())
        .containsExactly(ExecutorKind.ARKANNIE, ExecutorKind.REPLAY, ExecutorKind.ORACLE);
  }

  private static SamplerParams withRelax(List<FilterName> order, Map<Feature, Double> w) {
    SamplerParams s = sampler();
    return new SamplerParams(
        s.sampleSize(),
        s.sampleMin(),
        s.radiusKm(),
        s.cooldownDays(),
        s.maxPerType(),
        s.homeShareMin(),
        s.rainMax(),
        w,
        s.learningMethod(),
        s.eta(),
        s.explorationMethod(),
        s.eps0(),
        s.tau(),
        s.emotionEnabled(),
        s.emotionWeight(),
        order,
        s.maxRelaxations());
  }
}
