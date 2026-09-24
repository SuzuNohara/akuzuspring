package com.nexus.nexussync.bench;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Selection rule of a calibration stage (unit U13, Fase C3, U13-08).
 *
 * <p>Lexicographic rule over the metric means ({@link SelectorMode#MEAN}):
 *
 * <ol>
 *   <li>discard every experiment whose {@code goldViolationRate} exceeds {@code
 *       gold_violation_rate_max} (0: any violation disqualifies);
 *   <li>discard every remaining experiment whose {@code fairnessGap} exceeds {@code
 *       fairness_gap_max};
 *   <li>pick the maximum {@code goldHitRate};
 *   <li>break ties by the minimum {@code callsPerRun}, then by experiment name (determinism).
 * </ol>
 *
 * <p>A filtered metric that is NaN (no gold labels, no learned weights) cannot prove compliance, so
 * the experiment is discarded with that value; a NaN {@code goldHitRate} ranks after any number and
 * a NaN {@code callsPerRun} after any number.
 */
public final class Selector {

  private static final Comparator<Double> HIGHEST_FIRST_NAN_LAST =
      (x, y) ->
          Double.compare(nanAs(y, Double.NEGATIVE_INFINITY), nanAs(x, Double.NEGATIVE_INFINITY));
  private static final Comparator<Double> LOWEST_FIRST_NAN_LAST =
      (x, y) ->
          Double.compare(nanAs(x, Double.POSITIVE_INFINITY), nanAs(y, Double.POSITIVE_INFINITY));

  /** Order of the surviving experiments: best first. */
  static final Comparator<ExperimentResult> RANKING =
      Comparator.comparing(
              (ExperimentResult r) -> r.metric(Metrics.GOLD_HIT), HIGHEST_FIRST_NAN_LAST)
          .thenComparing(r -> r.metric(Metrics.CALLS_PER_RUN), LOWEST_FIRST_NAN_LAST)
          .thenComparing(ExperimentResult::experiment);

  private Selector() {}

  /**
   * Applies the rule with {@link SelectorMode#MEAN}.
   *
   * @param results measured experiments
   * @param th calibration thresholds
   * @return the winner with the ranking and the discards; empty if every experiment was discarded
   *     or there was none
   * @implNote O(n log n) time and O(n) space, n = number of experiments.
   */
  public static Optional<Selection> pick(List<ExperimentResult> results, Thresholds th) {
    return pick(results, th, SelectorMode.MEAN);
  }

  /**
   * Applies the rule in the given mode.
   *
   * @param results measured experiments
   * @param th calibration thresholds
   * @param mode comparison mode
   * @return the winner with the ranking and the discards; empty if no experiment survives
   * @throws UnsupportedOperationException for {@link SelectorMode#CI_LOWER} (pending approval)
   * @implNote O(n log n) time and O(n) space, n = number of experiments.
   */
  public static Optional<Selection> pick(
      List<ExperimentResult> results, Thresholds th, SelectorMode mode) {
    return switch (mode) {
      case MEAN -> byMean(results, th);
      case CI_LOWER ->
          throw new UnsupportedOperationException(
              "CI_LOWER selection is pending the developer's approval; use MEAN");
    };
  }

  /**
   * The discard trace of the two filters, also when no experiment survives.
   *
   * @param results measured experiments
   * @param th calibration thresholds
   * @return one discard per removed experiment, in input order
   * @implNote O(n) time and space.
   */
  public static List<Selection.Discard> discards(List<ExperimentResult> results, Thresholds th) {
    List<Selection.Discard> out = new ArrayList<>();
    for (ExperimentResult r : results) {
      discard(r, th).ifPresent(out::add);
    }
    return out;
  }

  private static Optional<Selection> byMean(List<ExperimentResult> results, Thresholds th) {
    List<ExperimentResult> kept = new ArrayList<>();
    for (ExperimentResult r : results) {
      if (discard(r, th).isEmpty()) {
        kept.add(r);
      }
    }
    if (kept.isEmpty()) {
      return Optional.empty();
    }
    kept.sort(RANKING);
    return Optional.of(new Selection(kept.get(0), kept, discards(results, th)));
  }

  private static Optional<Selection.Discard> discard(ExperimentResult r, Thresholds th) {
    double violation = r.metric(Metrics.GOLD_VIOLATION);
    if (!(violation <= th.goldViolationRateMax())) {
      return Optional.of(
          new Selection.Discard(
              r.experiment(),
              Selection.Reason.GOLD_VIOLATION,
              violation,
              th.goldViolationRateMax()));
    }
    double gap = r.metric(Metrics.FAIRNESS_GAP);
    if (!(gap <= th.fairnessGapMax())) {
      return Optional.of(
          new Selection.Discard(
              r.experiment(), Selection.Reason.FAIRNESS_GAP, gap, th.fairnessGapMax()));
    }
    return Optional.empty();
  }

  private static double nanAs(double v, double replacement) {
    return Double.isNaN(v) ? replacement : v;
  }
}
