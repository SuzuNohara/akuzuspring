package com.nexus.nexussync.ann;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Boundary between the package and external processes (A6).
 *
 * <p>{@code ArkannieRunner}, {@code AnnCheck} and the CLI never touch {@link ProcessBuilder}
 * directly: they receive a launcher, so tests replace the arkannie binary with a programmable fake
 * and production uses {@link DefaultLauncher}.
 */
public interface ProcessLauncher {

  /**
   * Runs a command to completion or until the timeout expires.
   *
   * @param cmd program and arguments, never empty
   * @param dir working directory of the process
   * @param env variables added on top of the inherited environment
   * @param timeout maximum wall-clock time before the process is destroyed
   * @return exit code, captured streams (truncated to 64 KiB each), timeout flag and elapsed time
   * @throws IOException if the process cannot be started or its streams cannot be read
   * @throws InterruptedException if the calling thread is interrupted while waiting
   */
  LaunchResult launch(List<String> cmd, Path dir, Map<String, String> env, Duration timeout)
      throws IOException, InterruptedException;
}
