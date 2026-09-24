package com.nexus.nexussync.bench;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Measured outcome of one experiment of a sweep (unit U13, Fase C3): the input of {@link Selector}.
 *
 * @param experiment experiment name (the stem of its {@code params/cal-*.yml})
 * @param runs number of runs measured, over every couple and seed
 * @param metrics metrics by name, as returned by {@link Metrics#of(java.util.List, Map)}; NaN where
 *     there was no data
 */
public record ExperimentResult(String experiment, int runs, Map<String, Double> metrics) {

  /**
   * Checks the components and copies the metrics, keeping their order.
   *
   * @throws IllegalArgumentException if {@code runs < 0}
   * @implNote O(m) time and space, m = number of metrics.
   */
  public ExperimentResult {
    Objects.requireNonNull(experiment, "experiment");
    if (runs < 0) {
      throw new IllegalArgumentException("runs must be >= 0: " + runs);
    }
    metrics = Collections.unmodifiableMap(new LinkedHashMap<>(metrics));
  }

  /**
   * Value of one metric.
   *
   * @param name metric name, e.g. {@link Metrics#GOLD_HIT}
   * @return its value; NaN if the metric is absent or had no data
   * @implNote O(1) time and space.
   */
  public double metric(String name) {
    Double v = metrics.get(name);
    return v == null ? Double.NaN : v;
  }
}
