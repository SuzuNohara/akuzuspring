package com.nexus.nexussync.sampler;

import com.nexus.nexussync.params.Feature;
import java.util.Map;

/**
 * Weighted mean of the feature vector of an activity (unit U4).
 *
 * <p>{@code score(x, w) = sum(w_f * x_f) / sum(w_f)} over the features {@code f} present in {@code
 * w}; a feature missing from {@code x} counts as {@code 0}, features of {@code x} absent from
 * {@code w} are ignored, and a zero total weight yields {@code 0.0} instead of dividing by zero.
 */
public final class Scorer {

  private Scorer() {}

  /**
   * Computes the weighted mean of {@code x} under the weights {@code w}.
   *
   * @param x feature values, normally in {@code [0, 1]}
   * @param w weight per feature; defines which features participate
   * @return {@code sum(w * x) / sum(w)}, or {@code 0.0} when {@code sum(w) == 0}
   * @implNote O(|w|) time, O(1) extra space.
   */
  public static double score(Map<Feature, Double> x, Map<Feature, Double> w) {
    double weighted = 0.0;
    double total = 0.0;
    for (Map.Entry<Feature, Double> entry : w.entrySet()) {
      double weight = entry.getValue();
      weighted += weight * x.getOrDefault(entry.getKey(), 0.0);
      total += weight;
    }
    return total == 0.0 ? 0.0 : weighted / total;
  }
}
