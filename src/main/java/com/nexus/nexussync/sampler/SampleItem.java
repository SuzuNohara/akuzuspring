package com.nexus.nexussync.sampler;

import com.nexus.nexussync.params.Feature;
import java.util.Map;
import java.util.Objects;

/**
 * One sampled activity with its score and features (unit U4).
 *
 * @param activityId slug of the activity
 * @param score weighted score of the features
 * @param features feature vector used to compute the score
 * @param explored whether the item was chosen by exploration instead of by score
 */
public record SampleItem(
    String activityId, double score, Map<Feature, Double> features, boolean explored) {

  /**
   * Validates the id and copies the features so the record is immutable.
   *
   * @implNote O(f) time and space, f = number of features.
   */
  public SampleItem {
    Objects.requireNonNull(activityId, "activityId");
    features = Map.copyOf(features);
  }
}
