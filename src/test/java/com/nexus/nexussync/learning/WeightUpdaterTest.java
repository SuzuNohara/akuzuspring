package com.nexus.nexussync.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.nexus.nexussync.params.ExplorationMethod;
import com.nexus.nexussync.params.Feature;
import com.nexus.nexussync.params.FilterName;
import com.nexus.nexussync.params.LearningMethod;
import com.nexus.nexussync.params.LearningParams;
import com.nexus.nexussync.params.SamplerParams;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** Multiplicative learning, clipping and cold start of the weights (T-56, T-57). */
final class WeightUpdaterTest {

  private static final double EPS = 1e-9;
  private static final LearningParams LP = new LearningParams(0.3, 0.45, 0.15, 3, 0.02, 0.6);

  static SamplerParams sampler(Map<Feature, Double> init, LearningMethod method) {
    return new SamplerParams(
        40,
        15,
        15.0,
        30,
        2,
        0.25,
        0.6,
        init,
        method,
        0.3,
        ExplorationMethod.EPS_GREEDY,
        0.35,
        5.0,
        true,
        1.0,
        List.of(FilterName.COOLDOWN),
        4);
  }

  static Map<Feature, Double> constant(double value) {
    Map<Feature, Double> m = new EnumMap<>(Feature.class);
    for (Feature f : Feature.values()) {
      m.put(f, value);
    }
    return m;
  }

  private static Map<Feature, Double> with(Map<Feature, Double> base, Feature f, double value) {
    Map<Feature, Double> m = new EnumMap<>(base);
    m.put(f, value);
    return m;
  }

  private static Weights start(SamplerParams sp) {
    return new Weights("4-8", "abcd1234", WeightUpdater.initial(sp), 0, 0);
  }

  private static double sum(Map<Feature, Double> w) {
    return w.values().stream().mapToDouble(Double::doubleValue).sum();
  }

  // U10-01
  @Test
  void given_weightsInit_when_initial_then_normalizedToOne() {
    SamplerParams sp = sampler(with(constant(1.0), Feature.INTEREST, 3.0), LearningMethod.NONE);

    Map<Feature, Double> w = WeightUpdater.initial(sp);

    assertThat(w).hasSize(Feature.values().length);
    assertThat(sum(w)).isCloseTo(1.0, within(EPS));
    assertThat(w.get(Feature.INTEREST)).isCloseTo(3.0 / 9.0, within(EPS));
    assertThat(w.get(Feature.PRICE)).isCloseTo(1.0 / 9.0, within(EPS));
  }

  @Test
  void given_partialOrZeroWeightsInit_when_initial_then_missingZeroAndAllZeroUniform() {
    Map<Feature, Double> partial =
        WeightUpdater.initial(sampler(Map.of(Feature.PRICE, 2.0), LearningMethod.NONE));
    Map<Feature, Double> zero = WeightUpdater.initial(sampler(constant(0.0), LearningMethod.NONE));

    assertThat(partial.get(Feature.PRICE)).isCloseTo(1.0, within(EPS));
    assertThat(partial.get(Feature.SEASON)).isZero();
    assertThat(zero.values()).allSatisfy(v -> assertThat(v).isCloseTo(1.0 / 7, within(EPS)));
  }

  // U10-02
  @Test
  void given_chosenInterestAboveMean_when_update_then_interestRisesAndCountersGrow() {
    SamplerParams sp = sampler(constant(1.0), LearningMethod.MULTIPLICATIVE);
    Weights cur = start(sp);
    Map<Feature, Double> chosen = with(constant(0.5), Feature.INTEREST, 1.0);
    List<Map<Feature, Double>> offered = List.of(constant(0.5), chosen);

    Weights next = WeightUpdater.update(cur, chosen, offered, OptionalInt.empty(), LP, sp);

    assertThat(next.w().get(Feature.INTEREST)).isGreaterThan(cur.w().get(Feature.INTEREST));
    assertThat(sum(next.w())).isCloseTo(1.0, within(EPS));
    assertThat(next.version()).isEqualTo(cur.version() + 1);
    assertThat(next.choiceCount()).isEqualTo(cur.choiceCount() + 1);
    assertThat(next.coupleId()).isEqualTo("4-8");
    assertThat(next.paramsHash()).isEqualTo("abcd1234");
  }

  // U10-02
  @Test
  void given_chosenInterestBelowMean_when_update_then_interestFalls() {
    SamplerParams sp = sampler(constant(1.0), LearningMethod.MULTIPLICATIVE);
    Weights cur = start(sp);
    Map<Feature, Double> chosen = with(constant(0.5), Feature.INTEREST, 0.0);
    List<Map<Feature, Double>> offered = List.of(constant(0.5), chosen);

    Weights next = WeightUpdater.update(cur, chosen, offered, OptionalInt.empty(), LP, sp);

    assertThat(next.w().get(Feature.INTEREST)).isLessThan(cur.w().get(Feature.INTEREST));
    assertThat(sum(next.w())).isCloseTo(1.0, within(EPS));
  }

  // U10-03
  @Test
  void given_ratings_when_update_then_etaPosEtaNegOrEta() {
    SamplerParams sp = sampler(constant(1.0), LearningMethod.MULTIPLICATIVE);
    Weights cur = start(sp);
    Map<Feature, Double> chosen = with(constant(0.0), Feature.INTEREST, 1.0);
    List<Map<Feature, Double>> offered = List.of(constant(0.0), chosen);

    double five = ratio(WeightUpdater.update(cur, chosen, offered, OptionalInt.of(5), LP, sp));
    double one = ratio(WeightUpdater.update(cur, chosen, offered, OptionalInt.of(1), LP, sp));
    double none = ratio(WeightUpdater.update(cur, chosen, offered, OptionalInt.empty(), LP, sp));
    double three = ratio(WeightUpdater.update(cur, chosen, offered, OptionalInt.of(3), LP, sp));

    assertThat(five).isCloseTo(Math.exp(LP.etaPos() * 0.5), within(EPS));
    assertThat(one).isCloseTo(Math.exp(LP.etaNeg() * 0.5), within(EPS));
    assertThat(none).isCloseTo(Math.exp(LP.eta() * 0.5), within(EPS));
    assertThat(three).isCloseTo(none, within(EPS));
  }

  // U10-03
  @Test
  void given_learningMethodNone_when_update_then_sameWeightsAndVersionPlusOne() {
    SamplerParams sp = sampler(constant(1.0), LearningMethod.NONE);
    Weights cur = start(sp);
    Map<Feature, Double> chosen = with(constant(0.0), Feature.INTEREST, 1.0);

    Weights next =
        WeightUpdater.update(cur, chosen, List.of(constant(0.0)), OptionalInt.of(5), LP, sp);

    assertThat(next.w()).isEqualTo(cur.w());
    assertThat(next.version()).isEqualTo(1);
    assertThat(next.choiceCount()).isEqualTo(1);
  }

  @Test
  void given_noOfferedActivities_when_update_then_noSignalAndWeightsKept() {
    SamplerParams sp = sampler(constant(1.0), LearningMethod.MULTIPLICATIVE);
    Weights cur = start(sp);

    Weights next = WeightUpdater.update(cur, constant(1.0), List.of(), OptionalInt.empty(), LP, sp);

    assertThat(next.w().get(Feature.PRICE)).isCloseTo(1.0 / 7, within(EPS));
    assertThat(next.version()).isEqualTo(1);
  }

  // U10-04
  @Test
  void given_fiftyExtremeUpdates_when_update_then_everyWeightWithinBoundsAndSumOne() {
    SamplerParams sp = sampler(constant(1.0), LearningMethod.MULTIPLICATIVE);
    LearningParams hot = new LearningParams(2.0, 2.0, 2.0, 3, 0.02, 0.6);
    Weights cur = start(sp);
    Map<Feature, Double> chosen = with(constant(0.0), Feature.INTEREST, 1.0);
    List<Map<Feature, Double>> offered = List.of(chosen, constant(1.0), constant(1.0));

    for (int i = 0; i < 50; i++) {
      cur = WeightUpdater.update(cur, chosen, offered, OptionalInt.of(5), hot, sp);
      assertThat(sum(cur.w())).isCloseTo(1.0, within(EPS));
      assertThat(cur.w().values()).allSatisfy(v -> assertThat(v).isBetween(0.02, 0.6));
    }
    assertThat(cur.w().get(Feature.INTEREST)).isCloseTo(0.6, within(EPS));
    assertThat(cur.version()).isEqualTo(50);
  }

  // U10-04
  @Test
  void given_manyAtLowerBound_when_clip_then_restLoweredAndBoundsKept() {
    Map<Feature, Double> raw =
        with(with(constant(1e-9), Feature.INTEREST, 5.0), Feature.PRICE, 5.0);

    Map<Feature, Double> w = WeightUpdater.clip(raw, 0.02, 0.6);

    assertThat(sum(w)).isCloseTo(1.0, within(EPS));
    assertThat(w.values()).allSatisfy(v -> assertThat(v).isBetween(0.02, 0.6));
    assertThat(w.get(Feature.INTEREST)).isCloseTo(0.45, within(EPS));
    assertThat(w.get(Feature.SEASON)).isCloseTo(0.02, within(EPS));
  }

  @Test
  void given_alreadyWithinBounds_when_clip_then_onlyNormalized() {
    Map<Feature, Double> w = WeightUpdater.clip(constant(3.0), 0.02, 0.6);

    assertThat(w.values()).allSatisfy(v -> assertThat(v).isCloseTo(1.0 / 7, within(EPS)));
  }

  @Test
  void given_infeasibleBounds_when_clip_then_illegalArgument() {
    Map<Feature, Double> w = constant(1.0);

    assertThatThrownBy(() -> WeightUpdater.clip(w, 0.5, 0.4))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> WeightUpdater.clip(w, 0.2, 0.6))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> WeightUpdater.clip(w, 0.0, 0.1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // U10-05
  @Test
  void given_ratedDatesAroundColdStartMin_when_effective_then_initialThenLearned() {
    SamplerParams sp = sampler(constant(1.0), LearningMethod.MULTIPLICATIVE);
    Map<Feature, Double> learnedW = with(constant(0.1), Feature.INTEREST, 0.4);
    Weights learned = new Weights("4-8", "abcd1234", learnedW, 5, 5);

    Map<Feature, Double> cold = WeightUpdater.effective(learned, LP.coldStartMin() - 1, LP, sp);
    Map<Feature, Double> warm = WeightUpdater.effective(learned, LP.coldStartMin(), LP, sp);

    assertThat(cold).isEqualTo(WeightUpdater.initial(sp));
    assertThat(warm).isEqualTo(learned.w());
  }

  @Test
  void given_weightsRecord_when_mutatingSource_then_recordUnchanged() {
    Map<Feature, Double> source = constant(0.5);
    Weights w = new Weights("4-8", "abcd1234", source, 0, 0);

    source.put(Feature.PRICE, 9.0);

    assertThat(w.w().get(Feature.PRICE)).isEqualTo(0.5);
    assertThatThrownBy(() -> w.w().put(Feature.PRICE, 1.0))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private static double ratio(Weights w) {
    return w.w().get(Feature.INTEREST) / w.w().get(Feature.PRICE);
  }
}
