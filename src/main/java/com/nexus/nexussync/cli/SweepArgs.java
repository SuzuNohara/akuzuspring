package com.nexus.nexussync.cli;

import com.nexus.nexussync.params.ExecutorKind;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Arguments of {@code sweep} (unit U13, Fase C3).
 *
 * @param dir nexussync root: {@code params/} receives the generated experiments, {@code runs/} the
 *     runs and {@code calibration/} the report
 * @param matrix the matrix YAML ({@code calibration/matrix-<stage>.yml})
 * @param couples slugs of {@code fixtures/couples/}, or the single value {@code all}
 * @param seeds seeds of every experiment; each one is a full pass over the couples
 * @param executor {@code ORACLE} (no AI) or {@code ARKANNIE}
 * @param rounds simulated dates per couple and seed, {@code >= 1}
 */
public record SweepArgs(
    Path dir,
    Path matrix,
    List<String> couples,
    List<Long> seeds,
    ExecutorKind executor,
    int rounds) {

  /**
   * Checks the components and copies the lists.
   *
   * @throws IllegalArgumentException if there are no couples or seeds, {@code rounds < 1} or the
   *     executor is {@code REPLAY}
   * @implNote O(c + s) time and space, c couples, s seeds.
   */
  public SweepArgs {
    Objects.requireNonNull(dir, "dir");
    Objects.requireNonNull(matrix, "matrix");
    Objects.requireNonNull(executor, "executor");
    couples = List.copyOf(couples);
    seeds = List.copyOf(seeds);
    if (couples.isEmpty() || seeds.isEmpty()) {
      throw new IllegalArgumentException("sweep needs couples and seeds");
    }
    if (rounds < 1) {
      throw new IllegalArgumentException("rounds must be >= 1: " + rounds);
    }
    if (executor == ExecutorKind.REPLAY) {
      throw new IllegalArgumentException("sweep runs with ORACLE or ARKANNIE, not REPLAY");
    }
  }
}
