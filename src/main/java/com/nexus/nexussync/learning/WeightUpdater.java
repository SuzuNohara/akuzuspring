package com.nexus.nexussync.learning;

import com.nexus.nexussync.params.Feature;
import com.nexus.nexussync.params.LearningMethod;
import com.nexus.nexussync.params.LearningParams;
import com.nexus.nexussync.params.SamplerParams;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Multiplicative learning of the sampler weights from the couple's choices (unit U10).
 *
 * <p>The base rate of an update is {@link LearningParams#eta()}; {@link SamplerParams#eta()} is not
 * used by the learning nor by the sampler (the parameter is duplicated and kept only so the params
 * contract does not change).
 */
public final class WeightUpdater {

  /** Ratings at or above this value use {@link LearningParams#etaPos()}. */
  static final int POSITIVE_RATING = 4;

  /** Ratings at or below this value use {@link LearningParams#etaNeg()}. */
  static final int NEGATIVE_RATING = 2;

  /** Maximum clamp and rebalance passes of {@link #clip(Map, double, double)}. */
  static final int MAX_CLIP_PASSES = 3;

  private static final int FEATURES = Feature.values().length;

  private WeightUpdater() {}

  /**
   * Initial weights: {@code weightsInit} normalized so they add up to 1.
   *
   * @param p sampler parameters holding {@code weights_init}
   * @return one weight per feature (0 for features absent from {@code weights_init}); uniform when
   *     the initial weights add up to 0
   * @implNote O(f) time and space, f = features.
   */
  public static Map<Feature, Double> initial(SamplerParams p) {
    return normalize(p.weightsInit());
  }

  /**
   * Learns from one choice: {@code w ← w·exp(eta·(x_chosen − mean_offered))}, then {@link #clip} to
   * {@code [w_min, w_max]} with the weights adding up to 1. The rate is {@code eta_pos} for a
   * rating ≥ {@value #POSITIVE_RATING}, {@code eta_neg} for a rating ≤ {@value #NEGATIVE_RATING}
   * and {@code eta} otherwise. With {@link LearningMethod#NONE} the weights are kept. The choice
   * count and the version always increase by one.
   *
   * @param cur current weights
   * @param chosen features of the chosen activity
   * @param offered features of every offered activity; empty means no signal
   * @param rating the couple's rating of the date, if any
   * @param lp learning parameters
   * @param sp sampler parameters (learning method)
   * @return the next version of the weights
   * @throws IllegalArgumentException if no weight vector fits {@code [w_min, w_max]} (see {@link
   *     #clip})
   * @implNote O(o·f) time, o = offered activities, f = features; O(f) space.
   */
  public static Weights update(
      Weights cur,
      Map<Feature, Double> chosen,
      List<Map<Feature, Double>> offered,
      OptionalInt rating,
      LearningParams lp,
      SamplerParams sp) {
    Map<Feature, Double> next = cur.w();
    if (sp.learningMethod() != LearningMethod.NONE) {
      double eta = rate(rating, lp);
      Map<Feature, Double> mean = mean(offered, chosen);
      Map<Feature, Double> raw = new EnumMap<>(Feature.class);
      for (Feature f : Feature.values()) {
        double delta = value(chosen, f) - value(mean, f);
        raw.put(f, value(cur.w(), f) * Math.exp(eta * delta));
      }
      next = clip(raw, lp.weightMin(), lp.weightMax());
    }
    return new Weights(
        cur.coupleId(), cur.paramsHash(), next, cur.choiceCount() + 1, cur.version() + 1);
  }

  /**
   * Normalizes the weights and clamps them to {@code [weightMin, weightMax]} keeping the sum at 1:
   * every pass clamps the violators and rebalances the rest affinely (lowering the weights above
   * {@code weightMin} in proportion to their excess, or raising the weights below {@code weightMax}
   * in proportion to their headroom), which never pushes a weight out of the bounds; at most
   * {@value #MAX_CLIP_PASSES} passes, one suffices except for rounding.
   *
   * @param w weights to clip; missing features count as 0
   * @param weightMin lower bound of every weight
   * @param weightMax upper bound of every weight
   * @return one weight per feature, each in {@code [weightMin, weightMax]}, adding up to 1
   * @throws IllegalArgumentException if {@code weightMin ≥ weightMax} or {@code f·weightMin > 1} or
   *     {@code f·weightMax < 1}, f = features (no vector fits)
   * @implNote O(f) time and space, f = features.
   */
  static Map<Feature, Double> clip(Map<Feature, Double> w, double weightMin, double weightMax) {
    if (!(weightMin < weightMax) || FEATURES * weightMin > 1.0 || FEATURES * weightMax < 1.0) {
      throw new IllegalArgumentException(
          "sin pesos posibles en ["
              + weightMin
              + ", "
              + weightMax
              + "] para "
              + FEATURES
              + " rasgos");
    }
    Map<Feature, Double> cur = new EnumMap<>(normalize(w));
    for (int pass = 0; pass < MAX_CLIP_PASSES && !within(cur, weightMin, weightMax); pass++) {
      cur.replaceAll((f, v) -> Math.min(weightMax, Math.max(weightMin, v)));
      rebalance(cur, weightMin, weightMax);
    }
    return Collections.unmodifiableMap(cur);
  }

  /**
   * Weights the sampler must use: the initial ones while the couple has fewer than {@code
   * cold_start_min} rated dates (cold start), the learned ones afterwards.
   *
   * @param cur learned weights
   * @param ratedDatesCount rated dates of the couple
   * @param lp learning parameters ({@code cold_start_min})
   * @param sp sampler parameters ({@code weights_init})
   * @return the effective weights
   * @implNote O(f) time and space, f = features.
   */
  public static Map<Feature, Double> effective(
      Weights cur, int ratedDatesCount, LearningParams lp, SamplerParams sp) {
    return ratedDatesCount < lp.coldStartMin() ? initial(sp) : cur.w();
  }

  private static double rate(OptionalInt rating, LearningParams lp) {
    if (rating.isEmpty()) {
      return lp.eta();
    }
    int r = rating.getAsInt();
    if (r >= POSITIVE_RATING) {
      return lp.etaPos();
    }
    return r <= NEGATIVE_RATING ? lp.etaNeg() : lp.eta();
  }

  private static Map<Feature, Double> mean(
      List<Map<Feature, Double>> offered, Map<Feature, Double> chosen) {
    if (offered.isEmpty()) {
      return chosen;
    }
    Map<Feature, Double> mean = new EnumMap<>(Feature.class);
    for (Feature f : Feature.values()) {
      double sum = 0.0;
      for (Map<Feature, Double> x : offered) {
        sum += value(x, f);
      }
      mean.put(f, sum / offered.size());
    }
    return mean;
  }

  private static Map<Feature, Double> normalize(Map<Feature, Double> w) {
    double sum = 0.0;
    for (Feature f : Feature.values()) {
      sum += value(w, f);
    }
    Map<Feature, Double> out = new EnumMap<>(Feature.class);
    for (Feature f : Feature.values()) {
      out.put(f, sum > 0.0 ? value(w, f) / sum : 1.0 / FEATURES);
    }
    return Collections.unmodifiableMap(out);
  }

  private static void rebalance(Map<Feature, Double> w, double weightMin, double weightMax) {
    double sum = 0.0;
    for (double v : w.values()) {
      sum += v;
    }
    if (sum > 1.0) {
      double factor = (1.0 - FEATURES * weightMin) / (sum - FEATURES * weightMin);
      w.replaceAll((f, v) -> weightMin + (v - weightMin) * factor);
    } else if (sum < 1.0) {
      double factor = (FEATURES * weightMax - 1.0) / (FEATURES * weightMax - sum);
      w.replaceAll((f, v) -> weightMax - (weightMax - v) * factor);
    }
  }

  private static boolean within(Map<Feature, Double> w, double weightMin, double weightMax) {
    for (double v : w.values()) {
      if (v < weightMin || v > weightMax) {
        return false;
      }
    }
    return true;
  }

  private static double value(Map<Feature, Double> x, Feature f) {
    return x.getOrDefault(f, 0.0);
  }
}
