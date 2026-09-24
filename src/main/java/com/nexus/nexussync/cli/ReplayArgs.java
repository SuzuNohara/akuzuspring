package com.nexus.nexussync.cli;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Arguments of {@code replay} (unit U11, §3.11).
 *
 * @param params experiment YAML of the recorded run; its directory must also hold {@code
 *     default.yml}
 * @param dir nexussync root overriding {@code runtime.nexussync_dir}, when given
 * @param from recorded run directory ({@code runs/<experiment>/<coupleId>/<runId>})
 */
public record ReplayArgs(Path params, Optional<Path> dir, Path from) {

  /**
   * Checks that no component is {@code null}.
   *
   * @implNote O(1) time and space.
   */
  public ReplayArgs {
    Objects.requireNonNull(params, "params");
    Objects.requireNonNull(dir, "dir");
    Objects.requireNonNull(from, "from");
  }
}
