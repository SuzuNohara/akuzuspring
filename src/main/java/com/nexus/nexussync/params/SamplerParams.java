package com.nexus.nexussync.params;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Parameters of the deterministic sampler (unit U4).
 *
 * <p>The design names {@code nSample} and {@code sMin} are exposed as {@code sampleSize} and {@code
 * sampleMin} because Google style forbids a one-letter camel-case prefix; the YAML keys stay {@code
 * n_sample} and {@code s_min}.
 *
 * @param sampleSize size of the sample offered to the agents ({@code n_sample})
 * @param sampleMin minimum number of eligible activities before the sample is insufficient ({@code
 *     s_min})
 * @param radiusKm search radius in kilometres
 * @param cooldownDays days an already offered activity is excluded
 * @param maxPerType maximum activities of the same type in a sample
 * @param homeShareMin minimum share of HOME activities reserved in a sample
 * @param rainMax maximum accepted rain probability for outdoor activities
 * @param weightsInit initial weight per feature
 * @param learningMethod weight update rule
 * @param eta base learning rate
 * @param explorationMethod exploration policy
 * @param eps0 initial exploration rate
 * @param tau decay constant of the exploration rate
 * @param emotionEnabled whether the EMOTION feature is active
 * @param emotionWeight multiplier of the EMOTION feature
 * @param relaxOrder order in which hard filters are relaxed
 * @param maxRelaxations maximum number of relaxation steps
 */
public record SamplerParams(
    @JsonProperty("n_sample") int sampleSize,
    @JsonProperty("s_min") int sampleMin,
    double radiusKm,
    int cooldownDays,
    int maxPerType,
    double homeShareMin,
    double rainMax,
    Map<Feature, Double> weightsInit,
    LearningMethod learningMethod,
    double eta,
    ExplorationMethod explorationMethod,
    double eps0,
    double tau,
    boolean emotionEnabled,
    double emotionWeight,
    List<FilterName> relaxOrder,
    int maxRelaxations) {

  /**
   * Copies the collections defensively so the record is immutable.
   *
   * @implNote O(f + r) time and space, f = features, r = relaxation steps.
   */
  public SamplerParams {
    weightsInit = Map.copyOf(weightsInit);
    relaxOrder = List.copyOf(relaxOrder);
  }
}
