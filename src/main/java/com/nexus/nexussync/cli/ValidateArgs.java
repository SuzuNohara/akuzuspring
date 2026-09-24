package com.nexus.nexussync.cli;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Arguments of {@code validate} (unit U11, §3.11).
 *
 * @param params experiment YAML; its directory must also hold {@code default.yml}
 * @param dir nexussync root overriding {@code runtime.nexussync_dir}, when given
 */
public record ValidateArgs(Path params, Optional<Path> dir) {

  /**
   * Checks that no component is {@code null}.
   *
   * @implNote O(1) time and space.
   */
  public ValidateArgs {
    Objects.requireNonNull(params, "params");
    Objects.requireNonNull(dir, "dir");
  }
}
