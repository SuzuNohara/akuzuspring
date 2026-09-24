package com.nexus.nexussync.learning;

import com.nexus.nexussync.params.Feature;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Learned feature weights of one couple (unit U10).
 *
 * <p>The design name {@code nChoices} is exposed as {@code choiceCount} because Google style
 * forbids a one-letter camel-case prefix.
 *
 * @param coupleId couple identifier, {@code min(a,b) + "-" + max(a,b)}
 * @param paramsHash hash of the parameter set the weights were learned under (A5)
 * @param w weight per feature; after an update they add up to 1
 * @param choiceCount number of choices learned from
 * @param version version of the weights, incremented by every update
 */
public record Weights(
    String coupleId, String paramsHash, Map<Feature, Double> w, int choiceCount, int version) {

  /**
   * Copies the weights into an unmodifiable {@link EnumMap} so the record is immutable and its
   * iteration order is the declaration order of {@link Feature}.
   *
   * @implNote O(f) time and space, f = features.
   */
  public Weights {
    Map<Feature, Double> copy = new EnumMap<>(Feature.class);
    copy.putAll(w);
    w = Collections.unmodifiableMap(copy);
  }
}
