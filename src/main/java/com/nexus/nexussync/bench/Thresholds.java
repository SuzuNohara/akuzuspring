package com.nexus.nexussync.bench;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.nexus.nexussync.params.ParamsException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;

/**
 * Acceptance thresholds of the calibration (Fase C2, decision D-CAL-2), loaded from {@code
 * calibration/thresholds.yml}. Every value is a rate in {@code [0, 1]}.
 *
 * @param goldViolationRateMax maximum {@code goldViolationRate} ({@code gold_violation_rate_max})
 * @param fairnessGapMax maximum {@code fairnessGap} ({@code fairness_gap_max})
 * @param goldHitRateMin minimum {@code goldHitRate} ({@code gold_hit_rate_min})
 * @param hallucinationRateMax maximum {@code hallucinationRate} ({@code hallucination_rate_max})
 * @param stabilityAtSeedMin minimum {@code stabilityAtSeed} ({@code stability_at_seed_min})
 * @param closureF1RateMin minimum {@code closureF1Rate} ({@code closure_f1_rate_min})
 * @param chosenInTop3TruthRateMin minimum {@code chosenInTop3TruthRate} ({@code
 *     chosen_in_top3_truth_rate_min})
 */
public record Thresholds(
    double goldViolationRateMax,
    double fairnessGapMax,
    double goldHitRateMin,
    double hallucinationRateMax,
    double stabilityAtSeedMin,
    double closureF1RateMin,
    double chosenInTop3TruthRateMin) {

  /** YAML keys, in the order of the components. */
  public static final List<String> KEYS =
      List.of(
          "gold_violation_rate_max",
          "fairness_gap_max",
          "gold_hit_rate_min",
          "hallucination_rate_max",
          "stability_at_seed_min",
          "closure_f1_rate_min",
          "chosen_in_top3_truth_rate_min");

  private static final YAMLMapper YAML = new YAMLMapper();

  /**
   * Loads the thresholds: every key of {@link #KEYS} is required, no other key is allowed and every
   * value must be a number in {@code [0, 1]}.
   *
   * @param yml the {@code thresholds.yml} file
   * @return the thresholds
   * @throws ParamsException if the file is unreadable, not a mapping, misses a key, has an unknown
   *     key or a value outside {@code [0, 1]}
   * @implNote O(k) time and space, k = number of keys.
   */
  public static Thresholds load(Path yml) throws ParamsException {
    JsonNode root = read(yml);
    for (Iterator<String> it = root.fieldNames(); it.hasNext(); ) {
      String key = it.next();
      if (!KEYS.contains(key)) {
        throw new ParamsException("unknown threshold '" + key + "' in " + yml);
      }
    }
    double[] v = new double[KEYS.size()];
    for (int i = 0; i < v.length; i++) {
      v[i] = rate(root, KEYS.get(i), yml);
    }
    return new Thresholds(v[0], v[1], v[2], v[3], v[4], v[5], v[6]);
  }

  private static JsonNode read(Path yml) throws ParamsException {
    try {
      JsonNode root = YAML.readTree(yml.toFile());
      if (root == null || !root.isObject()) {
        throw new ParamsException("thresholds file is not a mapping: " + yml);
      }
      return root;
    } catch (JsonProcessingException e) {
      throw new ParamsException("invalid yaml in " + yml, e);
    } catch (IOException e) {
      throw new ParamsException("cannot read " + yml, e);
    }
  }

  private static double rate(JsonNode root, String key, Path yml) throws ParamsException {
    JsonNode node = root.path(key);
    if (!node.isNumber()) {
      throw new ParamsException("missing or non-numeric threshold '" + key + "' in " + yml);
    }
    double v = node.asDouble();
    if (!(v >= 0.0 && v <= 1.0)) {
      throw new ParamsException("threshold '" + key + "' must be in [0, 1] in " + yml + ": " + v);
    }
    return v;
  }
}
