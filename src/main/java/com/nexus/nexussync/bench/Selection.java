package com.nexus.nexussync.bench;

import java.util.List;
import java.util.Objects;

/**
 * Outcome of {@link Selector#pick}: the winner, the ranking of the surviving experiments and the
 * trace of the discarded ones (unit U13, U13-08).
 *
 * @param winner first experiment of {@code ranking}
 * @param ranking experiments that passed every filter, best first
 * @param discards experiments removed by a filter, in input order
 */
public record Selection(
    ExperimentResult winner, List<ExperimentResult> ranking, List<Discard> discards) {

  /**
   * Checks the components and copies the lists.
   *
   * @throws IllegalArgumentException if {@code ranking} does not start with {@code winner}
   * @implNote O(r + d) time and space.
   */
  public Selection {
    Objects.requireNonNull(winner, "winner");
    ranking = List.copyOf(ranking);
    discards = List.copyOf(discards);
    if (ranking.isEmpty() || !ranking.get(0).equals(winner)) {
      throw new IllegalArgumentException("ranking must start with the winner");
    }
  }

  /** Filter of the selection rule that discarded an experiment. */
  public enum Reason {
    /** {@code goldViolationRate} above its maximum, or unmeasured. */
    GOLD_VIOLATION,
    /** {@code fairnessGap} above its maximum, or unmeasured. */
    FAIRNESS_GAP
  }

  /**
   * One discarded experiment.
   *
   * @param experiment experiment name
   * @param reason filter that discarded it
   * @param value value of the filtered metric (NaN when unmeasured)
   * @param limit maximum allowed by the thresholds
   */
  public record Discard(String experiment, Reason reason, double value, double limit) {

    /**
     * Checks that no component is {@code null}.
     *
     * @implNote O(1) time and space.
     */
    public Discard {
      Objects.requireNonNull(experiment, "experiment");
      Objects.requireNonNull(reason, "reason");
    }
  }
}
