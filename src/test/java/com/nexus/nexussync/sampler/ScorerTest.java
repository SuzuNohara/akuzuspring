package com.nexus.nexussync.sampler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.nexus.nexussync.params.Feature;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Weighted mean of the sampler (T-28). */
final class ScorerTest {

  private static final double EPS = 1e-9;

  // U4-08
  @Test
  void given_weightsAndValues_when_score_then_weightedMean() {
    Map<Feature, Double> x = features(1.0, 0.5, 0.0);
    Map<Feature, Double> w = features(1.0, 3.0, 4.0);

    assertThat(Scorer.score(x, w)).isCloseTo((1.0 + 1.5 + 0.0) / 8.0, within(EPS));
  }

  // U4-08
  @Test
  void given_allWeightsZero_when_score_then_zero() {
    Map<Feature, Double> x = features(1.0, 1.0, 1.0);
    Map<Feature, Double> w = features(0.0, 0.0, 0.0);

    assertThat(Scorer.score(x, w)).isEqualTo(0.0);
  }

  @Test
  void given_emptyWeights_when_score_then_zero() {
    Map<Feature, Double> x = features(1.0, 1.0, 1.0);

    assertThat(Scorer.score(x, new EnumMap<>(Feature.class))).isEqualTo(0.0);
  }

  @Test
  void given_uniformWeights_when_score_then_plainMean() {
    Map<Feature, Double> x = features(0.2, 0.4, 0.9);
    Map<Feature, Double> w = features(1.0, 1.0, 1.0);

    assertThat(Scorer.score(x, w)).isCloseTo(0.5, within(EPS));
  }

  @Test
  void given_featureMissingInValues_when_score_then_countsAsZero() {
    Map<Feature, Double> x = new EnumMap<>(Feature.class);
    x.put(Feature.INTEREST, 1.0);
    Map<Feature, Double> w = features(1.0, 1.0, 0.0);

    assertThat(Scorer.score(x, w)).isCloseTo(0.5, within(EPS));
  }

  @Test
  void given_valuesOutsideWeights_when_score_then_ignored() {
    Map<Feature, Double> x = features(1.0, 1.0, 1.0);
    x.put(Feature.SEASON, 0.0);
    x.put(Feature.NOVELTY, 0.0);
    Map<Feature, Double> w = features(1.0, 1.0, 1.0);

    assertThat(Scorer.score(x, w)).isCloseTo(1.0, within(EPS));
  }

  @Test
  void given_unitValues_when_score_then_exactlyOneRegardlessOfWeights() {
    Map<Feature, Double> x = features(1.0, 1.0, 1.0);
    Map<Feature, Double> w = features(0.02, 0.6, 0.38);

    assertThat(Scorer.score(x, w)).isCloseTo(1.0, within(EPS));
  }

  private static Map<Feature, Double> features(double interest, double price, double distance) {
    Map<Feature, Double> m = new EnumMap<>(Feature.class);
    m.put(Feature.INTEREST, interest);
    m.put(Feature.PRICE, price);
    m.put(Feature.DISTANCE, distance);
    return m;
  }
}
