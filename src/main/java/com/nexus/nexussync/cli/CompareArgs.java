package com.nexus.nexussync.cli;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Arguments of {@code compare} (unit U11, §3.11).
 *
 * @param dir nexussync root: experiments are read from {@code runs/} and the report is written to
 *     {@code reports/}
 * @param exps experiment names, one report column each, in this order
 */
public record CompareArgs(Path dir, List<String> exps) {

  /**
   * Checks that no component is {@code null} and copies the list.
   *
   * @implNote O(e) time and space, e = number of experiments.
   */
  public CompareArgs {
    Objects.requireNonNull(dir, "dir");
    exps = List.copyOf(exps);
  }
}
