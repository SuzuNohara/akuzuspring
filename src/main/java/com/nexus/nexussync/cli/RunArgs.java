package com.nexus.nexussync.cli;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Arguments of {@code run} (unit U11, §3.11).
 *
 * @param params experiment YAML; its directory must also hold {@code default.yml}
 * @param dir nexussync root overriding {@code runtime.nexussync_dir}, when given
 * @param couples slugs of {@code fixtures/couples/}, or the single value {@code all}; empty means
 *     that {@code --couples} was not given (usage error)
 * @param rounds simulated dates per couple, {@code >= 1}
 * @param seed seed overriding the one of the parameters, when given
 */
public record RunArgs(
    Path params, Optional<Path> dir, List<String> couples, int rounds, OptionalLong seed) {

  /**
   * Checks the components and copies the list.
   *
   * @throws IllegalArgumentException if {@code rounds < 1}
   * @implNote O(c) time and space, c = number of couples.
   */
  public RunArgs {
    Objects.requireNonNull(params, "params");
    Objects.requireNonNull(dir, "dir");
    Objects.requireNonNull(seed, "seed");
    couples = List.copyOf(couples);
    if (rounds < 1) {
      throw new IllegalArgumentException("rounds must be >= 1: " + rounds);
    }
  }
}
